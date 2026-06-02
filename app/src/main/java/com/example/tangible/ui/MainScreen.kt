package com.example.tangible.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tangible.SavedTag

enum class SortField(val displayName: String) {
    TITLE("Title"),
    SUBTITLE("Artist/Year"),
    DATE_ADDED("Date Added")
}

enum class SortOrder(val displayName: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

@Composable
fun MainScreen(
    tags: List<SavedTag>,
    selectedTagIds: Set<String>,
    musicSortField: SortField = SortField.TITLE,
    musicSortOrder: SortOrder = SortOrder.ASCENDING,
    movieSortField: SortField = SortField.TITLE,
    movieSortOrder: SortOrder = SortOrder.ASCENDING,
    onMusicSortChange: (SortField, SortOrder) -> Unit = { _, _ -> },
    onMovieSortChange: (SortField, SortOrder) -> Unit = { _, _ -> },
    onPlay: (SavedTag) -> Unit,
    onTagClick: (String) -> Unit,
    onTagLongClick: (String) -> Unit,
    onUpdateTag: (SavedTag) -> Unit = {},
    onDeleteTag: (String) -> Unit = {},
    sonosSpeakers: List<com.example.tangible.data.SonosSpeaker> = emptyList(),
    chromecastDevices: List<com.example.tangible.data.ChromecastDevice> = emptyList()
) {
    var selectedType by remember { mutableStateOf("MUSIC") }
    var tagToEdit by remember { mutableStateOf<SavedTag?>(null) }
    
    val currentSortField = if (selectedType == "MUSIC") musicSortField else movieSortField
    val currentSortOrder = if (selectedType == "MUSIC") musicSortOrder else movieSortOrder

    val filteredTags = remember(tags, selectedType, currentSortField, currentSortOrder) {
        val filtered = tags.filter { it.type == selectedType }
        when (currentSortField) {
            SortField.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortField.SUBTITLE -> filtered.sortedBy { it.subtitle.lowercase() }
            SortField.DATE_ADDED -> filtered.sortedBy { it.createdAt }
        }.let { if (currentSortOrder == SortOrder.DESCENDING) it.reversed() else it }
    }

    if (tagToEdit != null) {
        TagDetailsModal(
            tag = tagToEdit!!,
            onDismiss = { tagToEdit = null },
            onUpdate = { updated ->
                onUpdateTag(updated)
                tagToEdit = null
            },
            onDelete = {
                onDeleteTag(tagToEdit!!.id)
                tagToEdit = null
            },
            sonosSpeakers = sonosSpeakers,
            chromecastDevices = chromecastDevices
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (tags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.weight(1f)
                ) {
                    SegmentedButton(
                        selected = selectedType == "MUSIC",
                        onClick = { selectedType = "MUSIC" },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = 0,
                            count = 2,
                            baseShape = RoundedCornerShape(8.dp)
                        ),
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = null) }
                    ) {
                        Text("Music")
                    }
                    SegmentedButton(
                        selected = selectedType == "MOVIE",
                        onClick = { selectedType = "MOVIE" },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = 1,
                            count = 2,
                            baseShape = RoundedCornerShape(8.dp)
                        ),
                        icon = { Icon(Icons.Default.Movie, contentDescription = null) }
                    ) {
                        Text("Movies")
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                SortMenu(
                    selectedType = selectedType,
                    currentField = currentSortField,
                    currentOrder = currentSortOrder,
                    onSortChange = { field, order ->
                        if (selectedType == "MUSIC") onMusicSortChange(field, order)
                        else onMovieSortChange(field, order)
                    }
                )
            }
        }

        if (filteredTags.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (tags.isEmpty()) {
                        "No tags yet.\nTap + to write your first tag."
                    } else {
                        "No ${selectedType.lowercase()} tags found."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(filteredTags, key = { it.id }) { tag ->
                    TagRow(
                        tag = tag,
                        isSelected = tag.id in selectedTagIds,
                        isSelectionMode = selectedTagIds.isNotEmpty(),
                        onPlay = { onPlay(tag) },
                        onEdit = { tagToEdit = tag },
                        onTagClick = { onTagClick(tag.id) },
                        onTagLongClick = { onTagLongClick(tag.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenu(
    selectedType: String,
    currentField: SortField,
    currentOrder: SortOrder,
    onSortChange: (SortField, SortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.Sort,
                contentDescription = "Sort",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = TangiblePurple
            )

            SortField.entries.forEach { field ->
                val displayName = when (field) {
                    SortField.SUBTITLE -> if (selectedType == "MUSIC") "Artist" else "Year"
                    else -> field.displayName
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = displayName,
                            fontWeight = if (currentField == field) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSortChange(field, currentOrder)
                        expanded = false
                    },
                    trailingIcon = {
                        if (currentField == field) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = order.displayName,
                            fontWeight = if (currentOrder == order) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSortChange(currentField, order)
                        expanded = false
                    },
                    trailingIcon = {
                        if (currentOrder == order) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun TagDetailsModal(
    tag: SavedTag,
    onDismiss: () -> Unit,
    onUpdate: (SavedTag) -> Unit,
    onDelete: () -> Unit,
    sonosSpeakers: List<com.example.tangible.data.SonosSpeaker>,
    chromecastDevices: List<com.example.tangible.data.ChromecastDevice>
) {
    val isTangibleUri = tag.url.startsWith("tangible://")
    
    // Parse available services if it's a Tangible URI
    val availableServices = remember(tag) {
        if (!isTangibleUri) return@remember emptyList<String>()
        val uri = Uri.parse(tag.url)
        if (tag.type == "MUSIC") {
            buildList {
                if (uri.getQueryParameter("spotify_track") != null || uri.getQueryParameter("spotify_album") != null) add("SPOTIFY")
                if (uri.getQueryParameter("ytm") != null) add("YOUTUBE_MUSIC")
                if (uri.getQueryParameter("apple_music") != null) add("APPLE_MUSIC")
            }
        } else {
            // For movies, we can show the provider names or just "Auto-resolve"
            listOf("AUTO")
        }
    }

    var selectedService by remember { mutableStateOf(tag.customService ?: "DEFAULT") }
    var selectedOutputId by remember { mutableStateOf(tag.customOutputId ?: "DEFAULT") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tag.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Streaming Service Dropdown
                DetailDropdown(
                    label = "Streaming service",
                    selectedId = selectedService,
                    onSelectedChange = { selectedService = it },
                    options = buildList {
                        add("DEFAULT" to "Default")
                        if (isTangibleUri) {
                            if (tag.type == "MUSIC") {
                                availableServices.forEach { s ->
                                    val name = when(s) {
                                        "SPOTIFY" -> "Spotify"
                                        "YOUTUBE_MUSIC" -> "YouTube Music"
                                        "APPLE_MUSIC" -> "Apple Music"
                                        else -> s
                                    }
                                    add(s to name)
                                }
                            } else {
                                add("AUTO" to "Auto-resolve")
                            }
                        } else {
                            // Direct link
                            val serviceName = when {
                                tag.url.contains("spotify.com") -> "Spotify"
                                tag.url.contains("youtube.com") -> "YouTube Music"
                                tag.url.contains("apple.com") -> "Apple Music"
                                else -> "Direct Link"
                            }
                            add("DIRECT" to serviceName)
                        }
                    },
                    enabled = isTangibleUri
                )

                // Output Dropdown
                DetailDropdown(
                    label = "Output",
                    selectedId = selectedOutputId,
                    onSelectedChange = { selectedOutputId = it },
                    options = buildList {
                        add("DEFAULT" to "Default")
                        if (isTangibleUri) {
                            add("THIS_DEVICE" to "This device")
                            if (tag.type == "MUSIC") {
                                add("SONOS_ALL" to "Sonos: All speakers")
                                sonosSpeakers.forEach { add("SONOS:${it.ip}" to "Sonos: ${it.name}") }
                            }
                            chromecastDevices.forEach { add("CHROMECAST:${it.id}" to "Google TV: ${it.name}") }
                        } else {
                            add("THIS_DEVICE" to "This device")
                        }
                    },
                    enabled = isTangibleUri
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                TextButton(onClick = {
                    onUpdate(tag.copy(
                        customService = if (selectedService == "DEFAULT") null else selectedService,
                        customOutputId = if (selectedOutputId == "DEFAULT") null else selectedOutputId
                    ))
                }) {
                    Text("Save")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailDropdown(
    label: String,
    selectedId: String,
    onSelectedChange: (String) -> Unit,
    options: List<Pair<String, String>>,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = options.find { it.first == selectedId }?.second ?: options.firstOrNull()?.second ?: ""

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TangiblePurple)
        }
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedName,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                trailingIcon = { if (enabled) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onSelectedChange(id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
@Composable
private fun TagRow(
    tag: SavedTag,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onTagClick: () -> Unit,
    onTagLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isSelectionMode) onTagClick() else onEdit() },
                onLongClick = onTagLongClick
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ... (Cover art remains the same)
        // Cover art
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(72.dp),
            contentAlignment = Alignment.Center
        ) {
            val imageModifier = if (tag.type == "MOVIE") {
                Modifier
                    .width(48.dp)
                    .height(72.dp)
            } else {
                Modifier.size(72.dp)
            }
            Box(
                modifier = imageModifier.clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (tag.imageUrl != null) {
                    AsyncImage(
                        model = tag.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (tag.type == "MUSIC") "♪" else "▶",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        // Title + subtitle
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = tag.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = tag.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Play button - disappear in selection mode
        if (!isSelectionMode) {
            IconButton(onClick = onPlay, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = TangiblePurple,
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            // Optional: show a checkbox or just rely on the background color
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onTagClick() },
                colors = CheckboxDefaults.colors(checkedColor = TangiblePurple)
            )
        }
    }
}
