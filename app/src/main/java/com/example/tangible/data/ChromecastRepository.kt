package com.example.tangible.data

import android.content.Context
import android.util.Log
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class ChromecastRepository(private val context: Context) {
    private val TAG = "ChromecastRepo"

    suspend fun playSpotifyUri(
        device: ChromecastDevice,
        spotifyUri: String,
        spotifyAuth: SpotifyAuthRepository
    ): Boolean {
        val token = spotifyAuth.getValidToken() ?: return false

        // Cast SDK must be used from the main thread
        val session = withContext(Dispatchers.Main) {
            connectWithTimeout(device, timeoutMs = 12_000)
        }
        if (session == null) {
            Log.e(TAG, "Could not start Cast session for '${device.name}'")
            return false
        }
        Log.d(TAG, "Cast session started for '${device.name}', waiting for Spotify to launch…")

        // Give the Spotify Cast receiver time to fully start up
        delay(4_000)

        return withContext(Dispatchers.IO) {
            spotifyAuth.playOnDevice(token, device.name, spotifyUri)
        }
    }

    private suspend fun connectWithTimeout(device: ChromecastDevice, timeoutMs: Long): CastSession? {
        return try {
            withTimeout(timeoutMs) { connectToChromecast(device) }
        } catch (e: Exception) {
            Log.e(TAG, "Timeout or error connecting to '${device.name}': ${e.message}")
            null
        }
    }

    private suspend fun connectToChromecast(device: ChromecastDevice): CastSession? =
        suspendCancellableCoroutine { cont ->
            val castContext = try {
                CastContext.getSharedInstance(context)
            } catch (e: Exception) {
                Log.e(TAG, "CastContext unavailable: ${e.message}")
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            // Reuse an existing connected session
            castContext.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let {
                cont.resume(it)
                return@suspendCancellableCoroutine
            }

            val selector = MediaRouteSelector.Builder()
                .addControlCategory(
                    CastMediaControlIntent.categoryForCast(CastOptionsProvider.SPOTIFY_CAST_APP_ID)
                )
                .build()
            val mediaRouter = MediaRouter.getInstance(context)

            val sessionListener = object : SessionManagerListener<CastSession> {
                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    castContext.sessionManager.removeSessionManagerListener(this, CastSession::class.java)
                    if (cont.isActive) cont.resume(session)
                }
                override fun onSessionStartFailed(session: CastSession, error: Int) {
                    castContext.sessionManager.removeSessionManagerListener(this, CastSession::class.java)
                    if (cont.isActive) cont.resume(null)
                }
                override fun onSessionStarting(session: CastSession) {}
                override fun onSessionEnded(session: CastSession, error: Int) {}
                override fun onSessionEnding(session: CastSession) {}
                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {}
                override fun onSessionResumeFailed(session: CastSession, error: Int) {}
                override fun onSessionResuming(session: CastSession, sessionId: String) {}
                override fun onSessionSuspended(session: CastSession, reason: Int) {}
            }
            castContext.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)

            fun selectIfFound() {
                val route = mediaRouter.routes.firstOrNull { r ->
                    r.name == device.name && r.isEnabled && !r.isDefault
                }
                route?.let { mediaRouter.selectRoute(it) }
            }

            // Watch for routes added while scanning
            val routerCallback = object : MediaRouter.Callback() {
                override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                    if (route.name == device.name) selectIfFound()
                }
            }
            mediaRouter.addCallback(selector, routerCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)

            // Also try routes that are already known
            selectIfFound()

            cont.invokeOnCancellation {
                mediaRouter.removeCallback(routerCallback)
                castContext.sessionManager.removeSessionManagerListener(sessionListener, CastSession::class.java)
            }
        }
}
