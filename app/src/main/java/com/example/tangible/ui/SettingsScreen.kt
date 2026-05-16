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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.tangible.data.SonosSpeaker

enum class MusicService(val displayName: String) {
    SPOTIFY("Spotify"),
    YOUTUBE_MUSIC("YouTube Music")
}

enum class MusicSpeaker(val displayName: String) {
    THIS_DEVICE("This device"),
    SONOS("Sonos")
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
    onRefreshSonos: () -> Unit
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
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (speaker == selectedSpeaker),
                                onClick = { onSpeakerSelected(speaker) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (speaker == selectedSpeaker),
                            onClick = null
                        )
                        Text(
                            text = speaker.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
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
