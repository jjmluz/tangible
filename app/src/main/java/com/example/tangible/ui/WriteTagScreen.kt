package com.example.tangible.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tangible.data.AlbumSearchResult
import com.example.tangible.data.MovieOption
import com.example.tangible.data.MovieSearchResult
import com.example.tangible.data.MovieService
import com.example.tangible.data.MovieTag
import com.example.tangible.data.MusicTag
import kotlinx.coroutines.delay

enum class TagType { MUSIC, MOVIE }

data class TagWriteInfo(
    val title: String,
    val subtitle: String,
    val type: TagType,
    val imageUrl: String?,
    val url: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteTagScreen(
    onBackClick: () -> Unit,
    isWriting: Boolean = false,
    writeError: String? = null,
    onTagInfoUpdate: (TagWriteInfo?) -> Unit,
    onWriteClick: (TagWriteInfo) -> Unit,
    onSearchAlbums: suspend (String) -> Result<List<AlbumSearchResult>> = { Result.success(emptyList()) },
    onSearchMovies: suspend (String) -> List<MovieSearchResult> = { emptyList() }
) {
    var tagType by remember { mutableStateOf(TagType.MUSIC) }

    // Music state
    var url by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<AlbumSearchResult>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedAlbum by remember { mutableStateOf<AlbumSearchResult?>(null) }

    // Movie state
    var movieSearchQuery by remember { mutableStateOf("") }
    var movieSearchResults by remember { mutableStateOf<List<MovieSearchResult>>(emptyList()) }
    var isSearchingMovies by remember { mutableStateOf(false) }
    var selectedMovie by remember { mutableStateOf<MovieSearchResult?>(null) }
    var selectedService by remember { mutableStateOf<MovieService?>(null) }

    val showConfirmScreen = when (tagType) {
        TagType.MUSIC -> selectedAlbum != null
        TagType.MOVIE -> selectedMovie != null
    }

    // Tag Info for writing
    val currentTagInfo = remember(tagType, selectedAlbum, selectedMovie, url, showConfirmScreen) {
        if (!showConfirmScreen || url.isBlank()) null
        else {
            val title = when (tagType) {
                TagType.MUSIC -> selectedAlbum?.name ?: ""
                TagType.MOVIE -> selectedMovie?.title ?: ""
            }
            val subtitle = when (tagType) {
                TagType.MUSIC -> selectedAlbum?.artistName ?: ""
                TagType.MOVIE -> selectedMovie?.year ?: ""
            }
            val imageUrl = when (tagType) {
                TagType.MUSIC -> selectedAlbum?.imageUrl
                TagType.MOVIE -> selectedMovie?.posterUrl
            }
            TagWriteInfo(title = title, subtitle = subtitle, type = tagType, imageUrl = imageUrl, url = url)
        }
    }

    LaunchedEffect(currentTagInfo) {
        onTagInfoUpdate(currentTagInfo)
    }

    // Reset state when switching tag types
    LaunchedEffect(tagType) {
        url = ""
        selectedAlbum = null
        selectedMovie = null
        selectedService = null
        if (tagType == TagType.MUSIC) {
            movieSearchQuery = ""
            movieSearchResults = emptyList()
        } else {
            searchQuery = ""
            searchResults = emptyList()
        }
    }

    // The tag's URL
    LaunchedEffect(tagType, selectedAlbum, selectedMovie, selectedService) {
        url = when (tagType) {
            TagType.MUSIC -> selectedAlbum?.let { MusicTag.build(it.uri) } ?: ""
            TagType.MOVIE -> {
                val movie = selectedMovie
                val option = movie?.options?.find { it.service == selectedService }
                if (movie != null && option != null) MovieTag.build(movie.tmdbId, option) else ""
            }
        }
    }

    // Music search debouncing
    LaunchedEffect(searchQuery) {
        if (searchQuery.length > 2) {
            isSearching = true
            delay(500)
            val result = onSearchAlbums(searchQuery)
            searchResults = result.getOrDefault(emptyList())
            searchError = result.exceptionOrNull()?.let { "Couldn't search Music Assistant: ${it.message}" }
            isSearching = false
        } else {
            searchResults = emptyList()
            searchError = null
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
                            url = ""
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TangibleBackground)
            )
        },
        containerColor = TangibleBackground
    ) { padding ->
        if (showConfirmScreen) {
            WriteTagConfirmContent(
                tagType = tagType,
                selectedAlbum = selectedAlbum,
                selectedMovie = selectedMovie,
                movieOptions = selectedMovie?.options,
                selectedService = selectedService,
                onServiceSelected = { selectedService = it },
                url = url,
                writeError = writeError,
                isWriting = isWriting,
                onWriteClick = {
                    currentTagInfo?.let { onWriteClick(it) }
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
                isSearchingMusic = isSearching,
                musicResults = searchResults,
                musicSearchError = searchError,
                onMusicResultSelected = { selectedAlbum = it },
                movieSearchQuery = movieSearchQuery,
                onMovieSearchQueryChange = { movieSearchQuery = it },
                isSearchingMovies = isSearchingMovies,
                movieResults = movieSearchResults,
                onMovieResultSelected = { movie ->
                    selectedMovie = movie
                    // The most preferred service is preselected.
                    selectedService = movie.options.firstOrNull()?.service
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
    musicResults: List<AlbumSearchResult>,
    musicSearchError: String?,
    onMusicResultSelected: (AlbumSearchResult) -> Unit,
    movieSearchQuery: String,
    onMovieSearchQueryChange: (String) -> Unit,
    isSearchingMovies: Boolean,
    movieResults: List<MovieSearchResult>,
    onMovieResultSelected: (MovieSearchResult) -> Unit,
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
                selected = tagType == TagType.MOVIE,
                onClick = { onTagTypeChange(TagType.MOVIE) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = 1,
                    count = 2,
                    baseShape = RoundedCornerShape(8.dp)
                ),
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
                if (musicSearchError != null) {
                    Text(
                        musicSearchError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (musicResults.isNotEmpty()) {
                    SearchResultsCard {
                        items(musicResults) { result ->
                            ListItem(
                                headlineContent = { Text(result.name) },
                                supportingContent = {
                                    val details = listOf(result.artistName, result.version).filter { it.isNotBlank() }
                                    if (details.isNotEmpty()) Text(details.joinToString(" · "))
                                },
                                leadingContent = {
                                    AsyncImage(
                                        model = result.imageUrl,
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
                                        model = movie.thumbUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .width(40.dp)
                                            .height(60.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
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

@Composable
private fun WriteTagConfirmContent(
    tagType: TagType,
    selectedAlbum: AlbumSearchResult?,
    selectedMovie: MovieSearchResult?,
    movieOptions: List<MovieOption>?,
    selectedService: MovieService?,
    onServiceSelected: (MovieService) -> Unit,
    url: String,
    writeError: String?,
    onWriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    isWriting: Boolean = false
) {
    var showUrlPreview by remember { mutableStateOf(false) }

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
            val imageUrl = when (tagType) {
                TagType.MUSIC -> selectedAlbum?.imageUrl
                TagType.MOVIE -> selectedMovie?.posterUrl
            }
            val title = when (tagType) {
                TagType.MUSIC -> selectedAlbum?.name ?: ""
                TagType.MOVIE -> selectedMovie?.title ?: ""
            }
            val subtitle = when (tagType) {
                TagType.MUSIC -> selectedAlbum?.artistName ?: ""
                TagType.MOVIE -> selectedMovie?.year ?: ""
            }

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val imageModifier = if (tagType == TagType.MOVIE) {
                    Modifier
                        .width(240.dp)
                        .height(360.dp)
                } else {
                    Modifier.size(280.dp)
                }
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = imageModifier.clip(RoundedCornerShape(12.dp)),
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
                        movieOptions == null -> Unit
                        movieOptions.isEmpty() ->
                            Text(
                                "Couldn't find this movie on your streaming services or for Stremio, so it can't be written to a tag.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        else -> MovieServicePicker(movieOptions, selectedService, onServiceSelected)
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
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { showUrlPreview = true }) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "View URL",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isWriting) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Hold tag near device...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            } else {
                Button(
                    onClick = onWriteClick,
                    enabled = url.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(if (writeError != null) "Retry Writing" else "Write Tag")
                }
            }
        }
    }
}

/** Picks the streaming service the tag opens the movie in. */
@Composable
private fun MovieServicePicker(
    options: List<MovieOption>,
    selected: MovieService?,
    onSelected: (MovieService) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                "Open in",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option.service == selected,
                                onClick = { onSelected(option.service) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option.service == selected, onClick = null)
                        Text(
                            text = option.service.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
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
        WriteTagScreen(onBackClick = {}, onTagInfoUpdate = {}, onWriteClick = {})
    }
}
