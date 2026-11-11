package com.jjmluz.tangible.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.style.TextOverflow
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
import com.jjmluz.tangible.data.AlbumSearchResult
import com.jjmluz.tangible.data.MovieOption
import com.jjmluz.tangible.data.MovieSearchResult
import com.jjmluz.tangible.data.MovieService
import com.jjmluz.tangible.data.MovieTag
import com.jjmluz.tangible.data.MusicTag
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
    writeSucceeded: Boolean = false,
    onDone: () -> Unit = {},
    initialType: TagType = TagType.MUSIC,
    onTagInfoUpdate: (TagWriteInfo?) -> Unit,
    onWriteClick: (TagWriteInfo) -> Unit,
    onSearchAlbums: suspend (String) -> Result<List<AlbumSearchResult>> = { Result.success(emptyList()) },
    onSearchMovies: suspend (String) -> Result<List<MovieSearchResult>> = { Result.success(emptyList()) }
) {
    var tagType by remember { mutableStateOf(initialType) }

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
    var movieSearchError by remember { mutableStateOf<String?>(null) }
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
            val result = onSearchMovies(movieSearchQuery)
            movieSearchResults = result.getOrDefault(emptyList())
            movieSearchError = result.exceptionOrNull()?.message
            isSearchingMovies = false
        } else {
            movieSearchResults = emptyList()
            movieSearchError = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Write tag",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
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
                writeSucceeded = writeSucceeded,
                onDone = onDone,
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
                movieSearchError = movieSearchError,
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
    movieSearchError: String?,
    onMovieResultSelected: (MovieSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        // imePadding keeps the last results above the keyboard.
        modifier = modifier
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // The same Music/Movies switch as the collection.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TypeButton(
                label = "Music",
                icon = Icons.Default.MusicNote,
                selected = tagType == TagType.MUSIC,
                isFirst = true,
                onClick = { onTagTypeChange(TagType.MUSIC) },
                modifier = Modifier.weight(1f)
            )
            TypeButton(
                label = "Movies",
                icon = Icons.Default.Movie,
                selected = tagType == TagType.MOVIE,
                isFirst = false,
                onClick = { onTagTypeChange(TagType.MOVIE) },
                modifier = Modifier.weight(1f)
            )
        }

        when (tagType) {
            TagType.MUSIC -> {
                SearchField(
                    query = musicSearchQuery,
                    onQueryChange = onMusicSearchQueryChange,
                    placeholder = "Search artist or album",
                    isSearching = isSearchingMusic
                )
                if (musicSearchError != null) {
                    Text(
                        musicSearchError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                if (musicResults.isNotEmpty()) {
                    SearchResults(musicResults) { index, result ->
                        SearchResultRow(
                            title = result.name,
                            subtitle = listOf(result.artistName, result.version).filter { it.isNotBlank() }.joinToString(" · "),
                            imageUrl = result.imageUrl,
                            isPoster = false,
                            shape = groupedRowShape(index, musicResults.size),
                            onClick = { onMusicResultSelected(result) }
                        )
                    }
                } else if (musicSearchError == null && !isSearchingMusic) {
                    SearchHint("Find the album, then hold a tag to the phone to write it.")
                }
            }
            TagType.MOVIE -> {
                SearchField(
                    query = movieSearchQuery,
                    onQueryChange = onMovieSearchQueryChange,
                    placeholder = "Search movie",
                    isSearching = isSearchingMovies
                )
                if (movieSearchError != null) {
                    Text(
                        movieSearchError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                if (movieResults.isNotEmpty()) {
                    SearchResults(movieResults) { index, movie ->
                        SearchResultRow(
                            title = movie.title,
                            subtitle = movie.year,
                            imageUrl = movie.thumbUrl,
                            isPoster = true,
                            shape = groupedRowShape(index, movieResults.size),
                            onClick = { onMovieResultSelected(movie) }
                        )
                    }
                } else if (movieSearchError == null && !isSearchingMovies) {
                    SearchHint("Find the movie, then hold a tag to the phone to write it.")
                }
            }
        }
    }
}

/** A pill-shaped search box in a tonal fill, with no outline or underline. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    isSearching: Boolean
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            } else if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Results as one group of tonal rows, like the collection's list. */
@Composable
private fun <T> ColumnScope.SearchResults(results: List<T>, row: @Composable (Int, T) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        itemsIndexed(results) { index, result -> row(index, result) }
    }
}

@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String,
    imageUrl: String?,
    isPoster: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(60.dp), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = (if (isPoster) Modifier.size(width = 40.dp, height = 60.dp) else Modifier.size(60.dp))
                    .clip(RoundedCornerShape(if (isPoster) 8.dp else 14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentScale = ContentScale.Crop
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SearchHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    )
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
    isWriting: Boolean = false,
    writeSucceeded: Boolean = false,
    onDone: () -> Unit = {}
) {
    val result = when {
        writeSucceeded -> WriteResult.SUCCESS
        writeError != null -> WriteResult.FAILURE
        else -> null
    }
    val resultColors = result?.let { writeResultColors(it) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(result) {
        when (result) {
            WriteResult.SUCCESS -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            WriteResult.FAILURE -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
            null -> Unit
        }
    }

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
                // The padding leaves room for the result glow around the cover; a scrolling
                // column clips what's drawn above and below it.
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 56.dp),
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
                ResultCover(result = result, colors = resultColors, modifier = imageModifier) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        // A failed write drains the cover's colour.
                        colorFilter = if (result == WriteResult.FAILURE) {
                            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.3f) })
                        } else null
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
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
                                "This movie isn't on any of the services you picked in Settings → Streaming services, so it can't be written to a tag.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        else -> MovieServicePicker(movieOptions, selectedService, onServiceSelected)
                    }
                }
            }
        }

        if (result != null && resultColors != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (result == WriteResult.SUCCESS) "Tag written. Scan it to play." else "Couldn't write the tag",
                    color = resultColors.accent,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                if (result == WriteResult.FAILURE && writeError != null) {
                    Text(
                        text = writeError,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (result != WriteResult.SUCCESS) Row(
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

            if (result == WriteResult.SUCCESS && resultColors != null) {
                Button(
                    onClick = onDone,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = resultColors.accent,
                        contentColor = resultColors.onAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Done")
                }
            } else if (isWriting) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(28.dp),
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
                    shape = RoundedCornerShape(28.dp),
                    colors = if (resultColors != null) {
                        ButtonDefaults.buttonColors(
                            containerColor = resultColors.accent,
                            contentColor = resultColors.onAccent
                        )
                    } else ButtonDefaults.buttonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(if (writeError != null) "Try again" else "Write tag")
                }
            }
        }
    }
}

