package com.example.tangible

import android.app.PendingIntent
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.tangible.ui.TangibleBackground
import com.example.tangible.ui.TangiblePurple
import com.example.tangible.ui.TangibleSurface
import com.example.tangible.ui.TangibleTheme
import android.widget.Toast
import com.example.tangible.data.AdbRepository
import com.example.tangible.data.AppleMusicRepository
import com.example.tangible.data.ChromecastDevice
import com.example.tangible.data.ChromecastDiscovery
import com.example.tangible.data.DiscogsRepository
import com.example.tangible.data.DiscogsSearchResult
import com.example.tangible.data.MovieProvider
import com.example.tangible.data.MovieStreamingResult
import com.example.tangible.data.SonosDiscovery
import com.example.tangible.data.SonosRepository
import com.example.tangible.data.SonosSpeaker
import com.example.tangible.data.SpotifyRepository
import com.example.tangible.data.StreamingAvailabilityRepository
import com.example.tangible.data.TagRepository
import com.example.tangible.data.TmdbMovieResult
import com.example.tangible.data.TmdbRepository
import com.example.tangible.data.YouTubeRepository
import com.example.tangible.ui.MainScreen
import com.example.tangible.ui.SortField
import com.example.tangible.ui.SortOrder
import com.example.tangible.ui.MoviePlayerScreen
import com.example.tangible.ui.MovieProvidersScreen
import com.example.tangible.ui.MoviePlayer
import com.example.tangible.ui.MovieRegionScreen
import com.example.tangible.ui.MusicService
import com.example.tangible.ui.MusicServiceScreen
import com.example.tangible.ui.MusicSpeaker
import com.example.tangible.ui.MusicSpeakerScreen
import com.example.tangible.ui.SettingsScreen
import com.example.tangible.ui.TagWriteInfo
import com.example.tangible.ui.WriteTagScreen
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class TagMediaUris(
    val spotifyTrack: String? = null,
    val spotifyAlbum: String? = null,
    val ytm: String? = null,
    val appleMusic: String? = null
)

