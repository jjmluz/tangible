package com.example.tangible.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.tangible.data.ChromecastDevice
import com.example.tangible.data.SonosSpeaker

enum class MusicService(val displayName: String) {
    SPOTIFY("Spotify"),
    YOUTUBE_MUSIC("YouTube Music")
}

enum class MusicSpeaker(val displayName: String) {
    THIS_DEVICE("This device"),
    SONOS("Sonos"),
    CHROMECAST("Chromecast")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
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
    onConnectSpotify: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
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
                        RadioButton(
                            selected = (service == selectedService),
                            onClick = null
                        )
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
                    val isDisabled = (speaker == MusicSpeaker.SONOS || speaker == MusicSpeaker.CHROMECAST) && selectedService == MusicService.YOUTUBE_MUSIC
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
                        RadioButton(
                            selected = (speaker == selectedSpeaker),
                            enabled = !isDisabled,
                            onClick = null
                        )
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(
                                text = speaker.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else Color.Unspecified
                            )
                            if (isDisabled) {
                                Text(
                                    text = "Not supported with YouTube Music",
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
                    Text(
                        text = "Default Chromecast device",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onRefreshChromecast) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Chromecast")
                    }
                }

                if (chromecastDevices.isEmpty()) {
                    Text(
                        text = "No Chromecast devices found on this network.",
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
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null
                                )
                                Column(modifier = Modifier.padding(start = 16.dp)) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = device.host,
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

                if (isSpotifyAuthorized) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Spotify account connected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text(
                        text = "Spotify account required to stream to Chromecast.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onConnectSpotify) {
                        Text("Connect Spotify account")
                    }
                }
            }

            if (selectedSpeaker == MusicSpeaker.SONOS) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Default Sonos speaker",
                        style = MaterialTheme.typography.titleMedium
                    )
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
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null
                                )
                                Column(modifier = Modifier.padding(start = 16.dp)) {
                                    Text(
                                        text = sonosSpeaker.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
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
        }
    }
}
