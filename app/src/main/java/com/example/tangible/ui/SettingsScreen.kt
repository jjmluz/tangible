package com.example.tangible.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

private val REGIONS = listOf(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onMovieProvidersClick: () -> Unit,
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
    selectedMoviePlayer: MoviePlayer,
    onMoviePlayerSelected: (MoviePlayer) -> Unit,
    selectedRegion: String,
    onRegionSelected: (String) -> Unit
) {
    var showRegionWarning by remember { mutableStateOf<String?>(null) }

    if (showRegionWarning != null) {
        AlertDialog(
            onDismissRequest = { showRegionWarning = null },
            title = { Text("Change region?") },
            text = { Text("Changing the region will clear your custom movie provider preferences.") },
            confirmButton = {
                TextButton(onClick = {
                    onRegionSelected(showRegionWarning!!)
                    showRegionWarning = null
                }) { Text("Change") }
            },
            dismissButton = {
                TextButton(onClick = { showRegionWarning = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
            Text(
                text = "Default music service",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Column(Modifier.selectableGroup()) {
                MusicService.entries.forEach { service ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (service == selectedService),
                                onClick = { onServiceSelected(service) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (service == selectedService), onClick = null)
                        Text(
                            text = service.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Default music speaker",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Column(Modifier.selectableGroup()) {
                MusicSpeaker.entries.forEach { speaker ->
                    val isDisabled = when (speaker) {
                        MusicSpeaker.SONOS -> selectedService == MusicService.YOUTUBE_MUSIC ||
                            selectedService == MusicService.APPLE_MUSIC
                        MusicSpeaker.CHROMECAST -> selectedService == MusicService.APPLE_MUSIC
                        else -> false
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (speaker == selectedSpeaker),
                                enabled = !isDisabled,
                                onClick = { onSpeakerSelected(speaker) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (speaker == selectedSpeaker), enabled = !isDisabled, onClick = null)
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(
                                text = speaker.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else Color.Unspecified
                            )
                            if (isDisabled) {
                                Text(
                                    text = "Not supported with ${selectedService.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }
                }
            }

            if (selectedSpeaker == MusicSpeaker.CHROMECAST) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Default Google TV device", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onRefreshChromecast) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }

                if (chromecastDevices.isEmpty()) {
                    Text(
                        text = "No Google TV devices found. Make sure ADB debugging is enabled in Developer Options on your TV, then tap refresh.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(Modifier.selectableGroup()) {
                        chromecastDevices.forEach { device ->
                            val isSelected = device.id == selectedChromecastId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .selectable(
                                        selected = isSelected,
                                        onClick = { onChromecastDeviceSelected(device.id) },
                                        role = Role.RadioButton
                                    )
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSelected, onClick = null)
                                Column(modifier = Modifier.padding(start = 16.dp)) {
                                    Text(text = device.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = device.model,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedService == MusicService.SPOTIFY) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Spotify opens directly on your Google TV via ADB. Enable \"ADB debugging\" in Developer Options on the TV — on first use, approve the \"Allow ADB debugging?\" prompt that appears on screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (selectedSpeaker == MusicSpeaker.SONOS) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Default Sonos speaker", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onRefreshSonos) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Sonos")
                    }
                }

                if (sonosSpeakers.isEmpty()) {
                    Text(
                        text = "No Sonos speakers found on this network.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(Modifier.selectableGroup()) {
                        val isAllSelected = selectedSonosIp == SONOS_ALL_SPEAKERS
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .selectable(
                                    selected = isAllSelected,
                                    onClick = { onSonosSpeakerSelected(SONOS_ALL_SPEAKERS) },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = isAllSelected, onClick = null)
                            Text(
                                text = "All speakers",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }

                        sonosSpeakers.forEach { sonosSpeaker ->
                            val isSelected = sonosSpeaker.ip == selectedSonosIp
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .selectable(
                                        selected = isSelected,
                                        onClick = { onSonosSpeakerSelected(sonosSpeaker.ip) },
                                        role = Role.RadioButton
                                    )
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSelected, onClick = null)
                                Column(modifier = Modifier.padding(start = 16.dp)) {
                                    Text(text = sonosSpeaker.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = sonosSpeaker.model,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Default movie player",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Column(Modifier.selectableGroup()) {
                MoviePlayer.entries.forEach { player ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (player == selectedMoviePlayer),
                                onClick = { onMoviePlayerSelected(player) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (player == selectedMoviePlayer), onClick = null)
                        Text(
                            text = player.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            if (selectedMoviePlayer == MoviePlayer.GOOGLE_TV) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Default Google TV device", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onRefreshChromecast) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }

                if (chromecastDevices.isEmpty()) {
                    Text(
                        text = "No Google TV devices found. Make sure ADB debugging is enabled in Developer Options on your TV, then tap refresh.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(Modifier.selectableGroup()) {
                        chromecastDevices.forEach { device ->
                            val isSelected = device.id == selectedChromecastId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .selectable(
                                        selected = isSelected,
                                        onClick = { onChromecastDeviceSelected(device.id) },
                                        role = Role.RadioButton
                                    )
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSelected, onClick = null)
                                Column(modifier = Modifier.padding(start = 16.dp)) {
                                    Text(text = device.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = device.model,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Movies open directly on your Google TV via ADB. Enable \"ADB debugging\" in Developer Options on the TV — on first use, approve the \"Allow ADB debugging?\" prompt that appears on screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Movie streaming region",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            var regionExpanded by remember { mutableStateOf(false) }
            val selectedRegionName = REGIONS.find { it.first == selectedRegion }?.second ?: selectedRegion

            ExposedDropdownMenuBox(
                expanded = regionExpanded,
                onExpandedChange = { regionExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = "$selectedRegionName ($selectedRegion)",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = regionExpanded,
                    onDismissRequest = { regionExpanded = false }
                ) {
                    REGIONS.forEach { (code, name) ->
                        DropdownMenuItem(
                            text = { Text("$name ($code)") },
                            onClick = {
                                if (code != selectedRegion) showRegionWarning = code
                                regionExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onMovieProvidersClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Movie providers")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
}
