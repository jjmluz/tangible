package com.example.tangible.ui

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
    isNfcTagLaunchBlocked: Boolean = false,
    onFixNfcTagLaunch: () -> Unit = {},
    bottomContentPadding: androidx.compose.ui.unit.Dp = 112.dp
) {
    var selectedType by remember { mutableStateOf("MUSIC") }

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

    Column(modifier = modifier.fillMaxSize()) {
    if (isNfcTagLaunchBlocked) {
        NfcTagLaunchBlockedBanner(onFix = onFixNfcTagLaunch)
    }
    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
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
                    contentPadding = PaddingValues(top = 72.dp, bottom = bottomContentPadding)
                ) {
                    items(filteredTags, key = { it.id }) { tag ->
                        TagRow(
                            tag = tag,
                            isSelected = tag.id in selectedTagIds,
                            isSelectionMode = selectedTagIds.isNotEmpty(),
                            onPlay = { onPlay(tag) },
                            onTagClick = { onTagClick(tag.id) },
                            onTagLongClick = { onTagLongClick(tag.id) }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 72.dp, bottom = bottomContentPadding, start = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTags, key = { it.id }) { tag ->
                        TagGridItem(
                            tag = tag,
                            isSelected = tag.id in selectedTagIds,
                            isSelectionMode = selectedTagIds.isNotEmpty(),
                            onPlay = { onPlay(tag) },
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
}

/** Shown when Android's per-app NFC setting stops tags from opening Tangible. */
@Composable
fun NfcTagLaunchBlockedBanner(onFix: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "NFC tags can't open Tangible",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Scanning a tag won't play anything until you allow it in Android's NFC settings.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onFix) { Text("Allow") }
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

@Composable
private fun TagRow(
    tag: SavedTag,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onPlay: () -> Unit,
    onTagClick: () -> Unit,
    onTagLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                // A tap plays the album or movie; a long press starts selecting.
                onClick = { if (isSelectionMode) onTagClick() else onPlay() },
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

        if (isSelectionMode) {
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
    onTagClick: () -> Unit,
    onTagLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                // A tap plays the album or movie; a long press starts selecting.
                onClick = { if (isSelectionMode) onTagClick() else onPlay() },
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
