package com.example.tangible.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tangible.SavedTag

@Composable
fun MainScreen(
    tags: List<SavedTag>,
    selectedTagIds: Set<String>,
    onPlay: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onTagLongClick: (String) -> Unit
) {
    var selectedType by remember { mutableStateOf("MUSIC") }
    val filteredTags = remember(tags, selectedType) {
        tags.filter { it.type == selectedType }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (tags.isNotEmpty()) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SegmentedButton(
                    selected = selectedType == "MUSIC",
                    onClick = { selectedType = "MUSIC" },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = null) }
                ) {
                    Text("Music")
                }
                SegmentedButton(
                    selected = selectedType == "MOVIE",
                    onClick = { selectedType = "MOVIE" },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Default.Movie, contentDescription = null) }
                ) {
                    Text("Movies")
                }
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
                        onPlay = onPlay,
                        onTagClick = { onTagClick(tag.id) },
                        onTagLongClick = { onTagLongClick(tag.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagRow(
    tag: SavedTag,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onPlay: (String) -> Unit,
    onTagClick: () -> Unit,
    onTagLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isSelectionMode) onTagClick() },
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
            IconButton(onClick = { onPlay(tag.url) }, modifier = Modifier.size(44.dp)) {
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
