package com.example.tangible.data

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SpotifyRepository(private val clientId: String, private val clientSecret: String) {
    private val api: SpotifyApi
    private var accessToken: String? = null

    init {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logger)
            .build()

        api = Retrofit.Builder()
            .baseUrl("https://api.spotify.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpotifyApi::class.java)
    }

    private suspend fun ensureToken() {
        if (accessToken != null) return
        
        val auth = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
        val response = api.getToken(authHeader = "Basic $auth")
        accessToken = response.accessToken
    }

    suspend fun searchAlbums(query: String): List<SpotifyAlbum> {
        if (clientId.isBlank() || clientSecret.isBlank()) return emptyList()
        // Tokens expire after an hour, so on failure retry once with a fresh one.
        repeat(2) {
            try {
                ensureToken()
                return api.search(query = query, authHeader = "Bearer $accessToken").albums.items
            } catch (e: Exception) {
                Log.w("SpotifyRepository", "Album search failed: ${e.message}")
                accessToken = null
            }
        }
        return emptyList()
    }
}
