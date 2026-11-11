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
import com.example.tangible.data.AlbumSearchResult
import com.example.tangible.data.HomeAssistantRepository
import com.example.tangible.data.MovieOption
import com.example.tangible.data.MovieSearchResult
import com.example.tangible.data.PosterStore
import com.example.tangible.data.MovieTag
import com.example.tangible.data.MusicTag
import com.example.tangible.data.MusicAssistantPlayer
import com.example.tangible.data.StreamingAvailabilityRepository
import com.example.tangible.data.TagRepository
import com.example.tangible.data.TvDevice
import com.example.tangible.ui.MainScreen
import com.example.tangible.ui.MiniPlayer
import com.example.tangible.ui.NowPlayingScreen
import com.example.tangible.ui.SortField
import com.example.tangible.ui.SortOrder
import com.example.tangible.ui.ViewMode
import com.example.tangible.ui.HomeAssistantScreen
import com.example.tangible.ui.MovieTvScreen
import com.example.tangible.ui.MusicAssistantScreen
import com.example.tangible.ui.SettingsScreen
import com.example.tangible.ui.TagWriteInfo
import com.example.tangible.ui.WelcomeWizardScreen
import com.example.tangible.ui.WriteTagScreen
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** The Google TV app and the action its Quick Settings "TV remote" tile uses to open the remote. */
private const val GOOGLE_TV_PACKAGE = "com.google.android.videos"
private const val GOOGLE_TV_REMOTE_ACTION = "com.google.android.apps.googletv.ACTION_VIRTUAL_REMOTE"

