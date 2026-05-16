package com.example.tangible.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

class SpotifyAuthRepository(
    context: Context,
    private val clientId: String,
    private val clientSecret: String,
    private val redirectUri: String
) {
    private val TAG = "SpotifyAuth"
    private val prefs: SharedPreferences = context.getSharedPreferences("spotify_user_auth", Context.MODE_PRIVATE)
    private val client = OkHttpClient()

    var accessToken: String?
        get() = prefs.getString("access_token", null)
        private set(value) { prefs.edit().putString("access_token", value).apply() }

    private var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(value) { prefs.edit().putString("refresh_token", value).apply() }

    val isAuthorized: Boolean get() = accessToken != null

    fun buildAuthUrl(): String {
        val scopes = URLEncoder.encode("user-modify-playback-state user-read-playback-state", "UTF-8")
        val redirect = URLEncoder.encode(redirectUri, "UTF-8")
        return "https://accounts.spotify.com/authorize" +
            "?client_id=$clientId" +
            "&response_type=code" +
            "&redirect_uri=$redirect" +
            "&scope=$scopes"
    }

    suspend fun handleAuthCode(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val auth = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri)
                .build()
            val request = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .addHeader("Authorization", "Basic $auth")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string() ?: return@withContext false)
                accessToken = json.getString("access_token")
                json.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { refreshToken = it }
                Log.d(TAG, "OAuth successful")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auth code exchange failed: ${e.message}")
            false
        }
    }

    suspend fun refreshAccessToken(): Boolean = withContext(Dispatchers.IO) {
        val rt = refreshToken ?: return@withContext false
        try {
            val auth = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", rt)
                .build()
            val request = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .addHeader("Authorization", "Basic $auth")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val json = JSONObject(response.body?.string() ?: return@withContext false)
                accessToken = json.getString("access_token")
                json.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { refreshToken = it }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed: ${e.message}")
            false
        }
    }

    // Gets a valid token, refreshing if needed. Returns null if not authorized.
    suspend fun getValidToken(): String? {
        val token = accessToken ?: return null
        // Try a quick device list call to check if the token is still valid
        val ok = withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://api.spotify.com/v1/me/player/devices")
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                client.newCall(req).execute().use { it.code != 401 }
            } catch (e: Exception) { true } // Assume valid on network error
        }
        if (ok) return token
        return if (refreshAccessToken()) accessToken else null
    }

    suspend fun playOnDevice(token: String, deviceName: String, spotifyUri: String): Boolean = withContext(Dispatchers.IO) {
        // Fetch available Spotify Connect devices
        val devicesJson = try {
            val req = Request.Builder()
                .url("https://api.spotify.com/v1/me/player/devices")
                .addHeader("Authorization", "Bearer $token")
                .build()
            client.newCall(req).execute().use { it.body?.string() } ?: return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Get devices failed: ${e.message}")
            return@withContext false
        }

        Log.d(TAG, "Spotify devices: $devicesJson")

        val deviceId = parseDeviceId(devicesJson, deviceName)
        if (deviceId == null) {
            Log.e(TAG, "No Spotify device found matching '$deviceName'")
            return@withContext false
        }

        // Transfer playback to the Chromecast device
        try {
            val transferBody = """{"device_ids":["$deviceId"],"play":false}"""
                .toRequestBody("application/json".toMediaType())
            Request.Builder()
                .url("https://api.spotify.com/v1/me/player")
                .addHeader("Authorization", "Bearer $token")
                .put(transferBody)
                .build()
                .let { client.newCall(it).execute().close() }
        } catch (e: Exception) {
            Log.e(TAG, "Transfer failed: ${e.message}")
        }

        Thread.sleep(500)

        // Play the Spotify URI on that device
        val isTrack = spotifyUri.startsWith("spotify:track:")
        val playBody = if (isTrack) {
            """{"uris":["$spotifyUri"],"device_id":"$deviceId"}"""
        } else {
            """{"context_uri":"$spotifyUri","device_id":"$deviceId"}"""
        }.toRequestBody("application/json".toMediaType())

        return@withContext try {
            val req = Request.Builder()
                .url("https://api.spotify.com/v1/me/player/play")
                .addHeader("Authorization", "Bearer $token")
                .put(playBody)
                .build()
            val response = client.newCall(req).execute()
            val code = response.code
            response.close()
            Log.d(TAG, "Play response: $code for URI: $spotifyUri")
            code in 200..204
        } catch (e: Exception) {
            Log.e(TAG, "Play failed: ${e.message}")
            false
        }
    }

    private fun parseDeviceId(json: String, deviceName: String): String? {
        return try {
            val devices = JSONObject(json).getJSONArray("devices")
            for (i in 0 until devices.length()) {
                val d = devices.getJSONObject(i)
                val name = d.optString("name", "")
                if (name.contains(deviceName, ignoreCase = true) ||
                    deviceName.contains(name, ignoreCase = true)) {
                    return d.optString("id").takeIf { it.isNotEmpty() }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Device parse error: ${e.message}")
            null
        }
    }
}
