package com.example.tangible.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
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
import com.example.tangible.data.MovieStreamingResult
import com.example.tangible.data.NFC_NTAG215_BYTES
import com.example.tangible.data.StreamingOption
import com.example.tangible.data.TmdbMovieResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TagType { MUSIC, MOVIE }

enum class WriteAsOption {
    TANGIBLE, SPOTIFY, YOUTUBE_MUSIC, APPLE_MUSIC
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
    onAlbumSelected: suspend (DiscogsSearchResult) -> TagMediaUris = { TagMediaUris() },
    onSearchMovies: suspend (String) -> List<TmdbMovieResult> = { emptyList() },
    onGetMovieStreaming: suspend (tmdbId: Int) -> MovieStreamingResult = {
        MovieStreamingResult(emptyList(), emptySet(), "", 0)
    },
    region: String = "US"
) {
    var tagType by remember { mutableStateOf(TagType.MUSIC) }

    // Music state
    var url by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<DiscogsSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingFirstTrack by remember { mutableStateOf(false) }
    var writeAs by remember { mutableStateOf(WriteAsOption.TANGIBLE) }
    var selectedUris by remember { mutableStateOf(TagMediaUris()) }

    // Movie state
    var movieSearchQuery by remember { mutableStateOf("") }
    var movieSearchResults by remember { mutableStateOf<List<TmdbMovieResult>>(emptyList()) }
    var isSearchingMovies by remember { mutableStateOf(false) }
    var isSelectingMovie by remember { mutableStateOf(false) }
    var selectedMovie by remember { mutableStateOf<TmdbMovieResult?>(null) }
    var streamingResult by remember { mutableStateOf<MovieStreamingResult?>(null) }
    var isLoadingStreaming by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Reset state when switching tag types
    LaunchedEffect(tagType) {
        url = ""
        if (tagType == TagType.MUSIC) {
            movieSearchQuery = ""
            movieSearchResults = emptyList()
            selectedMovie = null
            streamingResult = null
            isLoadingStreaming = false
        } else {
            searchQuery = ""
            searchResults = emptyList()
            selectedUris = TagMediaUris()
        }
    }

    // Music URL derivation
    LaunchedEffect(writeAs, selectedUris, tagType) {
        if (tagType != TagType.MUSIC) return@LaunchedEffect
        url = when (writeAs) {
            WriteAsOption.TANGIBLE -> {
                val queryParams = mutableListOf<String>()
                selectedUris.spotifyTrack?.let { queryParams.add("spotify_track=${Uri.encode(it)}") }
                selectedUris.spotifyAlbum?.let { queryParams.add("spotify_album=${Uri.encode(it)}") }
                selectedUris.ytm?.let { queryParams.add("ytm=${Uri.encode(it)}") }
                selectedUris.appleMusic?.let { queryParams.add("apple_music=${Uri.encode(it)}") }
                if (queryParams.isNotEmpty()) "tangible://play?${queryParams.joinToString("&")}" else ""
            }
            WriteAsOption.SPOTIFY -> when {
                selectedUris.spotifyAlbum != null -> "https://open.spotify.com/album/${selectedUris.spotifyAlbum}"
                selectedUris.spotifyTrack != null -> "https://open.spotify.com/track/${selectedUris.spotifyTrack}"
                else -> ""
            }
            WriteAsOption.YOUTUBE_MUSIC -> {
                val id = selectedUris.ytm
                if (id != null && !id.startsWith("http")) "https://music.youtube.com/playlist?list=$id" else id ?: ""
            }
            WriteAsOption.APPLE_MUSIC -> {
                val id = selectedUris.appleMusic
                if (id != null && !id.startsWith("http")) "https://music.apple.com/album/$id" else id ?: ""
            }
        }
    }

    // Fetch streaming availability when a movie is selected
    LaunchedEffect(selectedMovie) {
        val movie = selectedMovie
        if (movie == null) {
            streamingResult = null
            return@LaunchedEffect
        }
        isLoadingStreaming = true
        streamingResult = null
        streamingResult = onGetMovieStreaming(movie.id)
        isLoadingStreaming = false
    }

    // Movie URL comes from the streaming result
    LaunchedEffect(streamingResult, tagType) {
        if (tagType != TagType.MOVIE) return@LaunchedEffect
        url = streamingResult?.tagUrl ?: ""
    }

    // Music search debouncing
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

    // Movie search debouncing
    LaunchedEffect(movieSearchQuery) {
        if (isSelectingMovie) {
            isSelectingMovie = false
            return@LaunchedEffect
        }
        if (movieSearchQuery.length > 2) {
            isSearchingMovies = true
            delay(500)
            movieSearchResults = onSearchMovies(movieSearchQuery)
            isSearchingMovies = false
        } else {
            movieSearchResults = emptyList()
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
                        Button(onClick = onCancelClick) { Text("Cancel") }
                    }
                }
            } else {
                // Tag type selector
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = tagType == TagType.MUSIC,
                        onClick = { tagType = TagType.MUSIC },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = null) }
                    ) {
                        Text("Music")
                    }
                    SegmentedButton(
                        selected = tagType == TagType.MOVIE,
                        onClick = { tagType = TagType.MOVIE },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Default.Movie, contentDescription = null) }
                    ) {
                        Text("Movie")
                    }
                }

                when (tagType) {
                    TagType.MUSIC -> MusicContent(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        isSearching = isSearching,
                        isLoadingFirstTrack = isLoadingFirstTrack,
                        searchResults = searchResults,
                        onResultSelected = { result ->
                            scope.launch {
                                isLoadingFirstTrack = true
                                searchQuery = result.title
                                searchResults = emptyList()
                                selectedUris = onAlbumSelected(result)
                                isLoadingFirstTrack = false
                            }
                        },
                        url = url,
                        onUrlChange = { url = it },
                        writeError = writeError,
                        writeAs = writeAs,
                        onWriteAsChange = { writeAs = it },
                        onWriteClick = { onWriteClick(url) }
                    )
                    TagType.MOVIE -> MovieContent(
                        searchQuery = movieSearchQuery,
                        onSearchQueryChange = { movieSearchQuery = it },
                        isSearching = isSearchingMovies,
                        searchResults = movieSearchResults,
                        selectedMovie = selectedMovie,
                        onResultSelected = { movie ->
                            isSelectingMovie = true
                            selectedMovie = movie
                            movieSearchQuery = movie.title
                            movieSearchResults = emptyList()
                        },
                        streamingResult = streamingResult,
                        isLoadingStreaming = isLoadingStreaming,
                        region = region,
                        url = url,
                        onUrlChange = { url = it },
                        writeError = writeError,
                        onWriteClick = { onWriteClick(url) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicContent(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearching: Boolean,
    isLoadingFirstTrack: Boolean,
    searchResults: List<DiscogsSearchResult>,
    onResultSelected: (DiscogsSearchResult) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    writeError: String?,
    writeAs: WriteAsOption,
    onWriteAsChange: (WriteAsOption) -> Unit,
    onWriteClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
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
        SearchResultsCard {
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
                    modifier = Modifier.clickable { onResultSelected(result) }
                )
            }
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = when (writeAs) {
                WriteAsOption.TANGIBLE -> "Tangible URI (Multi-player)"
                WriteAsOption.SPOTIFY -> "Spotify URI (Direct)"
                WriteAsOption.YOUTUBE_MUSIC -> "YouTube Music URI (Direct)"
                WriteAsOption.APPLE_MUSIC -> "Apple Music URI (Direct)"
            },
            onValueChange = {},
            readOnly = true,
            label = { Text("Write as") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Tangible URI (Multi-player support)") },
                onClick = {
                    onWriteAsChange(WriteAsOption.TANGIBLE)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Spotify URI (Opens Spotify directly)") },
                onClick = {
                    onWriteAsChange(WriteAsOption.SPOTIFY)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("YouTube Music URI (Opens YTM directly)") },
                onClick = {
                    onWriteAsChange(WriteAsOption.YOUTUBE_MUSIC)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Apple Music URI (Opens Apple Music directly)") },
                onClick = {
                    onWriteAsChange(WriteAsOption.APPLE_MUSIC)
                    expanded = false
                }
            )
        }
    }

    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text("URL to Write") },
        placeholder = { Text("https://...") },
        modifier = Modifier.fillMaxWidth()
    )

    if (writeError != null) {
        Text(text = writeError, color = MaterialTheme.colorScheme.error)
    }

    Button(
        onClick = onWriteClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = url.isNotBlank()
    ) {
        Text("Write to Tag")
    }
}

