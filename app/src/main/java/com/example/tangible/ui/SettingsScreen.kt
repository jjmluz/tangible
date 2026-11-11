package com.example.tangible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.example.tangible.data.MusicAssistantPlayer
import com.example.tangible.data.TvDevice

// ─── Main Settings Page ──────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    onHomeAssistantClick: () -> Unit,
    onMusicAssistantClick: () -> Unit,
    onMovieTvClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
    haServerUrl: String = "",
    isHomeAssistantConfigured: Boolean = false,
    maPlayers: List<MusicAssistantPlayer> = emptyList(),
    selectedMaPlayerId: String? = null,
    tvs: List<TvDevice> = emptyList(),
    selectedTvId: String? = null,
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

// ─── Movie TV Sub-page ────────────────────────────────────────────────────────

@Composable
fun MovieTvScreen(
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

    SettingsSubPage(title = "Movies", onBackClick = onBackClick) {
        Text(
            text = "Movie tags open on this TV, in Netflix, Disney+, HBO Max, Prime Video or Stremio. " +
                "The TV needs the Android TV Remote integration in Home Assistant.",
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
        Spacer(Modifier.height(16.dp))
    }
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
