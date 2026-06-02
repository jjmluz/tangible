package com.example.tangible.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.tangible.SONOS_ALL_SPEAKERS
import com.example.tangible.data.ChromecastDevice
import com.example.tangible.data.SonosSpeaker

enum class MusicService(val displayName: String) {
    SPOTIFY("Spotify"),
    YOUTUBE_MUSIC("YouTube Music"),
    APPLE_MUSIC("Apple Music")
}

enum class MusicSpeaker(val displayName: String) {
    THIS_DEVICE("This device"),
    CHROMECAST("Google TV"),
    SONOS("Sonos")
}

enum class MoviePlayer(val displayName: String) {
    THIS_DEVICE("This device"),
    GOOGLE_TV("Google TV")
}

val REGIONS = listOf(
    "AU" to "Australia",
    "AT" to "Austria",
    "BE" to "Belgium",
    "BR" to "Brazil",
    "CA" to "Canada",
    "DK" to "Denmark",
    "FI" to "Finland",
    "FR" to "France",
    "DE" to "Germany",
    "IN" to "India",
    "IE" to "Ireland",
    "IT" to "Italy",
    "JP" to "Japan",
    "MX" to "Mexico",
    "NL" to "Netherlands",
    "NZ" to "New Zealand",
    "NO" to "Norway",
    "PL" to "Poland",
    "PT" to "Portugal",
    "ES" to "Spain",
    "SE" to "Sweden",
    "CH" to "Switzerland",
    "TR" to "Turkey",
    "GB" to "United Kingdom",
    "US" to "United States"
)

