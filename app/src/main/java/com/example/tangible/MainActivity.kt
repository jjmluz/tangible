package com.example.tangible

import android.app.PendingIntent
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
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import com.example.tangible.data.AdbRepository
import com.example.tangible.data.ChromecastDevice
import com.example.tangible.data.ChromecastDiscovery
import com.example.tangible.data.DiscogsRepository
import com.example.tangible.data.DiscogsSearchResult
import com.example.tangible.data.HomeAssistantRepository
import com.example.tangible.data.MovieProvider
import com.example.tangible.data.MovieStreamingResult
import com.example.tangible.data.MusicAssistantPlayer
import com.example.tangible.data.SpotifyRepository
import com.example.tangible.data.StreamingAvailabilityRepository
import com.example.tangible.data.TagRepository
import com.example.tangible.data.TmdbMovieResult
import com.example.tangible.data.TmdbRepository
import com.example.tangible.ui.MainScreen
import com.example.tangible.ui.MiniPlayer
import com.example.tangible.ui.NowPlayingScreen
import com.example.tangible.ui.SortField
import com.example.tangible.ui.SortOrder
import com.example.tangible.ui.ViewMode
import com.example.tangible.ui.MoviePlayerScreen
import com.example.tangible.ui.MovieProvidersScreen
import com.example.tangible.ui.MoviePlayer
import com.example.tangible.ui.MovieRegionScreen
import com.example.tangible.ui.MusicAssistantScreen
import com.example.tangible.ui.SettingsScreen
import com.example.tangible.ui.TagWriteInfo
import com.example.tangible.ui.WelcomeWizardScreen
import com.example.tangible.ui.WriteTagScreen
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Music tags look like `tangible://play?spotify_album=<id>`. */
const val SPOTIFY_ALBUM_PARAM = "spotify_album"

data class SavedTag(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: String, // "MUSIC" or "MOVIE"
    val url: String,
    val imageUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val customService: String? = null,
    val customOutputId: String? = null
)

