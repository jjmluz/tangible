package com.example.tangible.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tangible.TagMediaUris
import com.example.tangible.data.DiscogsSearchResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class WriteAsOption {
    TANGIBLE, SPOTIFY, YOUTUBE_MUSIC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteTagScreen(
    onBackClick: () -> Unit,
    isWriting: Boolean = false,
    writeError: String? = null,
    onWriteClick: (url: String) -> Unit,
    onCancelClick: () -> Unit = {},
    onSearchAlbums: suspend (String) -> List<DiscogsSearchResult> = { emptyList() },
    onAlbumSelected: suspend (DiscogsSearchResult) -> TagMediaUris = { TagMediaUris() }
) {
    var url by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<DiscogsSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingFirstTrack by remember { mutableStateOf(false) }
    var writeAs by remember { mutableStateOf(WriteAsOption.TANGIBLE) }
    var selectedUris by remember { mutableStateOf(TagMediaUris()) }
    val scope = rememberCoroutineScope()

    // Update URL when radio button selection changes
    LaunchedEffect(writeAs, selectedUris) {
        url = when (writeAs) {
            WriteAsOption.TANGIBLE -> {
                val queryParams = mutableListOf<String>()
                selectedUris.spotifyTrack?.let { queryParams.add("spotify_track=${Uri.encode(it)}") }
                selectedUris.spotifyAlbum?.let { queryParams.add("spotify_album=${Uri.encode(it)}") }
                selectedUris.ytm?.let { queryParams.add("ytm=${Uri.encode(it)}") }
                if (queryParams.isNotEmpty()) {
                    "tangible://play?${queryParams.joinToString("&")}"
                } else ""
            }
            WriteAsOption.SPOTIFY -> {
                // Use track for phone
                val uri = selectedUris.spotifyTrack ?: selectedUris.spotifyAlbum
                uri?.let { u ->
                    val parts = u.split(":")
                    if (parts.size >= 3 && parts[0] == "spotify") {
                        "https://open.spotify.com/${parts[1]}/${parts[2]}"
                    } else {
                        u
                    }
                } ?: ""
            }
            WriteAsOption.YOUTUBE_MUSIC -> {
                selectedUris.ytm ?: ""
            }
        }
    }

    // Search debouncing
    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 2 && !isLoadingFirstTrack) {
            isSearching = true
            delay(500)
            searchResults = onSearchAlbums(searchQuery)
            isSearching = false
        } else {
            searchResults = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Write Tag") },
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
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isWriting) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Hold tag near device...")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onCancelClick) {
                            Text("Cancel")
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Album (Discogs)") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (isSearching || isLoadingFirstTrack) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    }
                )

                if (searchResults.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        LazyColumn {
                            items(searchResults) { result ->
                                ListItem(
                                    headlineContent = { Text(result.title) },
                                    leadingContent = {
                                        AsyncImage(
                                            model = result.thumb,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            isLoadingFirstTrack = true
                                            searchQuery = result.title
                                            searchResults = emptyList()
                                            selectedUris = onAlbumSelected(result)
                                            isLoadingFirstTrack = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL to Write") },
                    placeholder = { Text("https://...") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (writeError != null) {
                    Text(text = writeError, color = MaterialTheme.colorScheme.error)
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Write as:", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = writeAs == WriteAsOption.TANGIBLE,
                            onClick = { writeAs = WriteAsOption.TANGIBLE }
                        )
                        Text(
                            "Tangible URI (Multi-player support)",
                            modifier = Modifier.clickable { writeAs = WriteAsOption.TANGIBLE }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = writeAs == WriteAsOption.SPOTIFY,
                            onClick = { writeAs = WriteAsOption.SPOTIFY }
                        )
                        Text(
                            "Spotify URI (Opens Spotify directly)",
                            modifier = Modifier.clickable { writeAs = WriteAsOption.SPOTIFY }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = writeAs == WriteAsOption.YOUTUBE_MUSIC,
                            onClick = { writeAs = WriteAsOption.YOUTUBE_MUSIC }
                        )
                        Text(
                            "YouTube Music URI (Opens YTM directly)",
                            modifier = Modifier.clickable { writeAs = WriteAsOption.YOUTUBE_MUSIC }
                        )
                    }
                }

                Button(
                    onClick = { onWriteClick(url) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = url.isNotBlank()
                ) {
                    Text("Write to Tag")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WriteTagScreenPreview() {
    MaterialTheme {
        WriteTagScreen(onBackClick = {}, onWriteClick = { _ -> })
    }
}
