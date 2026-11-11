package com.jjmluz.tangible.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.jjmluz.tangible.SavedTag

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
    bottomContentPadding: androidx.compose.ui.unit.Dp = 112.dp,
    initialType: String = "MUSIC",
    onTypeChange: (String) -> Unit = {}
) {
    var selectedType by remember { mutableStateOf(initialType) }

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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 76.dp, bottom = bottomContentPadding),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(filteredTags, key = { _, tag -> tag.id }) { index, tag ->
                        TagRow(
                            tag = tag,
                            shape = groupedRowShape(index, filteredTags.size),
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
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        TypeButton(
                            label = "Music",
                            icon = Icons.Default.MusicNote,
                            selected = selectedType == "MUSIC",
                            isFirst = true,
                            onClick = { selectedType = "MUSIC"; onTypeChange("MUSIC") },
                            modifier = Modifier.weight(1f)
                        )
                        TypeButton(
                            label = "Movies",
                            icon = Icons.Default.Movie,
                            selected = selectedType == "MOVIE",
                            isFirst = false,
                            onClick = { selectedType = "MOVIE"; onTypeChange("MOVIE") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            onViewModeChange(if (viewMode == ViewMode.LIST) ViewMode.GALLERY else ViewMode.LIST)
                        }
                    ) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.LIST) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                            contentDescription = "Switch View Mode",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                )
                            )
                        )
                )
            }
        }
    }
    }
}

/**
 * One half of the Music/Movies switch: the selected half is a full pill in the primary colour,
 * the other is tonal with tighter corners on the side where the two meet.
 */
@Composable
internal fun TypeButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    isFirst: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inner by animateDpAsState(if (selected) 24.dp else 8.dp, label = "typeButtonCorner")
    val shape = if (isFirst) {
        RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = inner, bottomEnd = inner)
    } else {
        RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = 24.dp, bottomEnd = 24.dp)
    }
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Rows read as one group: big corners on the outside, small ones where rows meet. */
internal fun groupedRowShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) 24.dp else 6.dp
    val bottom = if (index == count - 1) 24.dp else 6.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                color = MaterialTheme.colorScheme.primary
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
    shape: RoundedCornerShape,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onPlay: () -> Unit,
    onTagClick: () -> Unit,
    onTagLongClick: () -> Unit
) {
    val container by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        label = "tagRowContainer"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .combinedClickable(
                // A tap plays the album or movie; a long press starts selecting.
                onClick = { if (isSelectionMode) onTagClick() else onPlay() },
                onLongClick = onTagLongClick
            )
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover art
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(60.dp),
            contentAlignment = Alignment.Center
        ) {
            val imageModifier = if (tag.type == "MOVIE") {
                Modifier
                    .width(40.dp)
                    .height(60.dp)
            } else {
                Modifier.size(60.dp)
            }
            Box(
                modifier = imageModifier.clip(RoundedCornerShape(if (tag.type == "MOVIE") 8.dp else 14.dp)),
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
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tag.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onTagClick() }
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
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(
                // A tap plays the album or movie; a long press starts selecting.
                onClick = { if (isSelectionMode) onTagClick() else onPlay() },
                onLongClick = onTagLongClick
            )
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
                modifier = imageModifier.clip(RoundedCornerShape(12.dp)),
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
