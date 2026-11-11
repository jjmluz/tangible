package com.jjmluz.tangible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import java.util.Locale
import com.jjmluz.tangible.data.MovieService
import com.jjmluz.tangible.data.MusicAssistantPlayer
import com.jjmluz.tangible.data.Region
import com.jjmluz.tangible.data.StreamingAvailabilityRepository
import kotlinx.coroutines.delay
import com.jjmluz.tangible.data.TvDevice

// ─── Main Settings Page ──────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    onHomeAssistantClick: () -> Unit,
    onMusicAssistantClick: () -> Unit,
    onMovieTvClick: () -> Unit,
    onStreamingServicesClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
    haServerUrl: String = "",
    isHomeAssistantConfigured: Boolean = false,
    maPlayers: List<MusicAssistantPlayer> = emptyList(),
    selectedMaPlayerId: String? = null,
    tvs: List<TvDevice> = emptyList(),
    selectedTvId: String? = null,
    hasStreamingApiKey: Boolean = true,
    regions: List<Region> = emptyList(),
    movieRegion: String = "",
    movieServices: Set<MovieService> = MovieService.entries.toSet(),
) {
    val homeAssistantSubtitle = if (isHomeAssistantConfigured) haServerUrl else "Not set up"

    val musicAssistantSubtitle = when {
        selectedMaPlayerId == null -> "No player selected"
        else -> maPlayers.find { it.entityId == selectedMaPlayerId }?.name ?: selectedMaPlayerId
    }

    val tvSubtitle = when {
        selectedTvId == null -> "No TV selected"
        else -> tvs.find { it.entityId == selectedTvId }?.name ?: selectedTvId
    }

    val streamingSubtitle = if (!hasStreamingApiKey) {
        "Add a Streaming Availability API key"
    } else {
        val region = regions.find { it.code == movieRegion }
        val count = MovieService.availableIn(region).count { it in movieServices }
        val regionName = region?.name ?: countryName(movieRegion)
        "$regionName · " + if (count == 1) "1 service" else "$count services"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 48.dp)
    ) {
        SectionHeader("Home Assistant")
        Spacer(Modifier.height(8.dp))
        SettingsItem("Server", homeAssistantSubtitle, onHomeAssistantClick)

        Spacer(Modifier.height(24.dp))

        SectionHeader("Music")
        Spacer(Modifier.height(8.dp))
        SettingsItem("Music player", musicAssistantSubtitle, onMusicAssistantClick)

        Spacer(Modifier.height(24.dp))

        SectionHeader("Movies")
        Spacer(Modifier.height(8.dp))
        SettingsItem("Streaming services", streamingSubtitle, onStreamingServicesClick)
        Spacer(Modifier.height(8.dp))
        SettingsItem("Movie player", tvSubtitle, onMovieTvClick)

        Spacer(Modifier.height(24.dp))

        SectionHeader("Backup")
        Spacer(Modifier.height(8.dp))
        SettingsItem("Export collection", "Save your tags to a JSON file", onExportClick)
        Spacer(Modifier.height(8.dp))
        SettingsItem("Import collection", "Restore tags from a JSON file", onImportClick)

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsItem(title: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Home Assistant Sub-page ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAssistantScreen(
    onBackClick: () -> Unit,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    accessToken: String,
    onAccessTokenChange: (String) -> Unit,
) {
    SettingsSubPage(title = "Home Assistant", onBackClick = onBackClick) {
        Text(
            text = "Tangible plays everything through Home Assistant: music with Music Assistant, " +
                "movies with the Android TV Remote integration.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("Server URL") },
            placeholder = { Text("http://homeassistant.local:8123") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = accessToken,
            onValueChange = onAccessTokenChange,
            label = { Text("Long-lived access token") },
            supportingText = { Text("Create one in Home Assistant under Profile → Security") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** A settings page with a back arrow and scrolling content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubPage(
    title: String,
    onBackClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            content = content
        )
    }
}

// ─── Music Assistant Sub-page ─────────────────────────────────────────────────

@Composable
fun MusicAssistantScreen(
    onBackClick: () -> Unit,
    isConfigured: Boolean,
    players: List<MusicAssistantPlayer>,
    selectedPlayerId: String?,
    onPlayerSelected: (String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    error: String?,
    defaultVolume: Int? = null,
    onDefaultVolumeChange: (Int?) -> Unit = {},
) {
    LaunchedEffect(Unit) {
        if (isConfigured && players.isEmpty() && !isRefreshing) onRefresh()
    }

    SettingsSubPage(title = "Music", onBackClick = onBackClick) {
            Text(
                text = "Tags play albums through Music Assistant. " +
                    "To play on several speakers at once, create a player group in Music Assistant and pick it below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            // Above the player list, which can be long.
            Spacer(Modifier.height(24.dp))
            DefaultVolumeSection(volume = defaultVolume, onVolumeChange = onDefaultVolumeChange)
            Spacer(Modifier.height(16.dp))
            DeviceSection(
                title = "Player",
                emptyText = when {
                    error != null -> error
                    !isConfigured -> "Set up the Home Assistant connection in Settings first."
                    else -> "Tap refresh to load your Music Assistant players."
                },
                devices = players.map { it.entityId to it.name },
                subtitles = players.associate { it.entityId to it.entityId },
                selectedId = selectedPlayerId,
                onSelect = onPlayerSelected,
                onRefresh = onRefresh,
                isRefreshing = isRefreshing
            )
            if (error != null && players.isNotEmpty()) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
    }
}

/** Off (null) or a 0–100 volume that's applied right before an album starts. */
@Composable
internal fun DefaultVolumeSection(volume: Int?, onVolumeChange: (Int?) -> Unit) {
    // Remember the last level so switching off and on again doesn't lose it.
    var lastLevel by rememberSaveable { mutableIntStateOf(volume ?: DEFAULT_VOLUME_SUGGESTION) }
    var dragLevel by remember { mutableStateOf<Float?>(null) }
    Text(
        text = "PLAYBACK",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
    ListItem(
        headlineContent = { Text("Default volume") },
        supportingContent = {
            Text(
                if (volume == null) "Albums start at whatever volume the player is at"
                else "Albums start at $volume% when you scan a tag or tap play"
            )
        },
        trailingContent = {
            Switch(
                checked = volume != null,
                onCheckedChange = { on -> onVolumeChange(if (on) lastLevel else null) },
                modifier = Modifier.testTag("default_volume_switch")
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.padding(horizontal = 4.dp)
    )
    if (volume != null) {
        val shown = dragLevel?.roundToInt() ?: volume
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = dragLevel ?: volume.toFloat(),
                onValueChange = { dragLevel = it },
                onValueChangeFinished = {
                    dragLevel?.roundToInt()?.let {
                        lastLevel = it
                        onVolumeChange(it)
                    }
                    dragLevel = null
                },
                valueRange = 0f..100f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .testTag("default_volume_slider")
            )
            Text(
                text = "$shown%",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(44.dp)
            )
        }
    }
}

private const val DEFAULT_VOLUME_SUGGESTION = 30

// ─── Movie player Sub-page ────────────────────────────────────────────────────

@Composable
fun MoviePlayerScreen(
    onBackClick: () -> Unit,
    isConfigured: Boolean,
    tvs: List<TvDevice>,
    selectedTvId: String?,
    onTvSelected: (String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    error: String?,
) {
    LaunchedEffect(Unit) {
        if (isConfigured && tvs.isEmpty() && !isRefreshing) onRefresh()
    }

    SettingsSubPage(title = "Movie player", onBackClick = onBackClick) {
        Text(
            text = "Movie tags open on this TV, in the streaming app you pick when writing the tag.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Spacer(Modifier.height(16.dp))
        DeviceSection(
            title = "TV",
            emptyText = when {
                error != null -> error
                !isConfigured -> "Set up the Home Assistant connection in Settings first."
                else -> "Tap refresh to load your TVs."
            },
            devices = tvs.map { it.entityId to it.name },
            subtitles = tvs.associate { it.entityId to it.entityId },
            selectedId = selectedTvId,
            onSelect = onTvSelected,
            onRefresh = onRefresh,
            isRefreshing = isRefreshing
        )
        if (error != null && tvs.isNotEmpty()) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        Text(
            text = "The TV needs the Android TV Remote integration in Home Assistant.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Spacer(Modifier.height(16.dp))
    }
}

// ─── Streaming services Sub-page ──────────────────────────────────────────────

@Composable
fun StreamingServicesScreen(
    onBackClick: () -> Unit,
    apiKey: String = "",
    onApiKeyChange: (String) -> Unit = {},
    isApiKeyValid: Boolean = false,
    onCheckApiKey: () -> Unit = {},
    regions: List<Region> = emptyList(),
    isRegionsLoading: Boolean = false,
    regionsError: String? = null,
    region: String = "",
    onRegionChange: (String) -> Unit = {},
    services: Set<MovieService> = MovieService.entries.toSet(),
    onServicesChange: (Set<MovieService>) -> Unit = {},
) {
    // Checks the key once typing stops (and on opening the page, which is skipped when it's known to work).
    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank()) {
            delay(800)
            onCheckApiKey()
        }
    }
    val uriHandler = LocalUriHandler.current
    val currentRegion = regions.find { it.code == region }
    var showRegionPicker by remember { mutableStateOf(false) }

    if (showRegionPicker) {
        RegionPickerDialog(
            regions = regions,
            selected = region,
            onSelect = {
                onRegionChange(it)
                showRegionPicker = false
            },
            onDismiss = { showRegionPicker = false }
        )
    }

    SettingsSubPage(title = "Streaming services", onBackClick = onBackClick) {
        Spacer(Modifier.height(8.dp))
        PageSectionHeader("Movie search")
        Text(
            text = "Finding movies and where to watch them uses the Streaming Availability API, " +
                "with your own API key. The free plan is enough and needs no payment details: " +
                "sign up, then copy the key from the console.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        TextButton(
            onClick = { uriHandler.openUri(StreamingAvailabilityRepository.KEY_SIGN_UP_URL) },
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Get a free API key")
        }
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("Streaming Availability API key") },
            supportingText = {
                Text(
                    when {
                        apiKey.isBlank() -> "Needed to write movie tags. Tags you've written work without it."
                        isRegionsLoading -> "Checking the key…"
                        regionsError != null -> regionsError
                        isApiKeyValid -> "The key works"
                        else -> ""
                    },
                    color = if (regionsError != null && apiKey.isNotBlank()) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            isError = regionsError != null && apiKey.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("streaming_api_key"),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(Modifier.height(16.dp))
        PageSectionHeader("Region")
        ListItem(
            headlineContent = { Text(currentRegion?.name ?: countryName(region)) },
            supportingContent = {
                Text(
                    when {
                        regions.isEmpty() -> "Add your API key to choose a region"
                        currentRegion == null -> "Movie search doesn't cover this region. Pick another."
                        else -> "Where to look up what's on your services"
                    },
                    color = if (regions.isNotEmpty() && currentRegion == null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .clickable(enabled = regions.isNotEmpty()) { showRegionPicker = true }
                .testTag("movie_region")
        )

        Spacer(Modifier.height(16.dp))
        PageSectionHeader("Your services")
        ServicesSection(region = currentRegion, selected = services, onChange = onServicesChange)

        Spacer(Modifier.height(24.dp))
        // The API's terms ask for this, with a link, somewhere users can see it.
        Text(
            text = "Streaming availability information is provided by Streaming Availability API by Movie of the Night.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .clickable { uriHandler.openUri(StreamingAvailabilityRepository.ATTRIBUTION_URL) }
        )
        TextButton(
            onClick = { uriHandler.openUri(StreamingAvailabilityRepository.ATTRIBUTION_URL) },
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text("movieofthenight.com/about/api")
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * The services Tangible can open in the region, to tick the ones the user has, and a note on the
 * region's other services, which Tangible can't open yet.
 */
@Composable
private fun ServicesSection(
    region: Region?,
    selected: Set<MovieService>,
    onChange: (Set<MovieService>) -> Unit
) {
    val available = MovieService.availableIn(region)
    Column {
        available.forEach { service ->
            val checked = service in selected
            ListItem(
                headlineContent = { Text(service.displayName) },
                supportingContent = if (service == MovieService.STREMIO) {
                    { Text("Plays any movie with an IMDb ID, with your Stremio add-ons") }
                } else null,
                trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .toggleable(
                        value = checked,
                        role = Role.Checkbox,
                        onValueChange = { on -> onChange(if (on) selected + service else selected - service) }
                    )
                    .testTag("service_${service.key}")
            )
        }
        val others = region?.services.orEmpty()
            .filter { s -> MovieService.fromApiId(s.apiId) == null }
            .map { it.name }
        if (others.isNotEmpty()) {
            Text(
                text = "Tangible can't open these yet: ${others.joinToString(", ")}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun RegionPickerDialog(
    regions: List<Region>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Region") },
        text = {
            LazyColumn(Modifier.selectableGroup()) {
                items(regions, key = { it.code }) { region ->
                    SelectionRow(
                        label = region.name,
                        selected = region.code == selected,
                        onClick = { onSelect(region.code) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** The country's name in the phone's language, for before the API's region list is loaded. */
private fun countryName(code: String): String =
    runCatching { Locale.Builder().setRegion(code.uppercase()).build().displayCountry }
        .getOrNull()?.takeIf { it.isNotBlank() } ?: code.uppercase()

@Composable
private fun PageSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

// ─── Shared components ────────────────────────────────────────────────────────

@Composable
private fun SelectionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    sublabel: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (enabled) 1f else 0.38f
                    )
                )
            }
        }
    }
}

@Composable
private fun DeviceSection(
    title: String,
    emptyText: String,
    devices: List<Pair<String, String>>,
    subtitles: Map<String, String>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
    }
    if (devices.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    } else {
        Column(Modifier.selectableGroup()) {
            devices.forEach { (id, name) ->
                SelectionRow(
                    label = name,
                    sublabel = subtitles[id],
                    selected = id == selectedId,
                    onClick = { onSelect(id) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
        }
    }
}
