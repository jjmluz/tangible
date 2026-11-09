package com.example.tangible

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tangible.data.HomeAssistantRepository
import com.example.tangible.data.PlayerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Keeps track of the Music Assistant player Tangible is showing, polls its state through Home
 * Assistant while the app is visible, and sends playback controls.
 *
 * Home Assistant accepts a command before the speaker has acted on it (resuming a Sonos can take a
 * second or two), so a command shows its result right away and then keeps showing it until the
 * player reports the same thing, polling quickly in the meantime. If the player never gets there,
 * the real state is shown after a timeout.
 */
/** What Tangible knows about an album from its tag, shown while the album starts. */
data class AlbumPreview(val title: String, val artist: String?, val artworkUrl: String?)

class NowPlayingController(
    private val repository: HomeAssistantRepository,
    private val scope: CoroutineScope,
    private val settings: () -> Pair<String, String>,
    private val onPlayerChanged: (String) -> Unit = {},
    private val pollIntervalMs: Long = 2000,
    private val settleDelayMs: Long = 700,
    private val confirmPollMs: Long = 300,
    private val confirmTimeoutMs: Long = 5000,
    private val startTimeoutMs: Long = 15000,
    private val clock: () -> Long = System::currentTimeMillis
) {
    var playerId by mutableStateOf<String?>(null)
        private set
    var status by mutableStateOf<PlayerStatus?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    // A failed status read clears itself on the next good read; a failed command's error stays
    // until the next action, so it's still on screen when the real state comes back.
    private var errorIsFromPoll = false
    /** An album was requested and isn't playing on the speaker yet. */
    var isStarting by mutableStateOf(false)
        private set
    var isTransferring by mutableStateOf(false)
        private set

    /** What the last command should lead to; polled states that don't match it yet are ignored. */
    private class Expectation(
        val matches: (PlayerStatus) -> Boolean,
        val deadlineMs: Long,
        val isStart: Boolean = false
    )

    private var expectation: Expectation? = null

    /**
     * Where the current track stopped. While a track is paused, Music Assistant reports unreliable
     * positions (a moment after pausing it can send where playback last started, e.g. 0:14 instead
     * of 0:36), so Tangible works out the stop position itself and holds it until the track plays
     * again or changes.
     */
    private data class StoppedAt(val trackKey: String?, val positionSec: Double)
    private var stoppedAt: StoppedAt? = null
    private var pollJob: Job? = null

    // Bumped around every command, so a poll that was already in flight when the command was sent
    // can't overwrite the state the command asked for.
    private var generation = 0

    fun setPlayer(entityId: String?) {
        if (entityId == playerId) return
        playerId = entityId
        status = null
        error = null
        stoppedAt = null
        clearExpectation()
        entityId?.let(onPlayerChanged)
        refreshSoon()
    }

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                refresh()
                delay(pollIntervalMs)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refreshSoon() {
        scope.launch { refresh() }
    }

    suspend fun refresh() {
        val id = playerId ?: return
        val (url, token) = settings()
        if (url.isBlank() || token.isBlank()) return
        val startedAt = generation
        repository.fetchPlayerStatus(url, token, id)
            .onSuccess { reported ->
                if (startedAt != generation || id != playerId) return@onSuccess
                val fresh = holdStoppedPosition(reported)
                val pending = expectation
                if (pending != null) {
                    if (pending.matches(fresh) || clock() >= pending.deadlineMs) {
                        clearExpectation()
                    } else {
                        return@onSuccess // The speaker hasn't caught up yet; keep showing what was asked for.
                    }
                }
                status = fresh
                if (errorIsFromPoll) error = null
            }
            .onFailure {
                if (id == playerId && (error == null || errorIsFromPoll)) {
                    error = it.message
                    errorIsFromPoll = true
                }
            }
    }

    /** Replaces the position of a stopped track with where it actually stopped, when that's known. */
    private fun holdStoppedPosition(fresh: PlayerStatus): PlayerStatus {
        if (fresh.isPlaying || !fresh.hasMedia) {
            stoppedAt = null
            return fresh
        }
        val held = stoppedAt?.takeIf { it.trackKey == fresh.trackKey }
            ?: stopPositionOf(status, fresh)?.let { StoppedAt(fresh.trackKey, it) }
            ?: return fresh // e.g. the app opened on an already paused track: HA's position is all there is
        stoppedAt = held
        return fresh.copy(positionSec = held.positionSec, positionUpdatedAtMs = clock())
    }

    /** If [previous] was this track playing, where it was when [fresh] says it stopped. */
    private fun stopPositionOf(previous: PlayerStatus?, fresh: PlayerStatus): Double? {
        if (previous == null || !previous.isPlaying || previous.trackKey != fresh.trackKey) return null
        val stoppedAtMs = fresh.stateChangedAtMs
            ?.takeIf { previous.positionUpdatedAtMs == null || it >= previous.positionUpdatedAtMs }
            ?.coerceAtMost(clock())
            ?: clock()
        return previous.positionAt(stoppedAtMs)
    }

    /** Called when a scan or the play button asks Music Assistant to play on [entityId]. */
    fun playbackStarting(entityId: String, preview: AlbumPreview? = null) {
        setPlayer(entityId)
        error = null
        isStarting = true
        // Done once the player is playing something new, or the same track from the top (rescanning
        // the album that's already playing).
        val before = status?.contentId
        // Until then, show the album being started rather than whatever the player last played.
        status = PlayerStatus(
            entityId = entityId,
            name = status?.name ?: entityId,
            state = "idle",
            title = preview?.title,
            artist = preview?.artist,
            artworkUrl = preview?.artworkUrl,
            volume = status?.volume,
            contentId = before
        )
        stoppedAt = null
        expectation = Expectation(
            matches = { it.isPlaying && (it.contentId != before || (it.positionAt(clock()) ?: 0.0) < 5.0) },
            deadlineMs = clock() + startTimeoutMs,
            isStart = true
        )
    }

    fun playbackStarted(result: Result<Unit>) {
        val pending = expectation?.takeIf { it.isStart }
        generation++
        result.onFailure {
            showActionError(it.message)
            if (pending != null) clearExpectation()
            isStarting = false
            refreshSoon()
            return
        }
        if (pending != null) scope.launch { awaitConfirmation(pending) } else isStarting = false
    }

    fun playPause() {
        val current = status ?: return
        val now = clock()
        val wasPlaying = current.isPlaying
        val position = current.positionAt(now)
        // Music Assistant resumes from its last saved position, which can be a second or two before
        // where the track actually stopped, so resume from the stop position Tangible held instead.
        val resumeAt = stoppedAt?.takeIf { !wasPlaying && it.trackKey == current.trackKey }?.positionSec
        // Pausing: this is where the track stops. Resuming: let the player report again.
        stoppedAt = if (wasPlaying && position != null) StoppedAt(current.trackKey, position) else null
        command(
            optimistic = current.copy(
                state = if (wasPlaying) "paused" else "playing",
                positionSec = resumeAt ?: position,
                positionUpdatedAtMs = now
            ),
            expect = { it.isPlaying != wasPlaying }
        ) { url, token, id ->
            when {
                wasPlaying -> repository.pause(url, token, id)
                resumeAt != null -> {
                    // If the seek fails, still resume, just from wherever Music Assistant has it.
                    repository.seek(url, token, id, resumeAt)
                    repository.play(url, token, id)
                }
                else -> repository.play(url, token, id)
            }
        }
    }

    fun next() = skip { url, token, id -> repository.nextTrack(url, token, id) }

    fun previous() = skip { url, token, id -> repository.previousTrack(url, token, id) }

    private fun skip(action: suspend (String, String, String) -> Result<Unit>) {
        val before = status?.contentId
        // Previous on a track that's past its first seconds restarts it instead of changing track.
        command(optimistic = null, expect = { it.contentId != before || (it.positionAt(clock()) ?: 0.0) < 3.0 }, action = action)
    }

    fun seek(positionSec: Double) {
        val current = status ?: return
        if (!current.isPlaying) stoppedAt = StoppedAt(current.trackKey, positionSec)
        command(
            optimistic = current.copy(positionSec = positionSec, positionUpdatedAtMs = clock()),
            expect = { fresh -> fresh.positionAt(clock())?.let { abs(it - positionSec) < 3.0 } ?: false }
        ) { url, token, id ->
            repository.seek(url, token, id, positionSec)
        }
    }

    fun setVolume(level: Float) {
        val current = status ?: return
        command(
            optimistic = current.copy(volume = level, isMuted = false),
            expect = { fresh -> fresh.volume?.let { abs(it - level) < 0.02f } ?: false }
        ) { url, token, id ->
            repository.setVolume(url, token, id, level)
        }
    }

    /**
     * Switches to another player. If something is playing, the queue moves with it; otherwise
     * Tangible just starts showing the other player.
     */
    fun switchTo(targetId: String) {
        val from = playerId
        if (from == null || targetId == from) return
        if (status?.isActive != true) {
            setPlayer(targetId)
            return
        }
        val (url, token) = settings()
        isTransferring = true
        error = null
        scope.launch {
            repository.transferQueue(url, token, from, targetId)
                .onSuccess {
                    setPlayer(targetId)
                    delay(settleDelayMs)
                    refresh()
                }
                .onFailure { showActionError(it.message) }
            isTransferring = false
        }
    }

    private fun command(
        optimistic: PlayerStatus?,
        expect: (PlayerStatus) -> Boolean,
        action: suspend (url: String, token: String, entityId: String) -> Result<Unit>
    ) {
        val id = playerId ?: return
        val (url, token) = settings()
        generation++
        clearExpectation()
        error = null
        optimistic?.let { status = it }
        val pending = Expectation(expect, clock() + confirmTimeoutMs)
        expectation = pending
        scope.launch {
            val result = action(url, token, id)
            generation++
            result.onFailure {
                // Nothing will change on the speaker, so show the real state straight away.
                showActionError(it.message)
                if (expectation === pending) clearExpectation()
                refresh()
                return@launch
            }
            awaitConfirmation(pending)
        }
    }

    /** Polls quickly until the player matches [pending] or it times out. */
    private suspend fun awaitConfirmation(pending: Expectation) {
        while (expectation === pending && clock() < pending.deadlineMs) {
            delay(confirmPollMs)
            refresh()
        }
        if (expectation === pending) {
            clearExpectation()
            refresh()
        }
    }

    private fun showActionError(message: String?) {
        error = message
        errorIsFromPoll = false
    }

    private fun clearExpectation() {
        if (expectation?.isStart == true) isStarting = false
        expectation = null
    }
}
