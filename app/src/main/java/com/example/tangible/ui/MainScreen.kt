package com.example.tangible.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

enum class ViewMode {
    LIST, GALLERY
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    tags: List<SavedTag>,
    selectedTagIds: Set<String>,
    onPlay: (SavedTag) -> Unit,
    onTagClick: (String) -> Unit,
    onTagLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    musicSortField: SortField = SortField.TITLE,
    musicSortOrder: SortOrder = SortOrder.ASCENDING,
    movieSortField: SortField = SortField.TITLE,
    movieSortOrder: SortOrder = SortOrder.ASCENDING,
    viewMode: ViewMode = ViewMode.LIST,
    onMusicSortChange: (SortField, SortOrder) -> Unit = { _, _ -> },
    onMovieSortChange: (SortField, SortOrder) -> Unit = { _, _ -> },
    onViewModeChange: (ViewMode) -> Unit = {},
    onUpdateTag: (SavedTag) -> Unit = {},
    onDeleteTag: (String) -> Unit = {},
    sonosSpeakers: List<com.example.tangible.data.SonosSpeaker> = emptyList(),
    chromecastDevices: List<com.example.tangible.data.ChromecastDevice> = emptyList(),
    movieProviders: List<com.example.tangible.data.MovieProvider> = emptyList()
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
            chromecastDevices = chromecastDevices,
            movieProviders = movieProviders
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (filteredTags.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
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
            if (viewMode == ViewMode.LIST) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 72.dp, bottom = 112.dp)
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
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 72.dp, bottom = 112.dp, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTags, key = { it.id }) { tag ->
                        TagGridItem(
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

        if (tags.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TangibleBackground)
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

                    IconButton(
                        onClick = {
                            onViewModeChange(if (viewMode == ViewMode.LIST) ViewMode.GALLERY else ViewMode.LIST)
                        }
                    ) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.LIST) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                            contentDescription = "Switch View Mode",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

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
                
                // Top gradient bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(TangibleBackground, Color.Transparent)
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun SortMenu(
    selectedType: String,
    currentField: SortField,
    currentOrder: SortOrder,
    onSortChange: (SortField, SortOrder) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Text(
                text = "Sort by",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = TangiblePurple
            )
            
            SortField.entries.forEach { field ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (field == SortField.SUBTITLE) (if (selectedType == "MUSIC") "Artist" else "Year") else field.displayName)
                            if (currentField == field) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    onClick = {
                        onSortChange(field, currentOrder)
                        expanded = false
                    }
                )
            }

            HorizontalDivider()

            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(order.displayName)
                            if (currentOrder == order) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    onClick = {
                        onSortChange(currentField, order)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDetailsModal(
    tag: SavedTag,
    onDismiss: () -> Unit,
    onUpdate: (SavedTag) -> Unit,
    onDelete: () -> Unit,
    sonosSpeakers: List<com.example.tangible.data.SonosSpeaker>,
    chromecastDevices: List<com.example.tangible.data.ChromecastDevice>,
    movieProviders: List<com.example.tangible.data.MovieProvider>
) {
    var selectedService by remember { mutableStateOf(tag.customService ?: "DEFAULT") }
    var selectedOutputId by remember { mutableStateOf(tag.customOutputId ?: "DEFAULT") }
    val isTangibleUri = tag.url.startsWith("tangible://")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (tag.imageUrl != null) {
                            AsyncImage(
                                model = tag.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(if (tag.type == "MUSIC") "♪" else "▶", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(tag.title, style = MaterialTheme.typography.titleMedium)
                        Text(tag.subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Service Dropdown
                DetailDropdown(
                    label = "Service",
                    selectedId = selectedService,
                    onSelectedChange = { selectedService = it },
                    options = buildList {
                        if (isTangibleUri) {
                            add("DEFAULT" to "Default")
                            if (tag.type == "MUSIC") {
                                add("SPOTIFY" to "Spotify")
                                add("YOUTUBE_MUSIC" to "YouTube Music")
                                add("APPLE_MUSIC" to "Apple Music")
                            } else {
                                val serviceDisplayNames = com.example.tangible.data.StreamingAvailabilityRepository.getServiceDisplayNamesFromTangibleUri(tag.url)
                                movieProviders
                                    .filter { it.isEnabled && it.id in serviceDisplayNames }
                                    .forEach { add("MOVIE_PROVIDER:${it.id}" to (serviceDisplayNames[it.id] ?: it.name)) }
                            }
                        } else {
                            val serviceName = when {
                                tag.url.contains("spotify.com") -> "Spotify"
                                tag.url.contains("youtube.com") || tag.url.contains("music.youtube.com") -> "YouTube Music"
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
                        if (isTangibleUri) {
                            add("DEFAULT" to "Default")
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
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onTagClick() },
                colors = CheckboxDefaults.colors(checkedColor = TangiblePurple)
            )
        }
    }
}

@Composable
private fun TagGridItem(
    tag: SavedTag,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onTagClick: () -> Unit,
    onTagLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { if (isSelectionMode) onTagClick() else onEdit() },
                onLongClick = onTagLongClick
            )
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cover art
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            val imageModifier = if (tag.type == "MOVIE") {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(2f / 3f)
            } else {
                Modifier.fillMaxSize()
            }
            Box(
                modifier = imageModifier.clip(RoundedCornerShape(4.dp)),
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

            // Play button overlay
            if (!isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Surface(
                        onClick = onPlay,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = TangiblePurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = tag.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = tag.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