data class SavedTag(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: String, // "MUSIC" or "MOVIE"
    val url: String,
    val imageUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

const val SONOS_ALL_SPEAKERS = "ALL_SPEAKERS"

private val STREAMING_PACKAGES = mapOf(
    "netflix.com" to "com.netflix.mediaclient",
    "primevideo.com" to "com.amazon.avod.thirdpartyclient",
    "disneyplus.com" to "com.disney.disneyplus",
    "play.max.com" to "com.wbd.stream",
    "tv.apple.com" to "com.apple.atve.androidtv.appletv",
    "skyshowtime.com" to "com.skyshowtime.skyshowtime.google"
)

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var isWritingMode by mutableStateOf(false)
    private var writeError by mutableStateOf<String?>(null)
    private var showSuccessDialog by mutableStateOf(false)
    private var dataToWrite: String? = null
    private var pendingTagInfo: TagWriteInfo? = null
    private var savedTags by mutableStateOf<List<SavedTag>>(emptyList())

    // Settings
    private val prefs by lazy { getSharedPreferences("tangible_prefs", MODE_PRIVATE) }
    private val tagRepository by lazy { TagRepository(prefs) }
    private val gson = Gson()
    private var selectedService by mutableStateOf(MusicService.SPOTIFY)
    private var selectedSpeaker by mutableStateOf(MusicSpeaker.THIS_DEVICE)
    private var selectedSonosIp by mutableStateOf<String?>(null)
    private var sonosSpeakers by mutableStateOf<List<SonosSpeaker>>(emptyList())
    private var isSonosRefreshing by mutableStateOf(false)
    private var chromecastDevices by mutableStateOf<List<ChromecastDevice>>(emptyList())
    private var isChromecastRefreshing by mutableStateOf(false)
    private var selectedChromecastId by mutableStateOf<String?>(null)
    private var selectedChromecastName by mutableStateOf<String?>(null)
    private var selectedChromecastHost: String? = null
    private var selectedMoviePlayer by mutableStateOf(MoviePlayer.THIS_DEVICE)
    private var region by mutableStateOf("US")
    private var movieProviders by mutableStateOf<List<MovieProvider>>(emptyList())

    // Sorting
    private var musicSortField by mutableStateOf(SortField.TITLE)
    private var musicSortOrder by mutableStateOf(SortOrder.ASCENDING)
    private var movieSortField by mutableStateOf(SortField.TITLE)
    private var movieSortOrder by mutableStateOf(SortOrder.ASCENDING)

    // Repositories
    private val discogsToken = "REMOVED_SECRET"
    private val discogsRepository by lazy { DiscogsRepository(discogsToken) }
    private val tmdbApiKey = "REMOVED_SECRET"
    private val tmdbRepository by lazy { TmdbRepository(tmdbApiKey) }
    private val streamingApiKey = "REMOVED_SECRET"
    private val streamingRepository by lazy { StreamingAvailabilityRepository(streamingApiKey) }
    private val clientId = "REMOVED_SECRET"
    private val clientSecret = "REMOVED_SECRET"
    private val spotifyRepository by lazy { SpotifyRepository(clientId, clientSecret) }
    private val youtubeApiKey = "REMOVED_SECRET"
    private val youtubeRepository by lazy { YouTubeRepository(youtubeApiKey) }
    private val appleMusicRepository by lazy { AppleMusicRepository() }
    private val sonosDiscovery by lazy { SonosDiscovery(this) }
    private val sonosRepository by lazy { SonosRepository() }
    private val chromecastDiscovery by lazy { ChromecastDiscovery(this) }
    private val adbRepository by lazy { AdbRepository(this) }

    private var spotifyAppRemote: SpotifyAppRemote? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Load settings
        val serviceValue = prefs.getString("selected_service", MusicService.SPOTIFY.name)
        selectedService = MusicService.entries.find { it.name == serviceValue } ?: MusicService.SPOTIFY
        val speakerValue = prefs.getString("selected_speaker", MusicSpeaker.THIS_DEVICE.name)
        selectedSpeaker = MusicSpeaker.entries.find { it.name == speakerValue } ?: MusicSpeaker.THIS_DEVICE
        selectedSonosIp = prefs.getString("selected_sonos_ip", null)
        selectedChromecastId = prefs.getString("selected_chromecast_id", null)
        selectedChromecastName = prefs.getString("selected_chromecast_name", null)
        selectedChromecastHost = prefs.getString("selected_chromecast_host", null)
        val moviePlayerValue = prefs.getString("selected_movie_player", MoviePlayer.THIS_DEVICE.name)
        selectedMoviePlayer = MoviePlayer.entries.find { it.name == moviePlayerValue } ?: MoviePlayer.THIS_DEVICE
        region = prefs.getString("region", "US") ?: "US"

        // Load sort settings
        musicSortField = SortField.valueOf(prefs.getString("music_sort_field", SortField.TITLE.name) ?: SortField.TITLE.name)
        musicSortOrder = SortOrder.valueOf(prefs.getString("music_sort_order", SortOrder.ASCENDING.name) ?: SortOrder.ASCENDING.name)
        movieSortField = SortField.valueOf(prefs.getString("movie_sort_field", SortField.TITLE.name) ?: SortField.TITLE.name)
        movieSortOrder = SortOrder.valueOf(prefs.getString("movie_sort_order", SortOrder.ASCENDING.name) ?: SortOrder.ASCENDING.name)

        loadMovieProviders()
        loadSonosSpeakers()
        loadChromecastDevices()
        savedTags = tagRepository.loadAll()

        intent?.let { handleIntent(it) }

        setContent {
            TangibleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TangibleApp(
                        savedTags = savedTags,
                        onPlay = { url -> playTagUrl(url) },
                        isWritingMode = isWritingMode,
                        writeError = writeError,
                        selectedService = selectedService,
                        onServiceSelected = {
                            selectedService = it
                            prefs.edit().putString("selected_service", it.name).apply()
                            val speakerInvalid = when (it) {
                                MusicService.YOUTUBE_MUSIC -> selectedSpeaker == MusicSpeaker.SONOS
                                MusicService.APPLE_MUSIC -> selectedSpeaker == MusicSpeaker.SONOS || selectedSpeaker == MusicSpeaker.CHROMECAST
                                else -> false
                            }
                            if (speakerInvalid) {
                                selectedSpeaker = MusicSpeaker.THIS_DEVICE
                                prefs.edit().putString("selected_speaker", MusicSpeaker.THIS_DEVICE.name).apply()
                            }
                        },
                        selectedSpeaker = selectedSpeaker,
                        onSpeakerSelected = {
                            selectedSpeaker = it
                            prefs.edit().putString("selected_speaker", it.name).apply()
                        },
                        sonosSpeakers = sonosSpeakers,
                        selectedSonosIp = selectedSonosIp,
                        onSonosSpeakerSelected = { ip ->
                            selectedSonosIp = ip
                            prefs.edit().putString("selected_sonos_ip", ip).apply()
                        },
                        onRefreshSonos = { refreshSonosSpeakers() },
                        isSonosRefreshing = isSonosRefreshing,
                        chromecastDevices = chromecastDevices,
                        selectedChromecastId = selectedChromecastId,
                        onChromecastDeviceSelected = { id ->
                            selectedChromecastId = id
                            prefs.edit().putString("selected_chromecast_id", id).apply()
                            chromecastDevices.firstOrNull { it.id == id }?.let { device ->
                                selectedChromecastName = device.name
                                selectedChromecastHost = device.host
                                prefs.edit()
                                    .putString("selected_chromecast_name", device.name)
                                    .putString("selected_chromecast_host", device.host)
                                    .apply()
                            }
                        },
                        onRefreshChromecast = { refreshChromecastDevices() },
                        isChromecastRefreshing = isChromecastRefreshing,
                        selectedMoviePlayer = selectedMoviePlayer,
                        onMoviePlayerSelected = {
                            selectedMoviePlayer = it
                            prefs.edit().putString("selected_movie_player", it.name).apply()
                        },
                        onStartWriting = { info ->
                            dataToWrite = info.url
                            pendingTagInfo = info
                            isWritingMode = true
                            writeError = null
                        },
                        onCancelWriting = {
                            isWritingMode = false
                            dataToWrite = null
                            writeError = null
                        },
                        onSearchAlbums = { query -> discogsRepository.searchAlbums(query) },
                        onSearchMovies = { query -> tmdbRepository.searchMovies(query) },
                        onGetMovieStreaming = { tmdbId -> streamingRepository.getMovieStreamingResult(tmdbId, region) },
                        selectedRegion = region,
                        onRegionSelected = { newRegion ->
                            if (newRegion != region) {
                                region = newRegion
                                prefs.edit().putString("region", newRegion).apply()
                                movieProviders = emptyList()
                                saveMovieProviders(emptyList())
                                prefs.edit().remove("movie_providers").apply()
                            }
                        },
                        onAlbumSelected = { discogsAlbum ->
                            val spotifyResults = spotifyRepository.searchAlbums(discogsAlbum.title)
                            val album = spotifyResults.firstOrNull()
                            val spotifyAlbumId = album?.id
                            val spotifyTrackId = album?.let { spotifyRepository.getFirstTrackUri(it.id) }
                                ?.removePrefix("spotify:track:")

                            val parts = discogsAlbum.title.split(" - ", limit = 2)
                            val (artist, title) = if (parts.size == 2) parts[0] to parts[1] else "" to discogsAlbum.title

                            val ytmUrl = youtubeRepository.searchAlbum(title, artist)
                            val ytmId = ytmUrl?.let { Uri.parse(it).getQueryParameter("list") }
                                ?: "https://music.youtube.com/search?q=${Uri.encode(discogsAlbum.title)}"

                            val appleMusicUrl = appleMusicRepository.searchAlbum(title, artist)
                            val appleMusicId = appleMusicUrl?.let { u ->
                                Uri.parse(u).lastPathSegment?.takeIf { seg -> seg.all { it.isDigit() } }
                            } ?: "https://music.apple.com/search?term=${Uri.encode(discogsAlbum.title)}"

                            TagMediaUris(
                                spotifyTrack = spotifyTrackId,
                                spotifyAlbum = spotifyAlbumId,
                                ytm = ytmId,
                                appleMusic = appleMusicId
                            )
                        },
                        movieProviders = movieProviders,
                        onMovieProvidersChanged = { providers ->
                            movieProviders = providers
                            saveMovieProviders(providers)
                        },
                        onGetServicesForRegion = { reg -> streamingRepository.getServicesForRegion(reg) },
                        onDeleteTags = { deleteTags(it) },
                        musicSortField = musicSortField,
                        musicSortOrder = musicSortOrder,
                        movieSortField = movieSortField,
                        movieSortOrder = movieSortOrder,
                        onMusicSortChange = { field, order ->
                            musicSortField = field
                            musicSortOrder = order
                            prefs.edit()
                                .putString("music_sort_field", field.name)
                                .putString("music_sort_order", order.name)
                                .apply()
                        },
                        onMovieSortChange = { field, order ->
                            movieSortField = field
                            movieSortOrder = order
                            prefs.edit()
                                .putString("movie_sort_field", field.name)
                                .putString("movie_sort_order", order.name)
                                .apply()
                        }
                    )

                    if (showSuccessDialog) {
                        AlertDialog(
                            onDismissRequest = { showSuccessDialog = false },
                            title = { Text("Success") },
                            text = { Text("Deep link successfully written to tag!") },
                            confirmButton = {
                                TextButton(onClick = { showSuccessDialog = false }) { Text("OK") }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun saveMovieProviders(providers: List<MovieProvider>) {
        prefs.edit().putString("movie_providers", gson.toJson(providers)).apply()
    }

    private fun loadMovieProviders() {
        val json = prefs.getString("movie_providers", null)
        movieProviders = if (json != null) {
            val type = object : TypeToken<List<MovieProvider>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
    }

    private fun saveSonosSpeakers(speakers: List<SonosSpeaker>) {
        prefs.edit().putString("cached_sonos_speakers", gson.toJson(speakers)).apply()
    }

    private fun loadSonosSpeakers() {
        val json = prefs.getString("cached_sonos_speakers", null)
        if (json != null) {
            val type = object : TypeToken<List<SonosSpeaker>>() {}.type
            sonosSpeakers = gson.fromJson(json, type)
        }
    }

    private fun saveChromecastDevices(devices: List<ChromecastDevice>) {
        prefs.edit().putString("cached_chromecast_devices", gson.toJson(devices)).apply()
    }

    private fun loadChromecastDevices() {
        val json = prefs.getString("cached_chromecast_devices", null)
        if (json != null) {
            val type = object : TypeToken<List<ChromecastDevice>>() {}.type
            chromecastDevices = gson.fromJson(json, type)
        }
    }

    private fun refreshSonosSpeakers() {
        if (isSonosRefreshing) return
        isSonosRefreshing = true
        lifecycleScope.launch {
            val speakers = sonosDiscovery.discoverSpeakers()
            sonosSpeakers = speakers
            saveSonosSpeakers(speakers)
            val ip = selectedSonosIp
            if (ip != null && ip != SONOS_ALL_SPEAKERS) {
                try {
                    sonosRepository.getPlayingInfo(ip)
                } catch (e: Exception) {
                    Log.e("Tangible", "Error getting Sonos info", e)
                }
            }
            isSonosRefreshing = false
        }
    }

    private fun refreshChromecastDevices() {
        if (isChromecastRefreshing) return
        isChromecastRefreshing = true
        lifecycleScope.launch {
            val devices = chromecastDiscovery.discoverDevices()
            chromecastDevices = devices
            saveChromecastDevices(devices)
            isChromecastRefreshing = false
        }
    }

    private fun playOnChromecast(uri: String, service: MusicService) {
        val host = chromecastDevices.firstOrNull { it.id == selectedChromecastId }?.host
            ?: selectedChromecastHost
        if (host == null) {
            Toast.makeText(this, "Please select a Google TV device in Settings", Toast.LENGTH_LONG).show()
            return
        }
        val packageName = when (service) {
            MusicService.SPOTIFY -> "com.spotify.tv.android"
            MusicService.YOUTUBE_MUSIC -> "com.google.android.youtube.tv"
            MusicService.APPLE_MUSIC -> "com.apple.android.music"
        }
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Launching ${service.displayName} on TV…", Toast.LENGTH_SHORT).show()
            val resolvedUri = when (service) {
                MusicService.YOUTUBE_MUSIC -> {
                    val listId = if (uri.startsWith("http")) Uri.parse(uri).getQueryParameter("list") else uri
                    if (listId != null) "https://www.youtube.com/playlist?list=$listId"
                    else uri.replace("music.youtube.com", "www.youtube.com")
                }
                MusicService.APPLE_MUSIC ->
                    if (uri.startsWith("http")) uri else "https://music.apple.com/album/$uri"
                else -> uri
            }
            val ok = adbRepository.launchUri(host, resolvedUri, packageName)
            if (!ok) {
                Toast.makeText(
                    this@MainActivity,
                    "Could not launch ${service.displayName}. Enable ADB debugging in Developer Options on your Google TV.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connectToSpotify()
    }

    override fun onStop() {
        super.onStop()
        spotifyAppRemote?.let { SpotifyAppRemote.disconnect(it) }
    }

    private fun connectToSpotify(onConnected: () -> Unit = {}) {
        if (clientId == "YOUR_CLIENT_ID") return
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri("com.example.tangible://callback")
            .showAuthView(true)
            .build()
        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                Log.d("Tangible", "Connected to Spotify")
                onConnected()
            }
            override fun onFailure(throwable: Throwable) {
                Log.e("Tangible", "Spotify connection failed: ${throwable.message}")
            }
        })
    }

    override fun onResume() {
        super.onResume()
        enableNfcForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        disableNfcForegroundDispatch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val data: Uri? = intent.data

        Log.d("Tangible", "Handling intent: action=$action, data=$data, isWritingMode=$isWritingMode")

        if (isWritingMode && (
                NfcAdapter.ACTION_TAG_DISCOVERED == action ||
                NfcAdapter.ACTION_NDEF_DISCOVERED == action ||
                NfcAdapter.ACTION_TECH_DISCOVERED == action
            )) {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            if (tag != null) writeToTag(tag, dataToWrite ?: "")
            else Log.e("Tangible", "NFC Tag extra is null")
            return
        }

        var uris = TagMediaUris()
        var movieToPlay: String? = null

        fun parseTangibleUri(uri: Uri) {
            if (uri.scheme == "tangible" && (uri.host == "play" || uri.host == null)) {
                val spotifyTrackId = uri.getQueryParameter("spotify_track")
                val spotifyAlbumId = uri.getQueryParameter("spotify_album")
                uris = TagMediaUris(
                    spotifyTrack = spotifyTrackId?.let { "spotify:track:$it" },
                    spotifyAlbum = spotifyAlbumId?.let { "spotify:album:$it" },
                    ytm = uri.getQueryParameter("ytm"),
                    appleMusic = uri.getQueryParameter("apple_music")
                )
            } else if (uri.scheme == "tangible" && uri.host == "movie") {
                val typePriority = listOf("s", "f", "r", "b")
                val enabledProviders = movieProviders.filter { it.isEnabled }
                val candidates = StreamingAvailabilityRepository.ALL_SERVICE_KEYS
                    .mapNotNull { key ->
                        val encoded = uri.getQueryParameter(key) ?: return@mapNotNull null
                        val providerId = StreamingAvailabilityRepository.getProviderId(key) ?: return@mapNotNull null
                        val resolved = StreamingAvailabilityRepository.resolveService(key, encoded) ?: return@mapNotNull null
                        Triple(key, providerId, resolved)
                    }
                movieToPlay = if (movieProviders.isNotEmpty()) {
                    val enabledIds = enabledProviders.map { it.id }
                    candidates
                        .filter { it.second in enabledIds }
                        .sortedWith(compareBy(
                            { enabledIds.indexOf(it.second) },
                            { typePriority.indexOf(it.third.typeCode).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                        ))
                        .firstOrNull()?.third?.url
                } else {
                    candidates
                        .sortedBy { typePriority.indexOf(it.third.typeCode).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                        .firstOrNull()?.third?.url
                }
            } else if (uri.scheme == "https" && uri.host == "open.spotify.com") {
                val pathSegments = uri.pathSegments
                if (pathSegments.size >= 2) {
                    val spotifyUri = "spotify:${pathSegments[0]}:${pathSegments[1]}"
                    uris = if (pathSegments[0] == "album") TagMediaUris(spotifyAlbum = spotifyUri)
                           else TagMediaUris(spotifyTrack = spotifyUri)
                }
            } else if (uri.scheme == "https" && uri.host == "music.youtube.com") {
                uris = TagMediaUris(ytm = uri.getQueryParameter("list") ?: uri.toString())
            } else if (uri.scheme == "https" && uri.host?.endsWith("music.apple.com") == true) {
                val albumId = uri.lastPathSegment?.takeIf { seg -> seg.all { it.isDigit() } }
                uris = TagMediaUris(appleMusic = albumId ?: uri.toString())
            }
        }

        data?.let { parseTangibleUri(it) }

        if (uris.spotifyTrack == null && uris.spotifyAlbum == null && uris.ytm == null && uris.appleMusic == null && movieToPlay == null) {
            val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            rawMsgs?.forEach {
                val msg = it as NdefMessage
                msg.records.forEach { record ->
                    try { record.toUri()?.let { uri -> parseTangibleUri(uri) } } catch (e: Exception) { }
                }
            }
        }

        if (movieToPlay != null && !isWritingMode) {
            if (selectedMoviePlayer == MoviePlayer.GOOGLE_TV) {
                playMovieOnGoogleTv(movieToPlay!!)
            } else {
                playMovie(movieToPlay!!)
            }
            return
        }

        if ((uris.spotifyTrack != null || uris.spotifyAlbum != null || uris.ytm != null || uris.appleMusic != null) && !isWritingMode) {
            Log.d("Tangible", "Action: selectedService=$selectedService, selectedSpeaker=$selectedSpeaker, uris=$uris")
            when {
                selectedService == MusicService.SPOTIFY && selectedSpeaker == MusicSpeaker.THIS_DEVICE -> {
                    (uris.spotifyTrack ?: uris.spotifyAlbum)?.let { playOnSpotify(it) }
                }
                selectedService == MusicService.YOUTUBE_MUSIC && selectedSpeaker == MusicSpeaker.THIS_DEVICE -> {
                    uris.ytm?.let { playOnYouTubeMusic(it) }
                }
                selectedService == MusicService.APPLE_MUSIC && selectedSpeaker == MusicSpeaker.THIS_DEVICE -> {
                    uris.appleMusic?.let { playOnAppleMusic(it) }
                }
                selectedService == MusicService.SPOTIFY && selectedSpeaker == MusicSpeaker.SONOS -> {
                    (uris.spotifyAlbum ?: uris.spotifyTrack)?.let { playOnSonos(it) }
                }
                selectedService == MusicService.YOUTUBE_MUSIC && selectedSpeaker == MusicSpeaker.SONOS -> {
                    Toast.makeText(this, "YouTube Music + Sonos not yet implemented", Toast.LENGTH_SHORT).show()
                }
                selectedService == MusicService.APPLE_MUSIC && selectedSpeaker == MusicSpeaker.SONOS -> {
                    uris.appleMusic?.let { playOnSonos(it) }
                }
                selectedService == MusicService.SPOTIFY && selectedSpeaker == MusicSpeaker.CHROMECAST -> {
                    (uris.spotifyTrack ?: uris.spotifyAlbum)?.let { playOnChromecast(it, MusicService.SPOTIFY) }
                }
                selectedService == MusicService.YOUTUBE_MUSIC && selectedSpeaker == MusicSpeaker.CHROMECAST -> {
                    uris.ytm?.let { playOnChromecast(it, MusicService.YOUTUBE_MUSIC) }
                }
                selectedService == MusicService.APPLE_MUSIC && selectedSpeaker == MusicSpeaker.CHROMECAST -> {
                    uris.appleMusic?.let { playOnChromecast(it, MusicService.APPLE_MUSIC) }
                }
                else -> Log.w("Tangible", "No action for service $selectedService and speaker $selectedSpeaker")
            }
            return
        }

        Log.d("Tangible", "No URIs found or in writing mode. uris=$uris, isWritingMode=$isWritingMode")
    }

    private fun playMovieOnGoogleTv(url: String) {
        val host = chromecastDevices.firstOrNull { it.id == selectedChromecastId }?.host
            ?: selectedChromecastHost
        if (host == null) {
            Toast.makeText(this, "Please select a Google TV device in Settings", Toast.LENGTH_LONG).show()
            return
        }
        val uri = Uri.parse(url)
        val packageName: String
        val tvUrl: String
        if (uri.scheme == "stremio") {
            packageName = "com.stremio.one"
            // Mobile format: stremio://detail/movie/tt1234567
            // TV format:     stremio:///detail/movie/tt1234567/tt1234567
            val type = uri.pathSegments.getOrNull(0) ?: "movie"
            val imdbId = uri.pathSegments.getOrNull(1) ?: uri.lastPathSegment ?: ""
            tvUrl = "stremio:///detail/$type/$imdbId/$imdbId"
        } else {
            val urlHost = uri.host?.lowercase() ?: ""
            packageName = STREAMING_PACKAGES.entries.firstOrNull { urlHost.endsWith(it.key) }?.value ?: ""
            tvUrl = url
        }
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Opening movie on TV…", Toast.LENGTH_SHORT).show()
            val ok = adbRepository.launchUri(host, tvUrl, packageName)
            if (!ok) {
                Toast.makeText(
                    this@MainActivity,
                    "Could not open movie. Enable ADB debugging in Developer Options on your Google TV.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun playMovie(url: String) {
        val parsed = Uri.parse(url)
        val asin = if (parsed.host == "app.primevideo.com") {
            parsed.getQueryParameter("asin")
                ?: parsed.lastPathSegment?.takeIf { it.matches(Regex("[A-Z0-9]{10,}")) }
        } else null
        Log.d("Tangible", "playMovie: url=$url asin=$asin")
        if (asin != null) {
            lifecycleScope.launch {
                val resolved = StreamingAvailabilityRepository.resolveGtiUrl(asin) ?: url
                Log.d("Tangible", "playMovie: resolved=$resolved")
                doPlayMovie(resolved)
            }
            return
        }
        doPlayMovie(url)
    }

    private fun doPlayMovie(url: String) {
        val uri = Uri.parse(url)
        val targetPackage = if (uri.scheme == "stremio") {
            "com.stremio.one"
        } else {
            val host = uri.host?.lowercase() ?: ""
            STREAMING_PACKAGES.entries.firstOrNull { host.endsWith(it.key) }?.value
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            targetPackage?.let { setPackage(it) }
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            } catch (e2: Exception) {
                Toast.makeText(this, "No app found to open this streaming service", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open this streaming service", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playOnSonos(musicUri: String) {
        val ip = selectedSonosIp
        if (ip == null) {
            Toast.makeText(this, "Please select a Sonos speaker in Settings", Toast.LENGTH_LONG).show()
            return
        }
        openSonosApp()
        lifecycleScope.launch {
            val finalUri = when (selectedService) {
                MusicService.SPOTIFY -> {
                    if (musicUri.startsWith("spotify:")) musicUri
                    else spotifyRepository.searchAlbums(musicUri).firstOrNull()?.uri
                }
                MusicService.APPLE_MUSIC -> {
                    if (musicUri.startsWith("http")) musicUri else "https://music.apple.com/album/$musicUri"
                }
                else -> musicUri
            }
            if (finalUri == null) {
                Log.e("Tangible", "Could not resolve URI for $musicUri")
                return@launch
            }
            if (ip == SONOS_ALL_SPEAKERS) {
                val discovered = sonosDiscovery.discoverSpeakers()
                if (discovered.isNotEmpty()) sonosSpeakers = discovered
                if (sonosSpeakers.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No Sonos speakers found", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Playing on all speakers...", Toast.LENGTH_SHORT).show()
                    // Query the full topology to get one IP per group (its coordinator).
                    // Joining coordinators only is enough — each group's satellites auto-follow.
                    val coordinators = sonosRepository.getAllCoordinatorIps(sonosSpeakers[0].ip)
                        .ifEmpty { sonosSpeakers.map { it.ip } }
                    val masterIp = coordinators[0]
                    val masterUuid = sonosRepository.getSpeakerUuid(masterIp)
                    // Play on master first while it is in a stable state, then join the other
                    // group coordinators to it. Adding to an already-playing group is reliable.
                    if (selectedService == MusicService.APPLE_MUSIC) sonosRepository.playAppleMusicUri(masterIp, finalUri)
                    else sonosRepository.playSpotifyUri(masterIp, finalUri)
                    if (masterUuid != null) {
                        coordinators.drop(1).forEach { coordinatorIp ->
                            launch { sonosRepository.joinGroup(coordinatorIp, masterUuid) }
                        }
                    }
                }
            } else {
                if (selectedService == MusicService.APPLE_MUSIC) sonosRepository.playAppleMusicUri(ip, finalUri)
                else sonosRepository.playSpotifyUri(ip, finalUri)
            }
        }
    }

    private fun openSonosApp() {
        val packageName = "com.sonos.acr2"
        Log.d("Tangible", "Attempting to open Sonos app: $packageName")
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Log.d("Tangible", "Sonos app launch intent sent")
            } else {
                Log.w("Tangible", "Sonos app launch intent is null (app not found or visibility restricted)")
                // Try the other package name just in case
                val altPackage = "com.sonos.acr"
                val altIntent = packageManager.getLaunchIntentForPackage(altPackage)
                if (altIntent != null) {
                    altIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(altIntent)
                    Log.d("Tangible", "Sonos S2 app launch intent sent")
                }
            }
        } catch (e: Exception) {
            Log.e("Tangible", "Could not open Sonos app", e)
        }
    }

    private fun playOnAppleMusic(value: String) {
        val url = if (value.startsWith("http")) value else "https://music.apple.com/album/$value"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.apple.android.music")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun playOnSpotify(query: String) {
        val intent = if (query.startsWith("spotify:")) {
            Intent(Intent.ACTION_VIEW, Uri.parse(query)).apply {
                putExtra("android.intent.extra.play_state", true)
            }
        } else {
            Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/album")
                putExtra(SearchManager.QUERY, query)
                putExtra("android.intent.extra.play_state", true)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.setPackage("com.spotify.music")

        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback if Spotify is not installed or package name is different
            intent.setPackage(null)
            try { startActivity(intent) } catch (e2: Exception) { }
        }

        val remote = spotifyAppRemote
        if (remote != null && remote.isConnected) {
            remote.playerApi.play(query)
        } else {
            connectToSpotify {
                lifecycleScope.launch {
                    delay(1000)
                    spotifyAppRemote?.playerApi?.play(query)
                }
            }
        }
    }

    private fun playOnYouTubeMusic(ytmValue: String) {
        val listId = if (ytmValue.startsWith("http")) Uri.parse(ytmValue).getQueryParameter("list")
                     else ytmValue
        val url = if (listId != null) "https://music.youtube.com/watch?v=0&list=$listId&autoplay=1"
                  else ytmValue
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.google.android.apps.youtube.music")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { startActivity(intent) } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun enableNfcForegroundDispatch() {
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    private fun disableNfcForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(this)
    }

    private fun writeToTag(tag: Tag, data: String) {
        Log.d("Tangible", "Writing to tag: $data")
        val ndef = Ndef.get(tag)
        var formatable: NdefFormatable? = null
        try {
            val record = NdefRecord.createUri(data)
            val records = mutableListOf(record)
            if (data.startsWith("tangible:")) records.add(NdefRecord.createApplicationRecord(packageName))
            val message = NdefMessage(records.toTypedArray())
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    writeError = "Tag is read-only"
                    isWritingMode = false
                    return
                }
                if (ndef.maxSize < message.toByteArray().size) {
                    writeError = "Tag capacity too small (${ndef.maxSize} bytes)"
                    isWritingMode = false
                    return
                }
                ndef.writeNdefMessage(message)
                finishWriting()
            } else {
                formatable = NdefFormatable.get(tag)
                if (formatable != null) {
                    formatable.connect()
                    formatable.format(message)
                    finishWriting()
                } else {
                    writeError = "Tag doesn't support NDEF"
                    isWritingMode = false
                }
            }
        } catch (e: Exception) {
            Log.e("Tangible", "Error writing to tag", e)
            writeError = "Failed to write: ${e.message}"
            isWritingMode = false
        } finally {
            try { ndef?.close() } catch (e: Exception) { }
            try { formatable?.close() } catch (e: Exception) { }
        }
    }

    private fun playTagUrl(url: String) {
        handleIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun finishWriting() {
        val info = pendingTagInfo
        if (info != null) {
            val tag = SavedTag(
                id = java.util.UUID.randomUUID().toString(),
                title = info.title,
                subtitle = info.subtitle,
                type = info.type.name,
                url = info.url,
                imageUrl = info.imageUrl,
                createdAt = System.currentTimeMillis()
            )
            tagRepository.save(tag)
            savedTags = tagRepository.loadAll()
        }
        isWritingMode = false
        dataToWrite = null
        pendingTagInfo = null
        writeError = null
        showSuccessDialog = true
        Log.d("Tangible", "Successfully wrote to tag")
    }

    private fun deleteTags(tagIds: Set<String>) {
        tagRepository.delete(tagIds)
        savedTags = tagRepository.loadAll()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TangibleApp(
    savedTags: List<SavedTag>,
    onPlay: (String) -> Unit,
    isWritingMode: Boolean,
    writeError: String?,
    selectedService: MusicService,
    onServiceSelected: (MusicService) -> Unit,
    selectedSpeaker: MusicSpeaker,
    onSpeakerSelected: (MusicSpeaker) -> Unit,
    sonosSpeakers: List<SonosSpeaker>,
    selectedSonosIp: String?,
    onSonosSpeakerSelected: (String) -> Unit,
    onRefreshSonos: () -> Unit,
    isSonosRefreshing: Boolean,
    chromecastDevices: List<ChromecastDevice>,
    selectedChromecastId: String?,
    onChromecastDeviceSelected: (String) -> Unit,
    onRefreshChromecast: () -> Unit,
    isChromecastRefreshing: Boolean,
    selectedMoviePlayer: MoviePlayer,
    onMoviePlayerSelected: (MoviePlayer) -> Unit,
    onStartWriting: (TagWriteInfo) -> Unit,
    onCancelWriting: () -> Unit,
    onSearchAlbums: suspend (String) -> List<DiscogsSearchResult>,
    onSearchMovies: suspend (String) -> List<TmdbMovieResult>,
    onGetMovieStreaming: suspend (Int) -> MovieStreamingResult,
    selectedRegion: String,
    onRegionSelected: (String) -> Unit,
    onAlbumSelected: suspend (DiscogsSearchResult) -> TagMediaUris,
    movieProviders: List<MovieProvider>,
    onMovieProvidersChanged: (List<MovieProvider>) -> Unit,
    onGetServicesForRegion: suspend (String) -> List<MovieProvider>,
    onDeleteTags: (Set<String>) -> Unit,
    musicSortField: SortField,
    musicSortOrder: SortOrder,
    movieSortField: SortField,
    movieSortOrder: SortOrder,
    onMusicSortChange: (SortField, SortOrder) -> Unit,
    onMovieSortChange: (SortField, SortOrder) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showChrome = currentRoute == "collection" || currentRoute == "settings"

    var selectedTagIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Clear selection when navigating away from collection
    LaunchedEffect(currentRoute) {
        if (currentRoute != "collection") {
            selectedTagIds = emptySet()
        }
    }

    Scaffold(
        topBar = {
            if (showChrome) {
                TopAppBar(
                    title = {
                        if (currentRoute == "collection") {
                            if (selectedTagIds.isNotEmpty()) {
                                Text(
                                    text = "${selectedTagIds.size} selected",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            } else {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "Tangible",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = TangiblePurple
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Tap to Play",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        color = Color(0xFF888899),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        if (currentRoute == "collection" && selectedTagIds.isNotEmpty()) {
                            IconButton(onClick = { showDeleteConfirmation = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = TangibleBackground
                    )
                )
            }
        },
        bottomBar = {
            if (showChrome) {
                TangibleBottomBar(
                    currentRoute = currentRoute,
                    onCollectionClick = {
                        navController.navigate("collection") {
                            popUpTo("collection") { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onWriteClick = { navController.navigate("write_tag") },
                    onSettingsClick = {
                        navController.navigate("settings") {
                            popUpTo("collection") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        containerColor = TangibleBackground
    ) { innerPadding ->
        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Delete Tags") },
                text = { Text("Are you sure you want to delete the ${selectedTagIds.size} selected tags?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteTags(selectedTagIds)
                            selectedTagIds = emptySet()
                            showDeleteConfirmation = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        NavHost(
            navController = navController,
            startDestination = "collection",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("collection") {
                MainScreen(
                    tags = savedTags,
                    selectedTagIds = selectedTagIds,
                    musicSortField = musicSortField,
                    musicSortOrder = musicSortOrder,
                    movieSortField = movieSortField,
                    movieSortOrder = movieSortOrder,
                    onMusicSortChange = onMusicSortChange,
                    onMovieSortChange = onMovieSortChange,
                    onPlay = onPlay,
                    onTagClick = { tagId ->
                        if (selectedTagIds.isNotEmpty()) {
                            selectedTagIds = if (tagId in selectedTagIds) {
                                selectedTagIds - tagId
                            } else {
                                selectedTagIds + tagId
                            }
                        }
                    },
                    onTagLongClick = { tagId ->
                        selectedTagIds = selectedTagIds + tagId
                    }
                )
            }
            composable("write_tag") {
                WriteTagScreen(
                    onBackClick = {
                        onCancelWriting()
                        navController.popBackStack()
                    },
                    isWriting = isWritingMode,
                    writeError = writeError,
                    onWriteClick = { info -> onStartWriting(info) },
                    onCancelClick = onCancelWriting,
                    onSearchAlbums = onSearchAlbums,
                    onSearchMovies = onSearchMovies,
                    onGetMovieStreaming = onGetMovieStreaming,
                    region = selectedRegion,
                    onAlbumSelected = onAlbumSelected
                )
            }
            composable("settings") {
                SettingsScreen(
                    selectedService = selectedService,
                    selectedSpeaker = selectedSpeaker,
                    selectedMoviePlayer = selectedMoviePlayer,
                    selectedRegion = selectedRegion,
                    onMusicServiceClick = { navController.navigate("music_service") },
                    onMusicSpeakerClick = { navController.navigate("music_speaker") },
                    onMoviePlayerClick = { navController.navigate("movie_player") },
                    onMovieRegionClick = { navController.navigate("movie_region") },
                    onMovieProvidersClick = { navController.navigate("movie_providers") }
                )
            }
            composable("music_service") {
                MusicServiceScreen(
                    onBackClick = { navController.popBackStack() },
                    selectedService = selectedService,
                    onServiceSelected = onServiceSelected
                )
            }
            composable("music_speaker") {
                MusicSpeakerScreen(
                    onBackClick = { navController.popBackStack() },
                    selectedService = selectedService,
                    selectedSpeaker = selectedSpeaker,
                    onSpeakerSelected = onSpeakerSelected,
                    sonosSpeakers = sonosSpeakers,
                    selectedSonosIp = selectedSonosIp,
                    onSonosSpeakerSelected = onSonosSpeakerSelected,
                    onRefreshSonos = onRefreshSonos,
                    isSonosRefreshing = isSonosRefreshing,
                    chromecastDevices = chromecastDevices,
                    selectedChromecastId = selectedChromecastId,
                    onChromecastDeviceSelected = onChromecastDeviceSelected,
                    onRefreshChromecast = onRefreshChromecast,
                    isChromecastRefreshing = isChromecastRefreshing
                )
            }
            composable("movie_player") {
                MoviePlayerScreen(
                    onBackClick = { navController.popBackStack() },
                    selectedMoviePlayer = selectedMoviePlayer,
                    onMoviePlayerSelected = onMoviePlayerSelected,
                    chromecastDevices = chromecastDevices,
                    selectedChromecastId = selectedChromecastId,
                    onChromecastDeviceSelected = onChromecastDeviceSelected,
                    onRefreshChromecast = onRefreshChromecast,
                    isChromecastRefreshing = isChromecastRefreshing
                )
            }
            composable("movie_region") {
                MovieRegionScreen(
                    onBackClick = { navController.popBackStack() },
                    selectedRegion = selectedRegion,
                    onRegionSelected = onRegionSelected
                )
            }
            composable("movie_providers") {
                MovieProvidersScreen(
                    onBackClick = { navController.popBackStack() },
                    region = selectedRegion,
                    providers = movieProviders,
                    onProvidersChanged = onMovieProvidersChanged,
                    onGetServicesForRegion = onGetServicesForRegion
                )
            }
        }
    }
}

@Composable
private fun TangibleBottomBar(
    currentRoute: String?,
    onCollectionClick: () -> Unit,
    onWriteClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter),
            color = TangibleSurface,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    icon = Icons.Default.VideoLibrary,
                    label = "Collection",
                    selected = currentRoute == "collection",
                    onClick = onCollectionClick,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
                BottomNavItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    selected = currentRoute == "settings",
                    onClick = onSettingsClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        FloatingActionButton(
            onClick = onWriteClick,
            modifier = Modifier
                .size(60.dp)
                .align(Alignment.TopCenter),
            containerColor = TangiblePurple,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Write Tag",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) TangiblePurple else Color(0xFF888899)
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}