// ─── Main Settings Page ──────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    selectedService: MusicService,
    selectedSpeaker: MusicSpeaker,
    selectedMoviePlayer: MoviePlayer,
    selectedRegion: String,
    onMusicServiceClick: () -> Unit,
    onMusicSpeakerClick: () -> Unit,
    onMoviePlayerClick: () -> Unit,
    onMovieRegionClick: () -> Unit,
    onMovieProvidersClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val regionName = REGIONS.find { it.first == selectedRegion }?.second ?: selectedRegion

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        SectionHeader("Music")
        Spacer(Modifier.height(8.dp))
        SettingsItem("Streaming service", selectedService.displayName, onMusicServiceClick)
        Spacer(Modifier.height(8.dp))
        SettingsItem("Default output", selectedSpeaker.displayName, onMusicSpeakerClick)

        Spacer(Modifier.height(24.dp))

        SectionHeader("Movies")
        Spacer(Modifier.height(8.dp))
        SettingsItem("Streaming services", "Manage enabled services", onMovieProvidersClick)
        Spacer(Modifier.height(8.dp))
        SettingsItem("Default output", selectedMoviePlayer.displayName, onMoviePlayerClick)
        Spacer(Modifier.height(8.dp))
        SettingsItem("Region", regionName, onMovieRegionClick)

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
        color = TangiblePurple,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsItem(title: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TangibleSurface
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

// ─── Default streaming service Sub-page ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicServiceScreen(
    onBackClick: () -> Unit,
    selectedService: MusicService,
    onServiceSelected: (MusicService) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Streaming service (Music)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TangibleBackground)
            )
        },
        containerColor = TangibleBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .selectableGroup()
        ) {
            MusicService.entries.forEach { service ->
                SelectionRow(
                    label = service.displayName,
                    selected = service == selectedService,
                    onClick = { onServiceSelected(service) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
        }
    }
}

// ─── Default speaker Sub-page ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSpeakerScreen(
    onBackClick: () -> Unit,
    selectedService: MusicService,
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
) {
    LaunchedEffect(selectedSpeaker) {
        if (selectedSpeaker == MusicSpeaker.CHROMECAST && chromecastDevices.isEmpty() && !isChromecastRefreshing) {
            onRefreshChromecast()
        }
        if (selectedSpeaker == MusicSpeaker.SONOS && sonosSpeakers.isEmpty() && !isSonosRefreshing) {
            onRefreshSonos()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Default output (Music)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TangibleBackground)
            )
        },
        containerColor = TangibleBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(Modifier.selectableGroup()) {
                MusicSpeaker.entries.forEach { speaker ->
                    val isDisabled = when (speaker) {
                        MusicSpeaker.SONOS -> selectedService == MusicService.YOUTUBE_MUSIC ||
                                selectedService == MusicService.APPLE_MUSIC
                        MusicSpeaker.CHROMECAST -> selectedService == MusicService.APPLE_MUSIC
                        else -> false
                    }
                    SelectionRow(
                        label = speaker.displayName,
                        selected = speaker == selectedSpeaker,
                        enabled = !isDisabled,
                        sublabel = if (isDisabled) "Not supported with ${selectedService.displayName}" else null,
                        onClick = { onSpeakerSelected(speaker) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }
            }

            if (selectedSpeaker == MusicSpeaker.CHROMECAST) {
                Spacer(Modifier.height(24.dp))
                DeviceSection(
                    title = "Google TV Device",
                    emptyText = "No Google TV devices found. Enable ADB debugging in Developer Options on your TV, then tap refresh.",
                    devices = chromecastDevices.map { it.id to it.name },
                    subtitles = chromecastDevices.associate { it.id to it.model },
                    selectedId = selectedChromecastId,
                    onSelect = onChromecastDeviceSelected,
                    onRefresh = onRefreshChromecast,
                    isRefreshing = isChromecastRefreshing
                )
                if (selectedService == MusicService.SPOTIFY) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Spotify opens directly on your Google TV via ADB. Enable \"ADB debugging\" in Developer Options on the TV — on first use, approve the prompt on screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            if (selectedSpeaker == MusicSpeaker.SONOS) {
                Spacer(Modifier.height(24.dp))
                val sonosDevices = buildList {
                    add(SONOS_ALL_SPEAKERS to "All speakers")
                    sonosSpeakers.forEach { add(it.ip to it.name) }
                }
                DeviceSection(
                    title = "Sonos Speaker",
                    emptyText = "No Sonos speakers found on this network.",
                    devices = sonosDevices,
                    subtitles = sonosSpeakers.associate { it.ip to it.model },
                    selectedId = selectedSonosIp,
                    onSelect = onSonosSpeakerSelected,
                    onRefresh = onRefreshSonos,
                    isRefreshing = isSonosRefreshing
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Default player Sub-page ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviePlayerScreen(
    onBackClick: () -> Unit,
    selectedMoviePlayer: MoviePlayer,
    onMoviePlayerSelected: (MoviePlayer) -> Unit,
    chromecastDevices: List<ChromecastDevice>,
    selectedChromecastId: String?,
    onChromecastDeviceSelected: (String) -> Unit,
    onRefreshChromecast: () -> Unit,
    isChromecastRefreshing: Boolean,
) {
    LaunchedEffect(selectedMoviePlayer) {
        if (selectedMoviePlayer == MoviePlayer.GOOGLE_TV && chromecastDevices.isEmpty() && !isChromecastRefreshing) {
            onRefreshChromecast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Default output (Movies)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TangibleBackground)
            )
        },
        containerColor = TangibleBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(Modifier.selectableGroup()) {
                MoviePlayer.entries.forEach { player ->
                    SelectionRow(
                        label = player.displayName,
                        selected = player == selectedMoviePlayer,
                        onClick = { onMoviePlayerSelected(player) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }
            }

            if (selectedMoviePlayer == MoviePlayer.GOOGLE_TV) {
                Spacer(Modifier.height(24.dp))
                DeviceSection(
                    title = "Google TV Device",
                    emptyText = "No Google TV devices found. Enable ADB debugging in Developer Options on your TV, then tap refresh.",
                    devices = chromecastDevices.map { it.id to it.name },
                    subtitles = chromecastDevices.associate { it.id to it.model },
                    selectedId = selectedChromecastId,
                    onSelect = onChromecastDeviceSelected,
                    onRefresh = onRefreshChromecast,
                    isRefreshing = isChromecastRefreshing
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Movies open directly on your Google TV via ADB. Enable \"ADB debugging\" in Developer Options on the TV — on first use, approve the prompt on screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Movie Region Sub-page ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieRegionScreen(
    onBackClick: () -> Unit,
    selectedRegion: String,
    onRegionSelected: (String) -> Unit,
) {
    var pendingRegion by remember { mutableStateOf<String?>(null) }

    if (pendingRegion != null) {
        AlertDialog(
            onDismissRequest = { pendingRegion = null },
            title = { Text("Change region?") },
            text = { Text("Changing the region will clear your custom movie provider preferences.") },
            confirmButton = {
                TextButton(onClick = {
                    onRegionSelected(pendingRegion!!)
                    pendingRegion = null
                }) { Text("Change") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRegion = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Region (Movies)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TangibleBackground)
            )
        },
        containerColor = TangibleBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .selectableGroup()
        ) {
            REGIONS.forEach { (code, name) ->
                SelectionRow(
                    label = name,
                    selected = code == selectedRegion,
                    onClick = { if (code != selectedRegion) pendingRegion = code }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
        }
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
            color = TangiblePurple
        )
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = TangiblePurple
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