private enum class WriteResult { SUCCESS, FAILURE }

/** [accent] colours the ring, badge, message and button; [glow] is the light behind the cover. */
private class WriteResultColors(val accent: Color, val onAccent: Color, val glow: Color)

/**
 * Green and red are fixed rather than taken from the system palette, so they mean success and
 * failure whatever the wallpaper is. Failure uses the theme's error colour.
 */
@Composable
private fun writeResultColors(result: WriteResult): WriteResultColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (result) {
        WriteResult.SUCCESS -> if (dark) {
            WriteResultColors(accent = Color(0xFF7EDB8B), onAccent = Color(0xFF00390F), glow = Color(0xFF7EDB8B))
        } else {
            WriteResultColors(accent = Color(0xFF1F6C34), onAccent = Color.White, glow = Color(0xFF3FBF5F))
        }
        WriteResult.FAILURE -> WriteResultColors(
            accent = MaterialTheme.colorScheme.error,
            onAccent = MaterialTheme.colorScheme.onError,
            glow = Color(0xFFFF6B5E)
        )
    }
}

/**
 * The cover on the confirm screen. After a write it gets a green or red glow behind it, a ring
 * in the same colour, and a check or cross badge on its corner.
 */
@Composable
private fun ResultCover(
    result: WriteResult?,
    colors: WriteResultColors?,
    modifier: Modifier = Modifier,
    cover: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    val glowAlpha by animateFloatAsState(
        targetValue = if (result != null) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "resultGlow"
    )
    // Keeps the last colour while the glow fades out after the result is cleared.
    var lastGlow by remember { mutableStateOf(Color.Transparent) }
    var lastAccent by remember { mutableStateOf(Color.Transparent) }
    if (colors != null) {
        lastGlow = colors.glow
        lastAccent = colors.accent
    }
    Box(
        modifier = modifier.drawBehind {
            if (glowAlpha > 0f) {
                val radius = size.maxDimension / 2 + 64.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to lastGlow.copy(alpha = 0.75f * glowAlpha),
                        0.6f to lastGlow.copy(alpha = 0.4f * glowAlpha),
                        1f to lastGlow.copy(alpha = 0f),
                        center = center,
                        radius = radius
                    ),
                    radius = radius
                )
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .border(3.dp, lastAccent.copy(alpha = glowAlpha), shape)
        ) {
            cover()
        }
        AnimatedVisibility(
            visible = result != null,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 14.dp, y = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(4.dp)
                    .background(lastAccent, CircleShape)
                    .testTag("write_result_badge"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (result == WriteResult.FAILURE) Icons.Default.Close else Icons.Default.Check,
                    contentDescription = if (result == WriteResult.FAILURE) "Failed" else "Success",
                    tint = colors?.onAccent ?: Color.Transparent,
                    modifier = Modifier.size(32.dp)
                )
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

@Preview(showBackground = true)
@Composable
fun WriteTagScreenPreview() {
    MaterialTheme {
        WriteTagScreen(onBackClick = {}, onTagInfoUpdate = {}, onWriteClick = {})
    }
}
