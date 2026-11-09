package com.example.tangible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tangible.data.MusicAssistantPlayer
import com.example.tangible.data.PlayerStatus
import kotlinx.coroutines.delay

/** Full-screen remote for the Music Assistant player Tangible is controlling. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playerId: String?,
    status: PlayerStatus?,
    players: List<MusicAssistantPlayer>,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Double) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSwitchPlayer: (String) -> Unit,
    onRefreshPlayers: () -> Unit,
    isStarting: Boolean = false,
    isTransferring: Boolean = false,
    error: String? = null,
    clock: () -> Long = System::currentTimeMillis
) {
    var showPlayerPicker by remember { mutableStateOf(false) }
    val now = rememberTicker(status?.isPlaying == true, clock)
    val playerName = status?.name ?: players.find { it.entityId == playerId }?.name ?: playerId ?: "No player"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to TangiblePurpleDark.copy(alpha = 0.45f),
                    0.55f to TangibleBackground
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                PlayerChip(
                    name = playerName,
                    isBusy = isTransferring,
                    onClick = {
                        onRefreshPlayers()
                        showPlayerPicker = true
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(48.dp))
            }

            // Artwork and track info share the space between the header and the controls; the
            // artwork shrinks on short screens so the controls always stay visible.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
            Artwork(
                url = status?.artworkUrl?.takeIf { status.hasMedia },
                modifier = Modifier
                    .weight(1f, fill = false)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
            )

            Spacer(modifier = Modifier.height(28.dp))

            when {
                playerId == null -> Message("Pick a player in Settings → Music Assistant")
                status == null && error == null -> {
                    CircularProgressIndicator(color = TangiblePurple, modifier = Modifier.padding(24.dp))
                }
                status != null && !status.isAvailable -> Message("$playerName is unavailable")
                status != null && !status.hasMedia && !isStarting -> {
                    Message("Nothing playing on $playerName.\nScan a tag to start an album.")
                }
                else -> TrackInfo(status)
            }
            }

            val canControl = status != null && status.isAvailable && status.hasMedia
            ProgressBar(status = status, now = now, enabled = canControl, onSeek = onSeek)

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, enabled = canControl, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(40.dp))
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = canControl && !isStarting,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = TangiblePurple)
                ) {
                    if (isStarting) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                    } else {
                        Icon(
                            imageVector = if (status?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (status?.isPlaying == true) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                IconButton(onClick = onNext, enabled = canControl, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(40.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            VolumeBar(
                volume = status?.volume,
                enabled = status != null && status.isAvailable && status.volume != null,
                onVolumeChange = onVolumeChange
            )

            Box(modifier = Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showPlayerPicker) {
        PlayerPickerSheet(
            players = players,
            currentId = playerId,
            movesPlayback = status?.isActive == true,
            onDismiss = { showPlayerPicker = false },
            onSelect = { id ->
                showPlayerPicker = false
                onSwitchPlayer(id)
            }
        )
    }
}

/** Compact bar shown above the bottom navigation while something is playing. */
@Composable
fun MiniPlayer(
    status: PlayerStatus,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    clock: () -> Long = System::currentTimeMillis
) {
    val now = rememberTicker(status.isPlaying, clock)
    val progress = progressFraction(status, now)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("mini_player"),
        color = TangibleSurfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(url = status.artworkUrl, modifier = Modifier.size(44.dp), cornerRadius = 8)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = status.title.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(status.artist, status.name).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (status.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (status.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress ?: 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = TangiblePurple,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
private fun PlayerChip(name: String, isBusy: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBusy) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = TangiblePurple)
            } else {
                Icon(Icons.Default.Speaker, contentDescription = null, tint = TangiblePurple, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch player")
        }
    }
}

@Composable
private fun Artwork(url: String?, modifier: Modifier = Modifier, cornerRadius: Int = 20) {
    val shape = RoundedCornerShape(cornerRadius.dp)
    Box(
        modifier = modifier
            .shadow(if (cornerRadius > 12) 24.dp else 0.dp, shape)
            .clip(shape)
            .background(TangibleSurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxSize(0.35f)
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun TrackInfo(status: PlayerStatus?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = status?.title ?: "Starting…",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        val subtitle = listOfNotNull(status?.artist, status?.album).joinToString(" — ")
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressBar(status: PlayerStatus?, now: Long, enabled: Boolean, onSeek: (Double) -> Unit) {
    val duration = status?.durationSec?.takeIf { it > 0 }
    val position = status?.positionAt(now)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: progressFraction(status, now) ?: 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                val target = dragFraction
                dragFraction = null
                if (target != null && duration != null) onSeek(target * duration)
            },
            enabled = enabled && duration != null,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = TangiblePurple),
            modifier = Modifier.testTag("progress")
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            val shown = dragFraction?.let { f -> duration?.let { it * f } } ?: position
            Text(formatTime(shown), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Text(formatTime(duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VolumeBar(volume: Float?, enabled: Boolean, onVolumeChange: (Float) -> Unit) {
    var dragVolume by remember { mutableStateOf<Float?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = dragVolume ?: volume ?: 0f,
            onValueChange = { dragVolume = it },
            onValueChangeFinished = {
                dragVolume?.let(onVolumeChange)
                dragVolume = null
            },
            enabled = enabled,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White.copy(alpha = 0.8f)),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .testTag("volume")
        )
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerPickerSheet(
    players: List<MusicAssistantPlayer>,
    currentId: String?,
    movesPlayback: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TangibleSurface
    ) {
        Text(
            text = if (movesPlayback) "Move playback to" else "Show player",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        PlayerPickerList(players = players, currentId = currentId, onSelect = onSelect)
    }
}

/** The players in the picker sheet; available ones first, unavailable ones dimmed. */
@Composable
internal fun PlayerPickerList(
    players: List<MusicAssistantPlayer>,
    currentId: String?,
    onSelect: (String) -> Unit
) {
    val sorted = players.sortedBy { it.state == "unavailable" }
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        sorted.forEach { player ->
            val available = player.state != "unavailable"
            ListItem(
                headlineContent = { Text(player.name) },
                supportingContent = { Text(player.state.replaceFirstChar { it.uppercase() }) },
                leadingContent = { Icon(Icons.Default.Speaker, contentDescription = null) },
                trailingContent = {
                    if (player.entityId == currentId) Icon(Icons.Default.Check, contentDescription = "Current", tint = TangiblePurple)
                },
                colors = if (available) {
                    ListItemDefaults.colors(containerColor = Color.Transparent)
                } else {
                    val dim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = dim,
                        supportingColor = dim,
                        leadingIconColor = dim
                    )
                },
                modifier = Modifier
                    .clickable(enabled = available && player.entityId != currentId) { onSelect(player.entityId) }
                    .padding(horizontal = 8.dp)
            )
        }
    }
}

/** Re-reads the clock every half second while playing, so the position moves between polls. */
@Composable
private fun rememberTicker(isPlaying: Boolean, clock: () -> Long): Long {
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(isPlaying) {
        now = clock()
        while (isPlaying) {
            delay(500)
            now = clock()
        }
    }
    return now
}

private fun progressFraction(status: PlayerStatus?, now: Long): Float? {
    val duration = status?.durationSec?.takeIf { it > 0 } ?: return null
    val position = status.positionAt(now) ?: return null
    return (position / duration).toFloat().coerceIn(0f, 1f)
}

internal fun formatTime(seconds: Double?): String {
    if (seconds == null || seconds < 0) return "--:--"
    val total = seconds.toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
}
