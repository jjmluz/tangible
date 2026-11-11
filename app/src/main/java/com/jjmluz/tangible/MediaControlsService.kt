package com.jjmluz.tangible

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.snapshotFlow
import coil.imageLoader
import coil.request.ImageRequest
import com.jjmluz.tangible.data.HomeAssistantRepository
import com.jjmluz.tangible.data.PlayerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The one [NowPlayingController] of the process. The activity shows it and the media controls
 * service mirrors it, so it has to outlive both.
 */
object NowPlaying {
    private const val PREFS = "tangible_prefs"
    private var instance: NowPlayingController? = null
    private val pollers = mutableSetOf<Any>()

    fun controller(context: Context): NowPlayingController {
        instance?.let { return it }
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val scope = MainScope()
        val controller = NowPlayingController(
            repository = HomeAssistantRepository(),
            scope = scope,
            settings = {
                (prefs.getString("ha_server_url", "") ?: "") to (prefs.getString("ha_access_token", "") ?: "")
            },
            onPlayerChanged = { prefs.edit().putString("now_playing_player_id", it).apply() }
        )
        instance = controller
        // Bring up the media controls whenever something starts playing. That only happens while the
        // app is open: with the app closed and no controls showing, nothing polls the player.
        scope.launch {
            snapshotFlow {
                shouldShowMediaControls(controller.status, controller.isStarting) &&
                    (controller.isStarting || controller.status?.isPlaying == true)
            }.collect { playing -> if (playing) MediaControlsService.start(app) }
        }
        return controller
    }

    /**
     * The player is polled while anyone needs it: the activity while it's visible, and the media
     * controls while they're showing.
     */
    fun setPolling(context: Context, who: Any, on: Boolean) {
        if (on) pollers += who else pollers -= who
        val controller = controller(context)
        if (pollers.isEmpty()) controller.stopPolling() else controller.startPolling()
    }
}

/** Media controls are up while an album is starting or a track is loaded, playing or paused. */
internal fun shouldShowMediaControls(status: PlayerStatus?, isStarting: Boolean): Boolean =
    status != null && status.hasMedia && (isStarting || status.isActive)

/** The player's state as Android's media controls want it. */
internal fun playbackStateOf(
    status: PlayerStatus,
    isStarting: Boolean,
    nowMs: Long,
    elapsedRealtimeMs: Long
): PlaybackState {
    val state = when {
        isStarting -> PlaybackState.STATE_BUFFERING
        status.isPlaying -> PlaybackState.STATE_PLAYING
        else -> PlaybackState.STATE_PAUSED
    }
    var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
    // Without a duration there's no progress bar to seek on.
    if (status.durationSec != null && !isStarting) actions = actions or PlaybackState.ACTION_SEEK_TO
    val positionMs = status.positionAt(nowMs)?.let { (it * 1000).toLong() }
        ?: PlaybackState.PLAYBACK_POSITION_UNKNOWN
    return PlaybackState.Builder()
        .setActions(actions)
        .setState(state, positionMs, if (state == PlaybackState.STATE_PLAYING) 1f else 0f, elapsedRealtimeMs)
        .build()
}

/**
 * Shows what the Music Assistant player is playing in Android's media controls (quick settings and
 * the lock screen), with play/pause, previous/next and seek. Nothing plays on the phone: the
 * controls go to the speaker through [NowPlayingController], and so do the phone's volume keys
 * while the controls are showing.
 *
 * It's a foreground service so the player keeps being polled, and the controls stay current, after
 * the app is left. It stops when the player has nothing loaded, or has been paused for a while.
 */
class MediaControlsService : Service() {
    private val scope: CoroutineScope = MainScope()
    private lateinit var nowPlaying: NowPlayingController
    private lateinit var session: MediaSession
    private var artwork: Pair<String, Bitmap>? = null
    private var shownNotification: List<Any?>? = null
    private var isForeground = false