data class SavedTag(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: String, // "MUSIC" or "MOVIE"
    val url: String,
    val imageUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {
    companion object {
        /** Sent by the media controls: opens the Now Playing screen. */
        const val ACTION_OPEN_NOW_PLAYING = "com.example.tangible.OPEN_NOW_PLAYING"
    }

    private var nfcAdapter: NfcAdapter? = null
    private var isWritingMode by mutableStateOf(false)
    private var isNfcTagLaunchBlocked by mutableStateOf(false)
    private var writeError by mutableStateOf<String?>(null)
    private var showSuccessDialog by mutableStateOf(false)
    private var dataToWrite: String? = null
    private var pendingTagInfo: TagWriteInfo? = null
    private var savedTags by mutableStateOf<List<SavedTag>>(emptyList())
    /** The movie posters kept on the phone, by tag ID. */
    private var posterFiles by mutableStateOf<Map<String, File>>(emptyMap())

    // Settings
    private val prefs by lazy { getSharedPreferences("tangible_prefs", MODE_PRIVATE) }
    private val tagRepository by lazy { TagRepository(prefs) }
    private val gson = Gson()
    private var haServerUrl by mutableStateOf("")
    private var haAccessToken by mutableStateOf("")
    private var selectedMaPlayerId by mutableStateOf<String?>(null)
    /** 0–100, applied right before an album starts; null leaves the volume alone. */
    private var maDefaultVolume by mutableStateOf<Int?>(null)
    private var maPlayers by mutableStateOf<List<MusicAssistantPlayer>>(emptyList())
    private var isMaRefreshing by mutableStateOf(false)
    private var maError by mutableStateOf<String?>(null)
    /** The TV movies open on, from Home Assistant's Android TV Remote integration. */
    private var selectedTvId by mutableStateOf<String?>(null)
    private var tvs by mutableStateOf<List<TvDevice>>(emptyList())
    private var isTvRefreshing by mutableStateOf(false)
    private var tvError by mutableStateOf<String?>(null)
    private var hasShownWelcomeWizard by mutableStateOf(false)

    // Sorting
    private var musicSortField by mutableStateOf(SortField.TITLE)
    private var musicSortOrder by mutableStateOf(SortOrder.ASCENDING)
    private var movieSortField by mutableStateOf(SortField.TITLE)
    private var movieSortOrder by mutableStateOf(SortOrder.ASCENDING)
    private var collectionViewMode by mutableStateOf(ViewMode.LIST)

    // Repositories
    private val streamingApiKey = BuildConfig.STREAMING_AVAILABILITY_API_KEY
    private val streamingRepository by lazy { StreamingAvailabilityRepository(streamingApiKey) }
    private val posterStore by lazy {
        PosterStore(File(filesDir, "posters"), prefs, lookUpPosterUrl = { streamingRepository.posterUrl(it) })
    }
    private val homeAssistantRepository by lazy { HomeAssistantRepository() }
    private val nowPlaying by lazy { NowPlaying.controller(this) }
    private val scanDebouncer = ScanDebouncer()
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
                        syncPosters()
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
        haServerUrl = prefs.getString("ha_server_url", "") ?: ""
        haAccessToken = prefs.getString("ha_access_token", "") ?: ""
        selectedMaPlayerId = prefs.getString("selected_ma_player_id", null)
        maDefaultVolume = prefs.getInt("ma_default_volume", -1).takeIf { it in 0..100 }
        selectedTvId = prefs.getString("selected_tv_id", null)
        hasShownWelcomeWizard = prefs.getBoolean("has_shown_welcome_wizard", false)

        // Load sort settings
        musicSortField = SortField.valueOf(prefs.getString("music_sort_field", SortField.TITLE.name) ?: SortField.TITLE.name)
        musicSortOrder = SortOrder.valueOf(prefs.getString("music_sort_order", SortOrder.ASCENDING.name) ?: SortOrder.ASCENDING.name)
        movieSortField = SortField.valueOf(prefs.getString("movie_sort_field", SortField.TITLE.name) ?: SortField.TITLE.name)
        movieSortOrder = SortOrder.valueOf(prefs.getString("movie_sort_order", SortOrder.ASCENDING.name) ?: SortOrder.ASCENDING.name)
        collectionViewMode = ViewMode.valueOf(prefs.getString("collection_view_mode", ViewMode.LIST.name) ?: ViewMode.LIST.name)

        loadMaPlayers()
        loadTvs()
        nowPlaying.setPlayer(prefs.getString("now_playing_player_id", null) ?: selectedMaPlayerId)
        savedTags = tagRepository.loadAll()
        syncPosters()

        // Only a fresh launch: a recreated activity (rotation, theme change) still has the intent
        // of the last scan, and handling it again would restart the album.
        if (savedInstanceState == null) intent?.let { handleIntent(it) }

        setContent {
            TangibleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TangibleApp(
                        // Movies show the poster kept on the phone; the tag's own link expires.
                        savedTags = savedTags.map { tag ->
                            posterFiles[tag.id]?.let { tag.copy(imageUrl = Uri.fromFile(it).toString()) } ?: tag
                        },
                        onPlay = { tag -> playTagUrl(tag) },
                        isWritingMode = isWritingMode,
                        writeError = writeError,
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
                        tvs = tvs,
                        selectedTvId = selectedTvId,
                        onTvSelected = { entityId ->
                            selectedTvId = entityId
                            prefs.edit().putString("selected_tv_id", entityId).apply()
                        },
                        onRefreshTvs = { refreshTvs() },
                        isTvRefreshing = isTvRefreshing,
                        tvError = tvError,
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
                        onSearchAlbums = { query -> homeAssistantRepository.searchAlbums(haServerUrl, haAccessToken, query) },
                        onSearchMovies = { query -> streamingRepository.searchMovies(query) },
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

    private fun loadTvs() {
        val json = prefs.getString("cached_tvs", null) ?: return
        val type = object : TypeToken<List<TvDevice>>() {}.type
        tvs = runCatching { gson.fromJson<List<TvDevice>>(json, type) }.getOrNull() ?: emptyList()
    }

    private fun refreshTvs() {
        if (isTvRefreshing) return
        if (haServerUrl.isBlank() || haAccessToken.isBlank()) {
            tvError = "Enter your Home Assistant URL and access token first."
            return
        }
        isTvRefreshing = true
        tvError = null
        lifecycleScope.launch {
            homeAssistantRepository.fetchTvs(haServerUrl, haAccessToken)
                .onSuccess { found ->
                    tvs = found
                    prefs.edit().putString("cached_tvs", gson.toJson(found)).apply()
                    if (found.isEmpty()) {
                        tvError = "No TVs found. Is the Android TV Remote integration set up in Home Assistant?"
                    }
                }
                .onFailure { e -> tvError = e.message ?: e.javaClass.simpleName }
            isTvRefreshing = false
        }
    }

    override fun onStart() {
        super.onStart()
        NowPlaying.setPolling(this, this, true)
    }

    override fun onStop() {
        super.onStop()
        NowPlaying.setPolling(this, this, false)
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

        // A tap on the media controls.
        if (action == ACTION_OPEN_NOW_PLAYING) {
            openNowPlayingRequest++
            return
        }

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

        var albumUri: String? = null
        var movie: MovieOption? = null

        fun parseTangibleUri(uri: Uri) {
            albumUri = MusicTag.parse(uri)
            if (albumUri == null) movie = MovieTag.parse(uri)
        }

        data?.let { parseTangibleUri(it) }

        if (albumUri == null && movie == null) {
            val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            rawMsgs?.forEach {
                val msg = it as NdefMessage
                msg.records.forEach { record ->
                    try { record.toUri()?.let { uri -> parseTangibleUri(uri) } } catch (e: Exception) { }
                }
            }
        }

        // A tap in the collection is deliberate; a scan can be the same tag read twice.
        val scanKey = if (customTag == null && !isWritingMode) albumUri ?: movie?.tvLink else null
        if (scanKey != null && scanDebouncer.isRepeat(scanKey)) {
            Log.d("Tangible", "Ignoring a repeated read of the same tag")
            return
        }

        val movieOption = movie
        if (movieOption != null && !isWritingMode) {
            playOnTv(movieOption, savedTag?.title)
            return
        }

        val album = albumUri
        if (album != null && !isWritingMode) {
            // Every music tag plays on the default player, from a scan or a tap in the collection.
            val preview = savedTag?.let { AlbumPreview(it.title, it.subtitle.ifBlank { null }, it.imageUrl) }
            playOnMusicAssistant(album, selectedMaPlayerId, preview)
            return
        }

        Log.d("Tangible", "No media found or in writing mode. isWritingMode=$isWritingMode")
        if (!isWritingMode && data?.scheme == "tangible") {
            Toast.makeText(this, "This tag is in an old format. Write it again.", Toast.LENGTH_LONG).show()
        }
    }

    /** Opens the movie on the TV picked in Settings, in the streaming app the tag was written for. */
    private fun playOnTv(movie: MovieOption, title: String?) {
        if (haServerUrl.isBlank() || haAccessToken.isBlank()) {
            Toast.makeText(this, "Set up Home Assistant in Settings first", Toast.LENGTH_LONG).show()
            return
        }
        val tvId = selectedTvId
        if (tvId == null) {
            Toast.makeText(this, "Pick a TV in Settings", Toast.LENGTH_LONG).show()
            return
        }
        val tvName = tvs.find { it.entityId == tvId }?.name ?: "the TV"
        val what = title?.takeIf { it.isNotBlank() } ?: "the movie"
        Toast.makeText(this, "Opening $what in ${movie.service.displayName} on $tvName…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            homeAssistantRepository.openOnTv(haServerUrl, haAccessToken, tvId, movie.tvLink)
                .onFailure { e ->
                    Toast.makeText(this@MainActivity, "Couldn't open the movie on $tvName: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
        openTvRemote()
    }

    /**
     * Brings up the Google TV app's remote on the phone, ready for when the movie opens. The app
     * shows the remote of the TV it was last connected to. Does nothing if the app isn't installed.
     */
    private fun openTvRemote() {
        try {
            startActivity(Intent(GOOGLE_TV_REMOTE_ACTION).setPackage(GOOGLE_TV_PACKAGE))
        } catch (e: Exception) {
            Log.w("Tangible", "Couldn't open the Google TV remote: ${e.message}")
        }
    }

    private fun playOnMusicAssistant(albumUri: String, playerId: String?, preview: AlbumPreview? = null) {
        if (haServerUrl.isBlank() || haAccessToken.isBlank()) {
            Toast.makeText(this, "Set up Home Assistant in Settings first", Toast.LENGTH_LONG).show()
            return
        }
        if (playerId == null) {
            Toast.makeText(this, "Pick a Music Assistant player in Settings", Toast.LENGTH_LONG).show()
            return
        }
        nowPlaying.playbackStarting(playerId, preview, albumUri)
        openNowPlayingRequest++
        val volume = maDefaultVolume
        lifecycleScope.launch {
            // Set the volume first so the album doesn't start loud. A failure here shouldn't stop playback.
            if (volume != null) {
                homeAssistantRepository.setVolume(haServerUrl, haAccessToken, playerId, volume / 100f)
                    .onFailure { Log.w("Tangible", "Couldn't set the default volume: ${it.message}") }
            }
            val result = homeAssistantRepository.playAlbum(haServerUrl, haAccessToken, playerId, albumUri)
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
            syncPosters()
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
        syncPosters()
    }

    /**
     * Fetches the movie posters that aren't on the phone yet (a new tag, a restored backup, cleared
     * storage) and drops those of deleted tags. Until a poster arrives, the tag's own link is shown.
     */
    private fun syncPosters() {
        posterFiles = posterStore.stored(savedTags)
        lifecycleScope.launch {
            posterStore.sync(savedTags)
            posterFiles = posterStore.stored(savedTags)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TangibleApp(
    savedTags: List<SavedTag>,
    onPlay: (SavedTag) -> Unit,
    isWritingMode: Boolean,
    writeError: String?,
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
    tvs: List<TvDevice> = emptyList(),
    selectedTvId: String? = null,
    onTvSelected: (String) -> Unit = {},
    onRefreshTvs: () -> Unit = {},
    isTvRefreshing: Boolean = false,
    tvError: String? = null,
    onStartWriting: (TagWriteInfo) -> Unit,
    onCancelWriting: () -> Unit,
    onSearchAlbums: suspend (String) -> Result<List<AlbumSearchResult>>,
    onSearchMovies: suspend (String) -> List<MovieSearchResult>,
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
    val isHaConfigured = haServerUrl.isNotBlank() && haAccessToken.isNotBlank()

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
                    onSearchMovies = onSearchMovies
                )
            }
            composable("settings") {
                SettingsScreen(
                    onHomeAssistantClick = { navController.navigate("home_assistant") },
                    onMusicAssistantClick = { navController.navigate("music_assistant") },
                    onMovieTvClick = { navController.navigate("movie_tv") },
                    onExportClick = onExportClick,
                    onImportClick = onImportClick,
                    modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
                    haServerUrl = haServerUrl,
                    isHomeAssistantConfigured = isHaConfigured,
                    maPlayers = maPlayers,
                    selectedMaPlayerId = selectedMaPlayerId,
                    tvs = tvs,
                    selectedTvId = selectedTvId
                )
            }
            composable("home_assistant") {
                HomeAssistantScreen(
                    onBackClick = { navController.popBackStack() },
                    serverUrl = haServerUrl,
                    onServerUrlChange = onHaServerUrlChange,
                    accessToken = haAccessToken,
                    onAccessTokenChange = onHaAccessTokenChange
                )
            }
            composable("music_assistant") {
                MusicAssistantScreen(
                    onBackClick = { navController.popBackStack() },
                    isConfigured = isHaConfigured,
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
            composable("movie_tv") {
                MovieTvScreen(
                    onBackClick = { navController.popBackStack() },
                    isConfigured = isHaConfigured,
                    tvs = tvs,
                    selectedTvId = selectedTvId,
                    onTvSelected = onTvSelected,
                    onRefresh = onRefreshTvs,
                    isRefreshing = isTvRefreshing,
                    error = tvError
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