private val STREAMING_PACKAGES = mapOf(
    "netflix.com" to "com.netflix.ninja",
    "primevideo.com" to "com.amazon.amazonvideo.livingroom",
    "disneyplus.com" to "com.disney.disneyplus",
    "play.max.com" to "com.wbd.stream",
    "tv.apple.com" to "com.apple.atve.androidtv.appletv",
    "skyshowtime.com" to "com.skyshowtime.skyshowtime.google"
)

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var isWritingMode by mutableStateOf(false)
    private var isNfcTagLaunchBlocked by mutableStateOf(false)
    private var writeError by mutableStateOf<String?>(null)
    private var showSuccessDialog by mutableStateOf(false)
    private var dataToWrite: String? = null
    private var pendingTagInfo: TagWriteInfo? = null
    private var savedTags by mutableStateOf<List<SavedTag>>(emptyList())

    // Settings
    private val prefs by lazy { getSharedPreferences("tangible_prefs", MODE_PRIVATE) }
    private val tagRepository by lazy { TagRepository(prefs) }
    private val gson = Gson()
    private var chromecastDevices by mutableStateOf<List<ChromecastDevice>>(emptyList())
    private var isChromecastRefreshing by mutableStateOf(false)
    private var selectedChromecastId by mutableStateOf<String?>(null)
    private var selectedChromecastName by mutableStateOf<String?>(null)
    private var selectedChromecastHost: String? = null
    private var haServerUrl by mutableStateOf("")
    private var haAccessToken by mutableStateOf("")
    private var selectedMaPlayerId by mutableStateOf<String?>(null)
    /** 0–100, applied right before an album starts; null leaves the volume alone. */
    private var maDefaultVolume by mutableStateOf<Int?>(null)
    private var maPlayers by mutableStateOf<List<MusicAssistantPlayer>>(emptyList())
    private var isMaRefreshing by mutableStateOf(false)
    private var maError by mutableStateOf<String?>(null)
    private var selectedMoviePlayer by mutableStateOf(MoviePlayer.THIS_DEVICE)
    private var region by mutableStateOf("US")
    private var movieProviders by mutableStateOf<List<MovieProvider>>(emptyList())
    private var hasShownWelcomeWizard by mutableStateOf(false)

    // Sorting
    private var musicSortField by mutableStateOf(SortField.TITLE)
    private var musicSortOrder by mutableStateOf(SortOrder.ASCENDING)
    private var movieSortField by mutableStateOf(SortField.TITLE)
    private var movieSortOrder by mutableStateOf(SortOrder.ASCENDING)
    private var collectionViewMode by mutableStateOf(ViewMode.LIST)

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
    private val chromecastDiscovery by lazy { ChromecastDiscovery(this) }
    private val adbRepository by lazy { AdbRepository(this) }
    private val homeAssistantRepository by lazy { HomeAssistantRepository() }
    private val nowPlaying by lazy {
        NowPlayingController(
            repository = homeAssistantRepository,
            scope = lifecycleScope,
            settings = { haServerUrl to haAccessToken },
            onPlayerChanged = { prefs.edit().putString("now_playing_player_id", it).apply() }
        )
    }
    /** Bumped when a scan should bring up the Now Playing screen. */
    private var openNowPlayingRequest by mutableIntStateOf(0)

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportCollection(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importCollection(it) }
    }

    private fun exportCollection(uri: Uri) {
        lifecycleScope.launch {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val json = tagRepository.exportJson()
                    outputStream.write(json.toByteArray())
                }
                Toast.makeText(this@MainActivity, "Collection exported successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun importCollection(uri: Uri) {
        lifecycleScope.launch {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val json = inputStream.bufferedReader().readText()
                    if (tagRepository.importJson(json)) {
                        savedTags = tagRepository.loadAll()
                        Toast.makeText(this@MainActivity, "Collection imported successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Import failed: Invalid file format", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        // Load settings
        selectedChromecastId = prefs.getString("selected_chromecast_id", null)
        selectedChromecastName = prefs.getString("selected_chromecast_name", null)
        selectedChromecastHost = prefs.getString("selected_chromecast_host", null)
        haServerUrl = prefs.getString("ha_server_url", "") ?: ""
        haAccessToken = prefs.getString("ha_access_token", "") ?: ""
        selectedMaPlayerId = prefs.getString("selected_ma_player_id", null)
        maDefaultVolume = prefs.getInt("ma_default_volume", -1).takeIf { it in 0..100 }
        val moviePlayerValue = prefs.getString("selected_movie_player", MoviePlayer.THIS_DEVICE.name)
        selectedMoviePlayer = MoviePlayer.entries.find { it.name == moviePlayerValue } ?: MoviePlayer.THIS_DEVICE
        region = prefs.getString("region", "US") ?: "US"
        hasShownWelcomeWizard = prefs.getBoolean("has_shown_welcome_wizard", false)

        // Load sort settings
        musicSortField = SortField.valueOf(prefs.getString("music_sort_field", SortField.TITLE.name) ?: SortField.TITLE.name)
        musicSortOrder = SortOrder.valueOf(prefs.getString("music_sort_order", SortOrder.ASCENDING.name) ?: SortOrder.ASCENDING.name)
        movieSortField = SortField.valueOf(prefs.getString("movie_sort_field", SortField.TITLE.name) ?: SortField.TITLE.name)
        movieSortOrder = SortOrder.valueOf(prefs.getString("movie_sort_order", SortOrder.ASCENDING.name) ?: SortOrder.ASCENDING.name)
        collectionViewMode = ViewMode.valueOf(prefs.getString("collection_view_mode", ViewMode.LIST.name) ?: ViewMode.LIST.name)

        loadMovieProviders()
        loadChromecastDevices()
        loadMaPlayers()
        nowPlaying.setPlayer(prefs.getString("now_playing_player_id", null) ?: selectedMaPlayerId)
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
                        onPlay = { tag -> playTagUrl(tag) },
                        onUpdateTag = { tag ->
                            tagRepository.update(tag)
                            savedTags = tagRepository.loadAll()
                        },
                        isWritingMode = isWritingMode,
                        writeError = writeError,
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
                        haServerUrl = haServerUrl,
                        onHaServerUrlChange = { url ->
                            haServerUrl = url
                            prefs.edit().putString("ha_server_url", url).apply()
                        },
                        haAccessToken = haAccessToken,
                        onHaAccessTokenChange = { token ->
                            haAccessToken = token
                            prefs.edit().putString("ha_access_token", token).apply()
                        },
                        maPlayers = maPlayers,
                        selectedMaPlayerId = selectedMaPlayerId,
                        onMaPlayerSelected = { entityId ->
                            selectedMaPlayerId = entityId
                            prefs.edit().putString("selected_ma_player_id", entityId).apply()
                            // Follow the new default unless something is playing elsewhere.
                            if (nowPlaying.status?.isActive != true) nowPlaying.setPlayer(entityId)
                        },
                        onRefreshMa = { refreshMaPlayers() },
                        maDefaultVolume = maDefaultVolume,
                        onMaDefaultVolumeChange = { level ->
                            maDefaultVolume = level
                            prefs.edit().apply {
                                if (level == null) remove("ma_default_volume") else putInt("ma_default_volume", level)
                            }.apply()
                        },
                        isMaRefreshing = isMaRefreshing,
                        maError = maError,
                        isNfcTagLaunchBlocked = isNfcTagLaunchBlocked,
                        onFixNfcTagLaunch = { openNfcTagLaunchSettings() },
                        selectedMoviePlayer = selectedMoviePlayer,
                        onMoviePlayerSelected = {
                            selectedMoviePlayer = it
                            prefs.edit().putString("selected_movie_player", it.name).apply()
                        },
                        onStartWriting = { info ->
                            Log.d("Tangible", "onStartWriting: url=${info.url}")
                            dataToWrite = info.url
                            pendingTagInfo = info
                            isWritingMode = true
                            writeError = null
                        },
                        onCancelWriting = {
                            Log.d("Tangible", "onCancelWriting")
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
                            spotifyRepository.searchAlbums(discogsAlbum.title).firstOrNull()?.id
                        },
                        movieProviders = movieProviders,
                        onMovieProvidersChanged = { providers ->
                            movieProviders = providers
                            saveMovieProviders(providers)
                        },
                        onGetServicesForRegion = { reg -> streamingRepository.getServicesForRegion(reg) },
                        onDeleteTags = { deleteTags(it) },
                        hasShownWelcomeWizard = hasShownWelcomeWizard,
                        onWelcomeWizardShown = {
                            hasShownWelcomeWizard = true
                            prefs.edit().putBoolean("has_shown_welcome_wizard", true).apply()
                        },
                        musicSortField = musicSortField,
                        musicSortOrder = musicSortOrder,
                        movieSortField = movieSortField,
                        movieSortOrder = movieSortOrder,
                        viewMode = collectionViewMode,
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
                        },
                        onViewModeChange = { mode ->
                            collectionViewMode = mode
                            prefs.edit().putString("collection_view_mode", mode.name).apply()
                        },
                        onExportClick = { exportLauncher.launch("tangible_backup.json") },
                        onImportClick = { importLauncher.launch(arrayOf("application/json")) },
                        nowPlaying = nowPlaying,
                        openNowPlayingRequest = openNowPlayingRequest,
                        onRefreshMaPlayers = { refreshMaPlayers() },
                        showSuccessDialog = showSuccessDialog,
                        onSuccessDismissed = { showSuccessDialog = false }
                    )
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

    private fun loadMaPlayers() {
        val json = prefs.getString("cached_ma_players", null) ?: return
        val type = object : TypeToken<List<MusicAssistantPlayer>>() {}.type
        maPlayers = runCatching { gson.fromJson<List<MusicAssistantPlayer>>(json, type) }.getOrNull() ?: emptyList()
    }

    private fun refreshMaPlayers() {
        if (isMaRefreshing) return
        if (haServerUrl.isBlank() || haAccessToken.isBlank()) {
            maError = "Enter your Home Assistant URL and access token first."
            return
        }
        isMaRefreshing = true
        maError = null
        lifecycleScope.launch {
            homeAssistantRepository.fetchPlayers(haServerUrl, haAccessToken)
                .onSuccess { players ->
                    maPlayers = players
                    prefs.edit().putString("cached_ma_players", gson.toJson(players)).apply()
                    if (players.isEmpty()) {
                        maError = "No Music Assistant players found. Is the Music Assistant integration set up in Home Assistant?"
                    }
                }
                .onFailure { e -> maError = e.message ?: e.javaClass.simpleName }
            isMaRefreshing = false
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

    override fun onStart() {
        super.onStart()
        nowPlaying.startPolling()
    }

    override fun onStop() {
        super.onStop()
        nowPlaying.stopPolling()
    }

    override fun onResume() {
        super.onResume()
        enableNfcForegroundDispatch()
        isNfcTagLaunchBlocked = checkNfcTagLaunchBlocked()
    }

    /**
     * Android 15+ lets the user stop individual apps from being opened by NFC tags. When Tangible
     * is blocked, a scan only brings the app to the front without the tag data, so nothing plays.
     */
    private fun checkNfcTagLaunchBlocked(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
        val adapter = nfcAdapter ?: return false
        return runCatching {
            adapter.isTagIntentAppPreferenceSupported && !adapter.isTagIntentAllowed
        }.getOrDefault(false)
    }

    private fun openNfcTagLaunchSettings() {
        try {
            startActivity(Intent(NfcAdapter.ACTION_CHANGE_TAG_INTENT_PREFERENCE))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS))
        }
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

    private fun playTagUrl(tag: SavedTag) {
        handleIntent(Intent(Intent.ACTION_VIEW, Uri.parse(tag.url)), tag)
    }

    private fun handleIntent(intent: Intent, customTag: SavedTag? = null) {
        val action = intent.action
        val data: Uri? = intent.data
        val dataString = intent.dataString

        Log.d("Tangible", "Handling intent: action=$action, data=$data, isWritingMode=$isWritingMode")

        // Find saved tag for custom settings if not provided
        val savedTag = customTag ?: savedTags.find { it.url == dataString }

        if (isWritingMode) {
            val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            if (tag != null) {
                writeToTag(tag, dataToWrite ?: "")
                return
            }
        }

        var spotifyAlbumId: String? = null
        var movieToPlay: String? = null

        fun parseTangibleUri(uri: Uri) {
            if (uri.scheme == "tangible" && (uri.host == "play" || uri.host == null)) {
                spotifyAlbumId = uri.getQueryParameter(SPOTIFY_ALBUM_PARAM)?.takeIf { it.isNotBlank() }
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

                val customServiceKey = savedTag?.customService
                movieToPlay = if (customServiceKey != null) {
                    candidates.find { it.first == customServiceKey }?.third?.url
                } else if (movieProviders.isNotEmpty()) {
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
            }
        }

        data?.let { parseTangibleUri(it) }

        if (spotifyAlbumId == null && movieToPlay == null) {
            val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            rawMsgs?.forEach {
                val msg = it as NdefMessage
                msg.records.forEach { record ->
                    try { record.toUri()?.let { uri -> parseTangibleUri(uri) } } catch (e: Exception) { }
                }
            }
        }

        if (movieToPlay != null && !isWritingMode) {
            val effectiveMoviePlayer = when {
                savedTag?.customOutputId == "THIS_DEVICE" -> MoviePlayer.THIS_DEVICE
                savedTag?.customOutputId?.startsWith("CHROMECAST:") == true -> MoviePlayer.GOOGLE_TV
                else -> selectedMoviePlayer
            }
            val effectiveChromecastId = if (savedTag?.customOutputId?.startsWith("CHROMECAST:") == true) {
                savedTag.customOutputId.removePrefix("CHROMECAST:")
            } else {
                selectedChromecastId
            }

            if (effectiveMoviePlayer == MoviePlayer.GOOGLE_TV) {
                playMovieOnGoogleTv(movieToPlay!!, effectiveChromecastId)
            } else {
                playMovie(movieToPlay!!)
            }
            return
        }

        val albumId = spotifyAlbumId
        if (albumId != null && !isWritingMode) {
            // Every music tag plays on the default player, from a scan or a tap in the collection.
            val preview = savedTag?.let { AlbumPreview(it.title, it.subtitle.ifBlank { null }, it.imageUrl) }
            playOnMusicAssistant(albumId, selectedMaPlayerId, preview)
            return
        }

        Log.d("Tangible", "No media found or in writing mode. isWritingMode=$isWritingMode")
    }

    private fun playMovieOnGoogleTv(url: String, customDeviceId: String? = null) {
        val host = if (customDeviceId != null) {
            chromecastDevices.find { it.id == customDeviceId }?.host
        } else {
            chromecastDevices.firstOrNull { it.id == selectedChromecastId }?.host ?: selectedChromecastHost
        }
        
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
            
            // For Prime Video, try the ASIN directly first as GTI might cause error 9345 on some TV versions
            val asin = if (urlHost.contains("primevideo.com")) {
                uri.getQueryParameter("asin") ?: uri.lastPathSegment?.takeIf { it.matches(Regex("[A-Z0-9]{10,}")) }
            } else null
            
            if (asin != null) {
                lifecycleScope.launch {
                    val tvAsinUrl = "https://app.primevideo.com/detail?asin=$asin"
                    val ok = adbRepository.launchUri(host, tvAsinUrl, packageName, forceStop = true)
                    if (!ok) {
                        // Fallback to GTI if direct ASIN fails
                        val resolved = StreamingAvailabilityRepository.resolveGtiUrl(asin) ?: url
                        adbRepository.launchUri(host, resolved, packageName, forceStop = true)
                    }
                }
                return
            }
            tvUrl = url
        }
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Opening movie on TV…", Toast.LENGTH_SHORT).show()
            val extras = if (packageName == "com.netflix.ninja") mapOf("source" to "30") else emptyMap()
            val component = if (packageName == "com.netflix.ninja") "com.netflix.ninja/.MainActivity" else ""
            val ok = adbRepository.launchUri(host, tvUrl, packageName, extras, component, forceStop = true)
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
            // URL is either https://app.primevideo.com/detail?asin=... or https://app.primevideo.com/detail/ASIN
            parsed.getQueryParameter("asin")
                ?: parsed.lastPathSegment?.takeIf { it.matches(Regex("[A-Z0-9]{10,}")) }
        } else if (parsed.host == "www.primevideo.com" || parsed.host == "primevideo.com") {
            parsed.lastPathSegment?.takeIf { it.matches(Regex("[A-Z0-9]{10,}")) }
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

    private fun playOnMusicAssistant(spotifyAlbumId: String, playerId: String?, preview: AlbumPreview? = null) {
        if (haServerUrl.isBlank() || haAccessToken.isBlank()) {
            Toast.makeText(this, "Set up Music Assistant in Settings first", Toast.LENGTH_LONG).show()
            return
        }
        if (playerId == null) {
            Toast.makeText(this, "Pick a Music Assistant player in Settings", Toast.LENGTH_LONG).show()
            return
        }
        nowPlaying.playbackStarting(playerId, preview)
        openNowPlayingRequest++
        val volume = maDefaultVolume
        lifecycleScope.launch {
            // Set the volume first so the album doesn't start loud. A failure here shouldn't stop playback.
            if (volume != null) {
                homeAssistantRepository.setVolume(haServerUrl, haAccessToken, playerId, volume / 100f)
                    .onFailure { Log.w("Tangible", "Couldn't set the default volume: ${it.message}") }
            }
            val result = homeAssistantRepository.playSpotifyAlbum(haServerUrl, haAccessToken, playerId, spotifyAlbumId)
            nowPlaying.playbackStarted(result)
            result.onFailure { e ->
                Toast.makeText(this@MainActivity, "Music Assistant playback failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
    onPlay: (SavedTag) -> Unit,
    onUpdateTag: (SavedTag) -> Unit,
    isWritingMode: Boolean,
    writeError: String?,
    chromecastDevices: List<ChromecastDevice>,
    selectedChromecastId: String?,
    onChromecastDeviceSelected: (String) -> Unit,
    onRefreshChromecast: () -> Unit,
    isChromecastRefreshing: Boolean,
    haServerUrl: String = "",
    onHaServerUrlChange: (String) -> Unit = {},
    haAccessToken: String = "",
    onHaAccessTokenChange: (String) -> Unit = {},
    maPlayers: List<MusicAssistantPlayer> = emptyList(),
    selectedMaPlayerId: String? = null,
    onMaPlayerSelected: (String) -> Unit = {},
    onRefreshMa: () -> Unit = {},
    maDefaultVolume: Int? = null,
    onMaDefaultVolumeChange: (Int?) -> Unit = {},
    isMaRefreshing: Boolean = false,
    maError: String? = null,
    isNfcTagLaunchBlocked: Boolean = false,
    onFixNfcTagLaunch: () -> Unit = {},
    selectedMoviePlayer: MoviePlayer,
    onMoviePlayerSelected: (MoviePlayer) -> Unit,
    onStartWriting: (TagWriteInfo) -> Unit,
    onCancelWriting: () -> Unit,
    onSearchAlbums: suspend (String) -> List<DiscogsSearchResult>,
    onSearchMovies: suspend (String) -> List<TmdbMovieResult>,
    onGetMovieStreaming: suspend (Int) -> MovieStreamingResult,
    selectedRegion: String,
    onRegionSelected: (String) -> Unit,
    onAlbumSelected: suspend (DiscogsSearchResult) -> String?,
    movieProviders: List<MovieProvider>,
    onMovieProvidersChanged: (List<MovieProvider>) -> Unit,
    onGetServicesForRegion: suspend (String) -> List<MovieProvider>,
    onDeleteTags: (Set<String>) -> Unit,
    hasShownWelcomeWizard: Boolean,
    onWelcomeWizardShown: () -> Unit,
    musicSortField: SortField,
    musicSortOrder: SortOrder,
    movieSortField: SortField,
    movieSortOrder: SortOrder,
    viewMode: ViewMode,
    onMusicSortChange: (SortField, SortOrder) -> Unit,
    onMovieSortChange: (SortField, SortOrder) -> Unit,
    onViewModeChange: (ViewMode) -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    nowPlaying: NowPlayingController? = null,
    openNowPlayingRequest: Int = 0,
    onRefreshMaPlayers: () -> Unit = {},
    showSuccessDialog: Boolean,
    onSuccessDismissed: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = onSuccessDismissed,
            title = { Text("Success") },
            text = { Text("Media link successfully written to tag!") },
            confirmButton = {
                TextButton(onClick = {
                    onSuccessDismissed()
                    navController.navigate("collection") {
                        popUpTo("collection") { inclusive = false }
                        launchSingleTop = true
                    }
                }) { Text("OK") }
            }
        )
    }

    LaunchedEffect(Unit) {
        if (!hasShownWelcomeWizard) {
            navController.navigate("welcome_wizard") {
                popUpTo("collection") { inclusive = false }
            }
        }
    }

    LaunchedEffect(openNowPlayingRequest) {
        if (openNowPlayingRequest > 0) {
            navController.navigate("now_playing") { launchSingleTop = true }
        }
    }

    var selectedTagIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val showChrome = currentRoute == "collection" || currentRoute == "settings"
    var chromeVisible by remember { mutableStateOf(showChrome) }
    var lastChromeRoute by remember { mutableStateOf("collection") }

    LaunchedEffect(showChrome) {
        if (showChrome) {
            chromeVisible = true
            lastChromeRoute = currentRoute ?: "collection"
        } else {
            // Delay disappearance slightly to ensure the next screen has rendered
            // its first frame, avoiding a flicker of the content behind the bars.
            delay(50)
            chromeVisible = false
        }
    }

    // Clear selection when navigating away from collection
    LaunchedEffect(currentRoute) {
        if (showChrome) {
            lastChromeRoute = currentRoute ?: "collection"
        }
        if (currentRoute != "collection") {
            selectedTagIds = emptySet()
        }
    }

    Scaffold(
        topBar = {
            if (chromeVisible) {
                TopAppBar(
                    title = {
                        if (lastChromeRoute == "collection") {
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
                        } else if (currentRoute == "settings") {
                            IconButton(onClick = { navController.navigate("welcome_wizard") }) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = "Help",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            if (chromeVisible) {
                TangibleBottomBar(
                    currentRoute = lastChromeRoute,
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
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable("welcome_wizard") {
                WelcomeWizardScreen(
                    onDismiss = {
                        onWelcomeWizardShown()
                        navController.popBackStack()
                    }
                )
            }
            composable("collection") {
                val miniStatus = nowPlaying?.status?.takeIf { it.isActive }
                Box(modifier = Modifier.fillMaxSize()) {
                MainScreen(
                    tags = savedTags,
                    selectedTagIds = selectedTagIds,
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
                    },
                    modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
                    musicSortField = musicSortField,
                    musicSortOrder = musicSortOrder,
                    movieSortField = movieSortField,
                    movieSortOrder = movieSortOrder,
                    viewMode = viewMode,
                    onMusicSortChange = onMusicSortChange,
                    onMovieSortChange = onMovieSortChange,
                    onViewModeChange = onViewModeChange,
                    onUpdateTag = onUpdateTag,
                    onDeleteTag = { onDeleteTags(setOf(it)) },
                    chromecastDevices = chromecastDevices,
                    movieProviders = movieProviders,
                    isNfcTagLaunchBlocked = isNfcTagLaunchBlocked,
                    onFixNfcTagLaunch = onFixNfcTagLaunch,
                    bottomContentPadding = if (miniStatus != null) 184.dp else 112.dp
                )
                if (miniStatus != null && nowPlaying != null) {
                    MiniPlayer(
                        status = miniStatus,
                        onClick = { navController.navigate("now_playing") { launchSingleTop = true } },
                        onPlayPause = { nowPlaying.playPause() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp)
                            .padding(bottom = innerPadding.calculateBottomPadding() + 4.dp)
                    )
                }
                }
            }
            composable("now_playing") {
                if (nowPlaying != null) {
                    NowPlayingScreen(
                        playerId = nowPlaying.playerId,
                        status = nowPlaying.status,
                        players = maPlayers,
                        onClose = { navController.popBackStack() },
                        onPlayPause = { nowPlaying.playPause() },
                        onPrevious = { nowPlaying.previous() },
                        onNext = { nowPlaying.next() },
                        onSeek = { nowPlaying.seek(it) },
                        onVolumeChange = { nowPlaying.setVolume(it) },
                        onSwitchPlayer = { nowPlaying.switchTo(it) },
                        onRefreshPlayers = onRefreshMaPlayers,
                        isStarting = nowPlaying.isStarting,
                        isTransferring = nowPlaying.isTransferring,
                        error = nowPlaying.error
                    )
                }
            }
            composable("write_tag") {
                WriteTagScreen(
                    onBackClick = {
                        onCancelWriting()
                        navController.popBackStack()
                    },
                    isWriting = isWritingMode,
                    writeError = writeError,
                    onTagInfoUpdate = { info ->
                        if (info != null) {
                            onStartWriting(info)
                        } else {
                            onCancelWriting()
                        }
                    },
                    onWriteClick = { info -> onStartWriting(info) },
                    onSearchAlbums = onSearchAlbums,
                    onSearchMovies = onSearchMovies,
                    onGetMovieStreaming = onGetMovieStreaming,
                    region = selectedRegion,
                    onAlbumSelected = onAlbumSelected
                )
            }
            composable("settings") {
                SettingsScreen(
                    selectedMoviePlayer = selectedMoviePlayer,
                    selectedRegion = selectedRegion,
                    onMusicAssistantClick = { navController.navigate("music_assistant") },
                    onMoviePlayerClick = { navController.navigate("movie_player") },
                    onMovieRegionClick = { navController.navigate("movie_region") },
                    onMovieProvidersClick = { navController.navigate("movie_providers") },
                    onExportClick = onExportClick,
                    onImportClick = onImportClick,
                    modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
                    isMusicAssistantConfigured = haServerUrl.isNotBlank() && haAccessToken.isNotBlank(),
                    chromecastDevices = chromecastDevices,
                    selectedChromecastId = selectedChromecastId,
                    maPlayers = maPlayers,
                    selectedMaPlayerId = selectedMaPlayerId
                )
            }
            composable("music_assistant") {
                MusicAssistantScreen(
                    onBackClick = { navController.popBackStack() },
                    serverUrl = haServerUrl,
                    onServerUrlChange = onHaServerUrlChange,
                    accessToken = haAccessToken,
                    onAccessTokenChange = onHaAccessTokenChange,
                    players = maPlayers,
                    selectedPlayerId = selectedMaPlayerId,
                    onPlayerSelected = onMaPlayerSelected,
                    onRefresh = onRefreshMa,
                    isRefreshing = isMaRefreshing,
                    error = maError,
                    defaultVolume = maDefaultVolume,
                    onDefaultVolumeChange = onMaDefaultVolumeChange
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
        // Left gradient bar
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(96.dp)
                .align(Alignment.BottomStart)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, TangibleBackground),
                        endY = 120f
                    )
                )
        )

        // Right gradient bar
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(96.dp)
                .align(Alignment.BottomEnd)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, TangibleBackground),
                        endY = 120f
                    )
                )
        )

        // Solid background for the bottom part of the bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter)
                .background(TangibleBackground)
        )

        // Navigation items
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter)
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
