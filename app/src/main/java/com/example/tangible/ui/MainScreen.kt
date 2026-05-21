package com.example.tangible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private data class MockTag(
    val title: String,
    val subtitle: String,
    val type: TagType,
    val artColor: Color
)

private val mockTags = listOf(
    MockTag("The Dark Side of the Moon", "Pink Floyd", TagType.MUSIC, Color(0xFF1A1A6E)),
    MockTag("Inception", "2010 · Nolan", TagType.MOVIE, Color(0xFF0D3B1E)),
    MockTag("Abbey Road", "The Beatles", TagType.MUSIC, Color(0xFF4A0E2D)),
    MockTag("Interstellar", "2014 · Nolan", TagType.MOVIE, Color(0xFF0A2744)),
    MockTag("Kind of Blue", "Miles Davis", TagType.MUSIC, Color(0xFF004D40)),
    MockTag("The Godfather", "1972 · Coppola", TagType.MOVIE, Color(0xFF3B1A0A)),
    MockTag("Random Access Memories", "Daft Punk", TagType.MUSIC, Color(0xFF5C3200)),
    MockTag("2001: A Space Odyssey", "1968 · Kubrick", TagType.MOVIE, Color(0xFF1A0A3B)),
)

@Composable
fun MainScreen() {
    if (mockTags.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No tags yet.\nTap + to write your first tag.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(mockTags) { tag ->
                TagRow(tag)
            }
        }
    }
}

@Composable
private fun TagRow(tag: MockTag) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover art
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tag.artColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (tag.type == TagType.MUSIC) "♪" else "▶",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White.copy(alpha = 0.35f)
            )
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

        // Action buttons
        IconButton(onClick = {}, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit tag",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = {}, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = TangiblePurple,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
