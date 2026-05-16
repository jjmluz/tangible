package com.example.tangible

import android.app.PendingIntent
import android.app.SearchManager
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
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
import com.example.tangible.data.ChromecastDevice
import com.example.tangible.data.ChromecastDiscovery
import com.example.tangible.data.ChromecastRepository
import com.example.tangible.data.DiscogsRepository
import com.example.tangible.data.DiscogsSearchResult
import com.example.tangible.data.SonosDiscovery
import com.example.tangible.data.SonosRepository
import com.example.tangible.data.SonosSpeaker
import com.example.tangible.data.SpotifyAuthRepository
import com.example.tangible.data.SpotifyRepository
import com.example.tangible.data.YouTubeRepository
import com.example.tangible.ui.MainScreen
import com.example.tangible.ui.MusicService
import com.example.tangible.ui.MusicSpeaker
import com.example.tangible.ui.SettingsScreen
import com.example.tangible.ui.WriteTagScreen
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Data class to hold all resolved URIs for a tag
data class TagMediaUris(
    val spotifyTrack: String? = null,
    val spotifyAlbum: String? = null,
    val ytm: String? = null
)

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var isWritingMode by mutableStateOf(false)
    private var writeError by mutableStateOf<String?>(null)
    private var showSuccessDialog by mutableStateOf(false)
    private var dataToWrite: String? = null

    // Settings
    private val prefs by lazy { getSharedPreferences("tangible_prefs", MODE_PRIVATE) }
    private var selectedService by mutableStateOf(MusicService.SPOTIFY)
    private var selectedSpeaker by mutableStateOf(MusicSpeaker.THIS_DEVICE)
    private var selectedSonosIp by mutableStateOf<String?>(null)
    private var sonosSpeakers by mutableStateOf<List<SonosSpeaker>>(emptyList())
    private var chromecastDevices by mutableStateOf<List<ChromecastDevice>>(emptyList())
    private var selectedChromecastId by mutableStateOf<String?>(null)
    private var isSpotifyAuthorized by mutableStateOf(false)

    // Repositories
    private val discogsToken = "REMOVED_SECRET"
    private val discogsRepository by lazy { DiscogsRepository(discogsToken) }
    private val clientId = "REMOVED_SECRET"
    private val clientSecret = "REMOVED_SECRET"
    private val redirectUri = "com.example.tangible://callback"
    private val spotifyRepository by lazy { SpotifyRepository(clientId, clientSecret) }
    private val youtubeApiKey = "REMOVED_SECRET"
    private val youtubeRepository by lazy { YouTubeRepository(youtubeApiKey) }
    private val sonosDiscovery by lazy { SonosDiscovery(this) }
    private val sonosRepository by lazy { SonosRepository() }
    private val chromecastDiscovery by lazy { ChromecastDiscovery(this) }
    private val chromecastRepository by lazy { ChromecastRepository(this) }
    private val spotifyAuthRepository by lazy {
        SpotifyAuthRepository(this, clientId, clientSecret, redirectUri)
    }

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
        isSpotifyAuthorized = spotifyAuthRepository.isAuthorized

        // Handle deep link if app was started by it
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
                            if (it == MusicService.YOUTUBE_MUSIC &&
                                (selectedSpeaker == MusicSpeaker.SONOS || selectedSpeaker == MusicSpeaker.CHROMECAST)) {
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
                        },
                        onRefreshChromecast = { refreshChromecastDevices() },
                        isSpotifyAuthorized = isSpotifyAuthorized,
                        onConnectSpotify = { openSpotifyAuth() },
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
                        onSearchAlbums = { query ->
                            discogsRepository.searchAlbums(query)
                        },
                        onAlbumSelected = { discogsAlbum ->
                            val spotifyResults = spotifyRepository.searchAlbums(discogsAlbum.title)
                            val album = spotifyResults.firstOrNull()
                            val spotifyAlbumUri = album?.uri
                            val spotifyTrackUri = album?.let { spotifyRepository.getFirstTrackUri(it.id) }
                            
                            val parts = discogsAlbum.title.split(" - ", limit = 2)
                            val ytmUri = if (parts.size == 2) {
                                youtubeRepository.searchAlbum(parts[1], parts[0])
                            } else {
                                youtubeRepository.searchAlbum(discogsAlbum.title, "")
                            } ?: "https://music.youtube.com/search?q=${Uri.encode(discogsAlbum.title)}"
                            
                            TagMediaUris(
                                spotifyTrack = spotifyTrackUri,
                                spotifyAlbum = spotifyAlbumUri,
                                ytm = ytmUri
                            )
                        }
                    )

                    if (showSuccessDialog) {
                        AlertDialog(
                            onDismissRequest = { showSuccessDialog = false },
                            title = { Text("Success") },
                            text = { Text("Deep link successfully written to tag!") },
                            confirmButton = {
                                TextButton(onClick = { showSuccessDialog = false }) {
                                    Text("OK")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun refreshSonosSpeakers() {
        lifecycleScope.launch {
            sonosSpeakers = sonosDiscovery.discoverSpeakers()
            // Diagnostics: if a speaker is selected, log what it's playing
            selectedSonosIp?.let { ip ->
                sonosRepository.getPlayingInfo(ip)
            }
        }
    }

    private fun refreshChromecastDevices() {
        lifecycleScope.launch {
            chromecastDevices = chromecastDiscovery.discoverDevices()
        }
    }

    private fun playOnChromecast(spotifyUri: String) {
        val deviceId = selectedChromecastId
        if (deviceId == null) {
            Toast.makeText(this, "Please select a Chromecast device in Settings", Toast.LENGTH_LONG).show()
            return
        }
        val device = chromecastDevices.firstOrNull { it.id == deviceId }
        if (device == null) {
            Toast.makeText(this, "Selected Chromecast not found — tap refresh in Settings", Toast.LENGTH_LONG).show()
            return
        }
        if (!spotifyAuthRepository.isAuthorized) {
            Toast.makeText(this, "Connect your Spotify account in Settings first", Toast.LENGTH_LONG).show()
            openSpotifyAuth()
            return
        }
        lifecycleScope.launch {
            val ok = chromecastRepository.playSpotifyUri(device, spotifyUri, spotifyAuthRepository)
            if (!ok) {
                Toast.makeText(this@MainActivity, "Could not play on Chromecast", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openSpotifyAuth() {
        val url = spotifyAuthRepository.buildAuthUrl()
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { startActivity(browserIntent) } catch (e: Exception) {
            Toast.makeText(this, "No browser found to open Spotify login", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        connectToSpotify()
    }

    override fun onStop() {
        super.onStop()
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
    }

    private fun connectToSpotify(onConnected: () -> Unit = {}) {
        if (clientId == "YOUR_CLIENT_ID") return

        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
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

        // Handle Spotify OAuth callback
        if (data?.scheme == "com.example.tangible" && data.host == "callback") {
            val code = data.getQueryParameter("code")
            if (code != null) {
                lifecycleScope.launch {
                    val success = spotifyAuthRepository.handleAuthCode(code)
                    isSpotifyAuthorized = success
                    if (success) {
                        Toast.makeText(this@MainActivity, "Spotify connected", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Spotify login failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return
        }

        var uris = TagMediaUris()

        fun parseTangibleUri(uri: Uri) {
            if (uri.scheme == "tangible" && (uri.host == "play" || uri.host == null)) {
                uris = TagMediaUris(
                    spotifyTrack = uri.getQueryParameter("spotify_track") ?: uri.getQueryParameter("spotify") ?: uri.getQueryParameter("query"),
                    spotifyAlbum = uri.getQueryParameter("spotify_album"),
                    ytm = uri.getQueryParameter("ytm")
                )
            } else if (uri.scheme == "https" && uri.host == "open.spotify.com") {
                val pathSegments = uri.pathSegments
                if (pathSegments.size >= 2) {
                    val spotifyUri = "spotify:${pathSegments[0]}:${pathSegments[1]}"
                    uris = if (pathSegments[0] == "album") {
                        TagMediaUris(spotifyAlbum = spotifyUri)
                    } else {
                        TagMediaUris(spotifyTrack = spotifyUri)
                    }
                }
            }
        }

        data?.let { parseTangibleUri(it) }

        if (uris.spotifyTrack == null && uris.spotifyAlbum == null && uris.ytm == null) {
            val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            rawMsgs?.forEach {
                val msg = it as NdefMessage
                msg.records.forEach { record ->
                    try {
                        val uri = record.toUri()
                        if (uri != null) parseTangibleUri(uri)
                    } catch (e: Exception) {}
                }
            }
        }

        if ((uris.spotifyTrack != null || uris.spotifyAlbum != null || uris.ytm != null) && !isWritingMode) {
            Log.d("Tangible", "Action: selectedService=$selectedService, selectedSpeaker=$selectedSpeaker, uris=$uris")
            when {
                selectedService == MusicService.SPOTIFY && selectedSpeaker == MusicSpeaker.THIS_DEVICE -> {
                    val toPlay = uris.spotifyTrack ?: uris.spotifyAlbum
                    toPlay?.let { playOnSpotify(it) }
                }
                selectedService == MusicService.YOUTUBE_MUSIC && selectedSpeaker == MusicSpeaker.THIS_DEVICE -> {
                    uris.ytm?.let { playOnYouTubeMusic(it) }
                }
                selectedService == MusicService.SPOTIFY && selectedSpeaker == MusicSpeaker.SONOS -> {
                    val toPlay = uris.spotifyAlbum ?: uris.spotifyTrack
                    toPlay?.let { playOnSonos(it) }
                }
                selectedService == MusicService.YOUTUBE_MUSIC && selectedSpeaker == MusicSpeaker.SONOS -> {
                    Toast.makeText(this, "YouTube Music + Sonos not yet implemented", Toast.LENGTH_SHORT).show()
                }
                selectedService == MusicService.SPOTIFY && selectedSpeaker == MusicSpeaker.CHROMECAST -> {
                    val toPlay = uris.spotifyAlbum ?: uris.spotifyTrack
                    toPlay?.let { playOnChromecast(it) }
                }
            }
            return
        }

        if (isWritingMode) {
            if (NfcAdapter.ACTION_TAG_DISCOVERED == action ||
                NfcAdapter.ACTION_NDEF_DISCOVERED == action ||
                NfcAdapter.ACTION_TECH_DISCOVERED == action
            ) {
                val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                }
                tag?.let { writeToTag(it, dataToWrite ?: "") }
            }
        }
    }

    private fun playOnSonos(spotifyUri: String) {
        val ip = selectedSonosIp
        if (ip == null) {
            Toast.makeText(this, "Please select a Sonos speaker in Settings", Toast.LENGTH_LONG).show()
            return
        }
        
        lifecycleScope.launch {
            if (spotifyUri.startsWith("spotify:")) {
                sonosRepository.playSpotifyUri(ip, spotifyUri)
            } else {
                val results = spotifyRepository.searchAlbums(spotifyUri)
                results.firstOrNull()?.let { album ->
                    sonosRepository.playSpotifyUri(ip, album.uri)
                }
            }
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
            try { startActivity(intent) } catch (e: Exception) {}
            connectToSpotify {
                lifecycleScope.launch {
                    delay(1000)
                    spotifyAppRemote?.playerApi?.play(query)
                }
            }
        }
    }

    private fun playOnYouTubeMusic(uriString: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
            setPackage("com.google.android.apps.youtube.music")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { startActivity(intent) } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
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
        val filters = arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, filters, null)
    }

    private fun disableNfcForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(this)
    }

    private fun writeToTag(tag: Tag, data: String) {
        val ndef = Ndef.get(tag) ?: return
        try {
            ndef.connect()
            val record = NdefRecord.createUri(data)
            val records = mutableListOf(record)
            if (data.startsWith("tangible:")) {
                records.add(NdefRecord.createApplicationRecord(packageName))
            }
            val message = NdefMessage(records.toTypedArray())
            ndef.writeNdefMessage(message)
            isWritingMode = false
            dataToWrite = null
            writeError = null
            showSuccessDialog = true
        } catch (e: Exception) {
            writeError = "Failed to write: ${e.message}"
        } finally {
            try { ndef.close() } catch (e: Exception) {}
        }
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
    isSpotifyAuthorized: Boolean,
    onConnectSpotify: () -> Unit,
    onStartWriting: (String) -> Unit,
    onCancelWriting: () -> Unit,
    onSearchAlbums: suspend (String) -> List<DiscogsSearchResult>,
    onAlbumSelected: suspend (DiscogsSearchResult) -> TagMediaUris
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
                onWriteClick = { url ->
                    onStartWriting(url)
                },
                onCancelClick = onCancelWriting,
                onSearchAlbums = onSearchAlbums,
                onAlbumSelected = onAlbumSelected
            )
        }
        composable("settings") {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
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
                isSpotifyAuthorized = isSpotifyAuthorized,
                onConnectSpotify = onConnectSpotify
            )
        }
    }
}