@Composable
private fun MovieContent(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearching: Boolean,
    searchResults: List<TmdbMovieResult>,
    selectedMovie: TmdbMovieResult?,
    onResultSelected: (TmdbMovieResult) -> Unit,
    streamingResult: MovieStreamingResult?,
    isLoadingStreaming: Boolean,
    region: String,
    url: String,
    onUrlChange: (String) -> Unit,
    writeError: String?,
    onWriteClick: () -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        label = { Text("Search Movie (TMDB)") },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.Default.Search, contentDescription = null)
            }
        }
    )

    if (searchResults.isNotEmpty()) {
        SearchResultsCard {
            items(searchResults) { movie ->
                ListItem(
                    headlineContent = { Text(movie.title) },
                    supportingContent = { if (movie.year.isNotBlank()) Text(movie.year) },
                    leadingContent = {
                        AsyncImage(
                            model = movie.posterUrl,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                    },
                    modifier = Modifier.clickable { onResultSelected(movie) }
                )
            }
        }
    }

    if (selectedMovie != null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = selectedMovie.posterUrl,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                )
                Column {
                    Text(selectedMovie.title, style = MaterialTheme.typography.titleMedium)
                    if (selectedMovie.year.isNotBlank()) {
                        Text(selectedMovie.year, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        when {
            isLoadingStreaming -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        "Fetching streaming availability in $region…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            streamingResult != null && streamingResult.options.isEmpty() -> {
                Text(
                    "Not available for streaming in $region.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            streamingResult != null -> {
                StreamingServicesCard(streamingResult, region)
            }
        }
    }

    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text("URL to Write") },
        placeholder = { Text("tangible://movie?tmdb_id=…") },
        modifier = Modifier.fillMaxWidth()
    )

    if (url.isNotBlank()) {
        val byteCount = url.toByteArray(Charsets.UTF_8).size
        val tagNote = when {
            byteCount <= NFC_NTAG215_BYTES -> "Fits NTAG215 and larger"
            else -> "Requires NTAG216 or larger"
        }
        val noteColor = if (byteCount <= NFC_NTAG215_BYTES)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.error
        Text(
            "$byteCount bytes · $tagNote",
            style = MaterialTheme.typography.labelSmall,
            color = noteColor
        )
    }

    if (writeError != null) {
        Text(text = writeError, color = MaterialTheme.colorScheme.error)
    }

    Button(
        onClick = onWriteClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = url.isNotBlank()
    ) {
        Text("Write to Tag")
    }
}

@Composable
private fun StreamingServicesCard(result: MovieStreamingResult, region: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Available in $region",
                style = MaterialTheme.typography.labelLarge
            )
            result.options.forEach { option ->
                val encoded = option.serviceId in result.encodedServiceIds
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (encoded) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        // Spacer to keep alignment when icon is absent
                        Spacer(modifier = Modifier.size(16.dp))
                    }
                    val typeLabel = option.allTypes.joinToString(", ") { type ->
                        type.replaceFirstChar { it.uppercase() }
                    }
                    val statusText = if (!encoded) " (tag full — use NTAG216+)" else ""
                    Text(
                        text = "${option.serviceName} ($typeLabel)$statusText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (encoded)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsCard(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        LazyColumn { content() }
    }
}

@Preview(showBackground = true)
@Composable
fun WriteTagScreenPreview() {
    MaterialTheme {
        WriteTagScreen(onBackClick = {}, onWriteClick = { _ -> })
    }
}
