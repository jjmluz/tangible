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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import com.example.tangible.data.TmdbMovieResult
import com.example.tangible.data.TmdbRepository
import com.example.tangible.data.YouTubeRepository
import com.example.tangible.ui.MainScreen
import com.example.tangible.ui.MovieProvidersScreen
import com.example.tangible.ui.MusicService
import com.example.tangible.ui.MusicSpeaker
import com.example.tangible.ui.SettingsScreen
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

const val SONOS_ALL_SPEAKERS = "ALL_SPEAKERS"

private val STREAMING_PACKAGES = mapOf(
    "netflix.com" to "com.netflix.mediaclient",
    "primevideo.com" to "com.amazon.avod.thirdpartyclient",
    "disneyplus.com" to "com.disney.disneyplus",
    "play.max.com" to "com.wbd.stream",
    "hulu.com" to "com.hulu.plus",
    "tv.apple.com" to "com.apple.atve.androidtv.appletv",
    "paramountplus.com" to "com.cbs.app",
    "peacocktv.com" to "com.peacocktv.peacockandroid"
)

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var isWritingMode by mutableStateOf(false)
    private var writeError by mutableStateOf<String?>(null)
    private var showSuccessDialog by mutableStateOf(false)
    private var dataToWrite: String? = null

    // Settings
    private val prefs by lazy { getSharedPreferences("tangible_prefs", MODE_PRIVATE) }
    private val gson = Gson()
    private var selectedService by mutableStateOf(MusicService.SPOTIFY)
    private var selectedSpeaker by mutableStateOf(MusicSpeaker.THIS_DEVICE)
    private var selectedSonosIp by mutableStateOf<String?>(null)
    private var sonosSpeakers by mutableStateOf<List<SonosSpeaker>>(emptyList())
    private var chromecastDevices by mutableStateOf<List<ChromecastDevice>>(emptyList())
    private var selectedChromecastId by mutableStateOf<String?>(null)
    private var selectedChromecastName by mutableStateOf<String?>(null)
    private var selectedChromecastHost: String? = null
    private var region by mutableStateOf("US")
    private var movieProviders by mutableStateOf<List<MovieProvider>>(emptyList())

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
        region = prefs.getString("region", "US") ?: "US"
        loadMovieProviders()

        intent?.let { handleIntent(it) }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TangibleApp(
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
                        onStartWriting = { url ->
                            dataToWrite = url
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
                        onGetServicesForRegion = { reg -> streamingRepository.getServicesForRegion(reg) }
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

    private fun refreshSonosSpeakers() {
        lifecycleScope.launch {
            sonosSpeakers = sonosDiscovery.discoverSpeakers()
            val ip = selectedSonosIp
            if (ip != null && ip != SONOS_ALL_SPEAKERS) {
                sonosRepository.getPlayingInfo(ip)
            }
        }
    }

    private fun refreshChromecastDevices() {
        lifecycleScope.launch {
            chromecastDevices = chromecastDiscovery.discoverDevices()
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
            playMovie(movieToPlay!!)
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

    private fun playMovie(url: String) {
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
                    Toast.makeText(this@MainActivity, "Grouping ${sonosSpeakers.size} speakers and playing...", Toast.LENGTH_SHORT).show()
                    val master = sonosSpeakers[0]
                    val masterUuid = sonosRepository.getSpeakerUuid(master.ip)
                    if (masterUuid != null) {
                        sonosRepository.becomeMaster(master.ip)
                        delay(500)
                        sonosSpeakers.drop(1).forEach { slave ->
                            launch {
                                sonosRepository.stop(slave.ip)
                                delay(200)
                                sonosRepository.joinGroup(slave.ip, masterUuid)
                            }
                        }
                        delay(3000)
                    }
                    if (selectedService == MusicService.APPLE_MUSIC) sonosRepository.playAppleMusicUri(master.ip, finalUri)
                    else sonosRepository.playSpotifyUri(master.ip, finalUri)
                }
            } else {
                if (selectedService == MusicService.APPLE_MUSIC) sonosRepository.playAppleMusicUri(ip, finalUri)
                else sonosRepository.playSpotifyUri(ip, finalUri)
            }
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
        val remote = spotifyAppRemote
        if (remote != null && remote.isConnected) {
            remote.playerApi.play(query)
        } else {
            val intent = if (query.startsWith("spotify:")) {
                Intent(Intent.ACTION_VIEW, Uri.parse(query)).apply {
                    putExtra("android.intent.extra.play_state", true)
                }
            } else {
                Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/album")
                    putExtra(SearchManager.QUERY, query)
                    putExtra("android.intent.extra.play_state", true)
                    `package` = "com.spotify.music"
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { startActivity(intent) } catch (e: Exception) { }
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

    private fun finishWriting() {
        isWritingMode = false
        dataToWrite = null
        writeError = null
        showSuccessDialog = true
        Log.d("Tangible", "Successfully wrote to tag")
    }
}

@Composable
fun TangibleApp(
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
    chromecastDevices: List<ChromecastDevice>,
    selectedChromecastId: String?,
    onChromecastDeviceSelected: (String) -> Unit,
    onRefreshChromecast: () -> Unit,
    onStartWriting: (String) -> Unit,
    onCancelWriting: () -> Unit,
    onSearchAlbums: suspend (String) -> List<DiscogsSearchResult>,
    onSearchMovies: suspend (String) -> List<TmdbMovieResult>,
    onGetMovieStreaming: suspend (Int) -> MovieStreamingResult,
    selectedRegion: String,
    onRegionSelected: (String) -> Unit,
    onAlbumSelected: suspend (DiscogsSearchResult) -> TagMediaUris,
    movieProviders: List<MovieProvider>,
    onMovieProvidersChanged: (List<MovieProvider>) -> Unit,
    onGetServicesForRegion: suspend (String) -> List<MovieProvider>
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                onAddClick = { navController.navigate("write_tag") },
                onSettingsClick = { navController.navigate("settings") }
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
                onWriteClick = { url -> onStartWriting(url) },
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
                onBackClick = { navController.popBackStack() },
                onMovieProvidersClick = { navController.navigate("movie_providers") },
                selectedService = selectedService,
                onServiceSelected = onServiceSelected,
                selectedSpeaker = selectedSpeaker,
                onSpeakerSelected = onSpeakerSelected,
                sonosSpeakers = sonosSpeakers,
                selectedSonosIp = selectedSonosIp,
                onSonosSpeakerSelected = onSonosSpeakerSelected,
                onRefreshSonos = onRefreshSonos,
                chromecastDevices = chromecastDevices,
                selectedChromecastId = selectedChromecastId,
                onChromecastDeviceSelected = onChromecastDeviceSelected,
                onRefreshChromecast = onRefreshChromecast,
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
