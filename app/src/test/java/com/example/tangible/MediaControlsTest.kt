package com.example.tangible

import android.media.session.PlaybackState
import com.example.tangible.data.PlayerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaControlsTest {

    private fun status(state: String = "playing", title: String? = "Song", durationSec: Double? = 200.0) = PlayerStatus(
        entityId = "media_player.a", name = "A", state = state, title = title,
        durationSec = durationSec, positionSec = 10.0, positionUpdatedAtMs = 1_000
    )

    @Test
    fun `a playing track reports its position as of now`() {
        val state = playbackStateOf(status(), isStarting = false, nowMs = 6_000, elapsedRealtimeMs = 42)

        assertEquals(PlaybackState.STATE_PLAYING, state.state)
        assertEquals(15_000, state.position)
        assertEquals(1f, state.playbackSpeed)
        assertEquals(42, state.lastPositionUpdateTime)
        assertTrue(state.actions and PlaybackState.ACTION_SEEK_TO != 0L)
    }

    @Test
    fun `a Sonos paused as idle is paused and stays where it stopped`() {
        val state = playbackStateOf(status(state = "idle"), isStarting = false, nowMs = 6_000, elapsedRealtimeMs = 42)

        assertEquals(PlaybackState.STATE_PAUSED, state.state)
        assertEquals(10_000, state.position)
        assertEquals(0f, state.playbackSpeed)
    }

    @Test
    fun `an album that is starting is buffering and can't be seeked`() {
        val state = playbackStateOf(status(state = "idle"), isStarting = true, nowMs = 6_000, elapsedRealtimeMs = 42)

        assertEquals(PlaybackState.STATE_BUFFERING, state.state)
        assertEquals(0L, state.actions and PlaybackState.ACTION_SEEK_TO)
    }

    @Test
    fun `controls show while a track is loaded or an album is starting`() {
        assertTrue(shouldShowMediaControls(status(), isStarting = false))
        assertTrue(shouldShowMediaControls(status(state = "idle"), isStarting = false))
        assertTrue(shouldShowMediaControls(status(state = "off"), isStarting = true))
        assertFalse(shouldShowMediaControls(status(state = "off"), isStarting = false))
        assertFalse(shouldShowMediaControls(status(title = null), isStarting = true))
        assertFalse(shouldShowMediaControls(null, isStarting = false))
    }
}
