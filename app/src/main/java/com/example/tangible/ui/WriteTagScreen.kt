package com.example.tangible.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tangible.TagMediaUris
import com.example.tangible.data.DiscogsSearchResult
import com.example.tangible.data.MovieStreamingResult
import com.example.tangible.data.TmdbMovieResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TagType { MUSIC, MOVIE }

data class TagWriteInfo(
    val title: String,
    val subtitle: String,
    val type: TagType,
    val imageUrl: String?,
    val url: String
)

enum class WriteAsOption {
    TANGIBLE, SPOTIFY, YOUTUBE_MUSIC, APPLE_MUSIC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteTagScreen(
    onBackClick: () -> Unit,
    isWriting: Boolean = false,
    writeError: String? = null,
    onWriteClick: (TagWriteInfo) -> Unit,
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
    var selectedAlbum by remember { mutableStateOf<DiscogsSearchResult?>(null) }

    // Movie state
    var movieSearchQuery by remember { mutableStateOf("") }
    var movieSearchResults by remember { mutableStateOf<List<TmdbMovieResult>>(emptyList()) }
    var isSearchingMovies by remember { mutableStateOf(false) }
    var selectedMovie by remember { mutableStateOf<TmdbMovieResult?>(null) }
    var streamingResult by remember { mutableStateOf<MovieStreamingResult?>(null) }
    var isLoadingStreaming by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val showConfirmScreen = when (tagType) {
        TagType.MUSIC -> selectedAlbum != null
        TagType.MOVIE -> selectedMovie != null
    }

    // Reset state when switching tag types
    LaunchedEffect(tagType) {
        url = ""
        selectedAlbum = null
        selectedMovie = null
        selectedUris = TagMediaUris()
        streamingResult = null
        if (tagType == TagType.MUSIC) {
            movieSearchQuery = ""
            movieSearchResults = emptyList()
        } else {
            searchQuery = ""
            searchResults = emptyList()
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
            isLoadingStreaming = false
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
                title = {
                    Text(
                        "Write Tag",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showConfirmScreen) {
                            selectedAlbum = null
                            selectedMovie = null
                            selectedUris = TagMediaUris()
                            url = ""
                            streamingResult = null
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TangibleBackground),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        containerColor = TangibleBackground
    ) { padding ->
        if (isWriting) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Hold tag near device...")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onCancelClick) { Text("Cancel") }
                }
            }
        } else if (showConfirmScreen) {
            WriteTagConfirmContent(
                tagType = tagType,
                selectedAlbum = selectedAlbum,
                selectedMovie = selectedMovie,
                isLoadingFirstTrack = isLoadingFirstTrack,
                isLoadingStreaming = isLoadingStreaming,
                streamingResult = streamingResult,
                region = region,
                url = url,
                writeAs = writeAs,
                onWriteAsChange = { writeAs = it },
                writeError = writeError,
                onWriteClick = {
                    val title = when (tagType) {
                        TagType.MUSIC -> selectedAlbum?.albumName ?: ""
                        TagType.MOVIE -> selectedMovie?.title ?: ""
                    }
                    val subtitle = when (tagType) {
                        TagType.MUSIC -> selectedAlbum?.artistName ?: ""
                        TagType.MOVIE -> selectedMovie?.year ?: ""
                    }
                    val imageUrl = when (tagType) {
                        TagType.MUSIC -> selectedAlbum?.displayImage
                        TagType.MOVIE -> selectedMovie?.largePosterUrl
                    }
                    onWriteClick(TagWriteInfo(title = title, subtitle = subtitle, type = tagType, imageUrl = imageUrl, url = url))
                },
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        } else {
            WriteTagSearchContent(
                tagType = tagType,
                onTagTypeChange = { tagType = it },
                musicSearchQuery = searchQuery,
                onMusicSearchQueryChange = { searchQuery = it },
                isSearchingMusic = isSearching || isLoadingFirstTrack,
                musicResults = searchResults,
                onMusicResultSelected = { result ->
                    selectedAlbum = result
                    scope.launch {
                        isLoadingFirstTrack = true
                        selectedUris = onAlbumSelected(result)
                        isLoadingFirstTrack = false
                    }
                },
                movieSearchQuery = movieSearchQuery,
                onMovieSearchQueryChange = { movieSearchQuery = it },
                isSearchingMovies = isSearchingMovies,
                movieResults = movieSearchResults,
                onMovieResultSelected = { movie ->
                    selectedMovie = movie
                },
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            )
        }
    }
}

@Composable
private fun WriteTagSearchContent(
    tagType: TagType,
    onTagTypeChange: (TagType) -> Unit,
    musicSearchQuery: String,
    onMusicSearchQueryChange: (String) -> Unit,
    isSearchingMusic: Boolean,
    musicResults: List<DiscogsSearchResult>,
    onMusicResultSelected: (DiscogsSearchResult) -> Unit,
    movieSearchQuery: String,
    onMovieSearchQueryChange: (String) -> Unit,
    isSearchingMovies: Boolean,
    movieResults: List<TmdbMovieResult>,
    onMovieResultSelected: (TmdbMovieResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = tagType == TagType.MUSIC,
                onClick = { onTagTypeChange(TagType.MUSIC) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Default.MusicNote, contentDescription = null) }
            ) {
                Text("Music")
            }
            SegmentedButton(
                selected = tagType == TagType.MOVIE,
                onClick = { onTagTypeChange(TagType.MOVIE) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Default.Movie, contentDescription = null) }
            ) {
                Text("Movie")
            }
        }

        when (tagType) {
            TagType.MUSIC -> {
                OutlinedTextField(
                    value = musicSearchQuery,
                    onValueChange = onMusicSearchQueryChange,
                    label = { Text("Search artist or album") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (isSearchingMusic) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    }
                )
                if (musicResults.isNotEmpty()) {
                    SearchResultsCard {
                        items(musicResults) { result ->
                            ListItem(
                                headlineContent = { Text(result.title) },
                                leadingContent = {
                                    AsyncImage(
                                        model = result.thumb,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp)
                                    )
                                },
                                modifier = Modifier.clickable { onMusicResultSelected(result) }
                            )
                        }
                    }
                }
            }
            TagType.MOVIE -> {
                OutlinedTextField(
                    value = movieSearchQuery,
                    onValueChange = onMovieSearchQueryChange,
                    label = { Text("Search movie") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (isSearchingMovies) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    }
                )
                if (movieResults.isNotEmpty()) {
                    SearchResultsCard {
                        items(movieResults) { movie ->
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
                                modifier = Modifier.clickable { onMovieResultSelected(movie) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WriteTagConfirmContent(
    tagType: TagType,
    selectedAlbum: DiscogsSearchResult?,
    selectedMovie: TmdbMovieResult?,
    isLoadingFirstTrack: Boolean,
    isLoadingStreaming: Boolean,
    streamingResult: MovieStreamingResult?,
    region: String,
    url: String,
    writeAs: WriteAsOption,
    onWriteAsChange: (WriteAsOption) -> Unit,
    writeError: String?,
    onWriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLoading = isLoadingFirstTrack || isLoadingStreaming
    var showWriteAsHelp by remember { mutableStateOf(false) }
    var showUrlPreview by remember { mutableStateOf(false) }

    if (showWriteAsHelp) {
        AlertDialog(
            onDismissRequest = { showWriteAsHelp = false },
            title = { Text("Writing Options") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text(
                            "Tangible URI",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "The 'smart' option. Stores IDs for multiple services. When scanned, Tangible chooses your preferred service and player automatically.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column {
                        Text(
                            "Direct Service URL",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "The 'universal' option. Writes a standard link (e.g., spotify.com). Works on any phone even without Tangible installed.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWriteAsHelp = false }) { Text("Got it") }
            }
        )
    }

    if (showUrlPreview) {
        AlertDialog(
            onDismissRequest = { showUrlPreview = false },
            title = { Text("Encoded URL") },
            text = {
                Text(
                    text = url.ifBlank { "No URL generated yet." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showUrlPreview = false }) { Text("Close") }
            }
        )
    }

    Column(
        modifier = modifier.padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        if (tagType == TagType.MUSIC) "Looking up streaming links…" else "Fetching streaming availability in $region…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val imageUrl = when (tagType) {
                    TagType.MUSIC -> selectedAlbum?.displayImage
                    TagType.MOVIE -> selectedMovie?.largePosterUrl
                }
                val title = when (tagType) {
                    TagType.MUSIC -> selectedAlbum?.albumName ?: ""
                    TagType.MOVIE -> selectedMovie?.title ?: ""
                }
                val subtitle = when (tagType) {
                    TagType.MUSIC -> selectedAlbum?.artistName ?: ""
                    TagType.MOVIE -> selectedMovie?.year ?: ""
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(280.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (tagType == TagType.MOVIE) {
                        when {
                            streamingResult != null && streamingResult.options.isEmpty() ->
                                Text(
                                    "Not available for streaming in $region.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            streamingResult != null ->
                                StreamingServicesCard(streamingResult, region)
                        }
                    }
                }
            }
        }

        if (writeError != null) {
            Text(
                text = writeError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tagType == TagType.MUSIC) {
                    WriteAsDropdown(
                        writeAs = writeAs,
                        onWriteAsChange = onWriteAsChange,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showWriteAsHelp = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Help",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                IconButton(onClick = { showUrlPreview = true }) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "View URL",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Button(
                onClick = onWriteClick,
                enabled = url.isNotBlank() && !isLoading,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Write Tag")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WriteAsDropdown(
    writeAs: WriteAsOption,
    onWriteAsChange: (WriteAsOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = when (writeAs) {
                WriteAsOption.TANGIBLE -> "Tangible URI"
                WriteAsOption.SPOTIFY -> "Spotify URL"
                WriteAsOption.YOUTUBE_MUSIC -> "YouTube Music URL"
                WriteAsOption.APPLE_MUSIC -> "Apple Music URL"
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
                text = { Text("Tangible URI") },
                onClick = { onWriteAsChange(WriteAsOption.TANGIBLE); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Spotify URL") },
                onClick = { onWriteAsChange(WriteAsOption.SPOTIFY); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("YouTube Music URL") },
                onClick = { onWriteAsChange(WriteAsOption.YOUTUBE_MUSIC); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("Apple Music URL") },
                onClick = { onWriteAsChange(WriteAsOption.APPLE_MUSIC); expanded = false }
            )
        }
    }
}

@Composable
private fun StreamingServicesCard(result: MovieStreamingResult, region: String) {
    val countryName = REGIONS.find { it.first == region }?.second ?: region
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Availability in $countryName",
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
                        Spacer(modifier = Modifier.size(16.dp))
                    }
                    val typeLabel = option.allTypes.joinToString(", ") { type ->
                        type.replaceFirstChar { it.uppercase() }
                    }
                    val statusText = if (!encoded) " (tag full — use NTAG216+)" else ""
                    val displayText = if (option.serviceId == "stremio") {
                        option.serviceName + statusText
                    } else {
                        "${option.serviceName} ($typeLabel)$statusText"
                    }
                    Text(
                        text = displayText,
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
        WriteTagScreen(onBackClick = {}, onWriteClick = {})
    }
}