    /** The speaker's volume, 0–100, for the phone's volume keys and volume panel. */
    private val speakerVolume = object : VolumeProvider(VOLUME_CONTROL_ABSOLUTE, 100, 0) {
        override fun onSetVolumeTo(volume: Int) = set(volume)

        // A key press comes as a direction (and 0 when the key is let go).
        override fun onAdjustVolume(direction: Int) {
            if (direction != 0) set(currentVolume + direction * VOLUME_KEY_STEP)
        }

        private fun set(volume: Int) {
            val level = volume.coerceIn(0, 100)
            currentVolume = level
            nowPlaying.setVolume(level / 100f)
        }
    }
    private var volumeIsRemote = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nowPlaying = NowPlaying.controller(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Now playing", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        session = MediaSession(this, "Tangible").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { if (nowPlaying.status?.isPlaying == false) nowPlaying.playPause() }
                override fun onPause() { if (nowPlaying.status?.isPlaying == true) nowPlaying.playPause() }
                override fun onStop() = onPause()
                override fun onSkipToNext() = nowPlaying.next()
                override fun onSkipToPrevious() = nowPlaying.previous()
                override fun onSeekTo(pos: Long) = nowPlaying.seek(pos / 1000.0)
            })
            setSessionActivity(openNowPlayingIntent())
        }
        NowPlaying.setPolling(this, this, true)

        scope.launch {
            snapshotFlow { nowPlaying.status to nowPlaying.isStarting }.collect { publish() }
        }
        scope.launch {
            snapshotFlow { nowPlaying.status?.artworkUrl }.collectLatest { url ->
                if (url == null || artwork?.first == url) return@collectLatest
                loadArtwork(url)?.let {
                    artwork = url to it
                    publish()
                }
            }
        }
        // A paused Sonos keeps its track loaded for good, so don't hold on to the controls forever.
        scope.launch {
            snapshotFlow { nowPlaying.isStarting || nowPlaying.status?.isPlaying == true }.collectLatest { playing ->
                if (!playing) {
                    delay(PAUSED_TIMEOUT_MS)
                    stop()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) stop() else publish()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        NowPlaying.setPolling(this, this, false)
        session.release()
        super.onDestroy()
    }

    /** Puts the player's current state in the media session and its notification. */
    private fun publish() {
        val status = nowPlaying.status
        val isStarting = nowPlaying.isStarting
        if (status == null || !shouldShowMediaControls(status, isStarting)) {
            // A started foreground service has to show a notification before it can go away.
            if (!isForeground) goForeground(notification(status?.title, status?.artist, null, false))
            stop()
            return
        }
        val art = artwork?.takeIf { it.first == status.artworkUrl }?.second
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, status.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, status.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, status.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, status.durationSec?.let { (it * 1000).toLong() } ?: -1L)
                .apply { if (art != null) putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art) }
                .build()
        )
        session.setPlaybackState(
            playbackStateOf(status, isStarting, System.currentTimeMillis(), SystemClock.elapsedRealtime())
        )
        // A player that doesn't report a volume can't be set either: leave the keys to the phone.
        val volume = status.volume
        if (volume != null) {
            speakerVolume.currentVolume = (volume * 100).roundToInt()
            if (!volumeIsRemote) session.setPlaybackToRemote(speakerVolume)
        } else if (volumeIsRemote) {
            session.setPlaybackToLocal(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
        }
        volumeIsRemote = volume != null
        session.isActive = true

        // The position changes all the time; the notification only when what it shows does.
        val isPlaying = isStarting || status.isPlaying
        val shown = listOf(status.title, status.artist, art, isPlaying)
        if (shown != shownNotification || !isForeground) {
            shownNotification = shown
            val notification = notification(status.title, status.artist, art, isPlaying)
            if (isForeground) {
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
            } else {
                goForeground(notification)
            }
        }
    }

    private fun goForeground(notification: Notification) {
        try {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            isForeground = true
        } catch (e: IllegalStateException) {
            // Android doesn't let a foreground service start from the background.
            Log.w("Tangible", "Couldn't show the media controls: ${e.message}")
            stopSelf()
        }
    }

    private fun stop() {
        session.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
        stopSelf()
    }

    private fun notification(title: String?, artist: String?, art: Bitmap?, isPlaying: Boolean): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(art)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
            .setContentIntent(openNowPlayingIntent())
            .setDeleteIntent(
                PendingIntent.getService(
                    this, 1,
                    Intent(this, MediaControlsService::class.java).setAction(ACTION_DISMISS),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .build()

    private fun openNowPlayingIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_NOW_PLAYING),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private suspend fun loadArtwork(url: String): Bitmap? {
        val request = ImageRequest.Builder(this).data(url).size(ARTWORK_SIZE_PX).allowHardware(false).build()
        return (imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
    }

    companion object {
        private const val CHANNEL_ID = "now_playing"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_DISMISS = "com.jjmluz.tangible.DISMISS_MEDIA_CONTROLS"
        private const val PAUSED_TIMEOUT_MS = 10 * 60 * 1000L
        private const val ARTWORK_SIZE_PX = 512
        private const val VOLUME_KEY_STEP = 2

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, MediaControlsService::class.java))
            } catch (e: IllegalStateException) {
                Log.w("Tangible", "Couldn't start the media controls: ${e.message}")
            }
        }
    }
}
