package com.jjmluz.tangible.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.jjmluz.tangible.data.MusicAssistantPlayer
import com.jjmluz.tangible.data.PlayerStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Phone-sized screen, like a Pixel 8 Pro.
@Config(sdk = [34], qualifiers = "w448dp-h998dp")
class NowPlayingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val playing = PlayerStatus(
        entityId = "media_player.sala", name = "Sala de Jantar", state = "playing",
        title = "Miracle", artist = "CHVRCHES", album = "Hansa Session EP",
        durationSec = 208.0, positionSec = 65.0, positionUpdatedAtMs = 0, volume = 0.3f
    )

    private val players = listOf(
        MusicAssistantPlayer("media_player.sala", "Sala de Jantar", "playing"),
        MusicAssistantPlayer("media_player.kitchen", "Kitchen", "idle")
    )

    @Test
    fun `shows the track and sends controls`() {
        val calls = mutableListOf<String>()
        composeTestRule.setContent {
            NowPlayingScreen(
                playerId = "media_player.sala", status = playing, players = players,
                onClose = { calls += "close" }, onPlayPause = { calls += "playPause" },
                onPrevious = { calls += "previous" }, onNext = { calls += "next" },
                onSeek = {}, onVolumeChange = {}, onSwitchPlayer = { calls += "switch:$it" },
                onRefreshPlayers = { calls += "refresh" },
                clock = { 0L }
            )
        }

        composeTestRule.onNodeWithText("Miracle").assertIsDisplayed()
        composeTestRule.onNodeWithText("CHVRCHES — Hansa Session EP").assertIsDisplayed()
        composeTestRule.onNodeWithText("1:05").assertIsDisplayed()
        composeTestRule.onNodeWithText("3:28").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Pause").performClick()
        // Previous and Next have uneven corners, and Robolectric can't hit-test a tap against
        // that outline, so they get the click action directly.
        composeTestRule.onNodeWithContentDescription("Next").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithContentDescription("Previous").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf("playPause", "next", "previous", "close"), calls)
    }

    @Test
    fun `player chip refreshes players and opens the picker`() {
        var refreshed = false
        composeTestRule.setContent {
            NowPlayingScreen(
                playerId = "media_player.sala", status = playing, players = players,
                onClose = {}, onPlayPause = {}, onPrevious = {}, onNext = {},
                onSeek = {}, onVolumeChange = {}, onSwitchPlayer = {},
                onRefreshPlayers = { refreshed = true },
                clock = { 0L }
            )
        }

        composeTestRule.onNodeWithContentDescription("Switch player").performClick()
        composeTestRule.onNodeWithText("Move playback to").assertIsDisplayed()
        assertEquals(true, refreshed)
    }

    @Test
    fun `picker list switches to an available player only`() {
        var switchedTo: String? = null
        composeTestRule.setContent {
            PlayerPickerList(
                players = players + MusicAssistantPlayer("media_player.office", "Office", "unavailable"),
                currentId = "media_player.sala",
                onSelect = { switchedTo = it }
            )
        }

        composeTestRule.onNodeWithText("Office").performClick()
        assertEquals(null, switchedTo)
        composeTestRule.onNodeWithText("Sala de Jantar").performClick()
        assertEquals(null, switchedTo)
        composeTestRule.onNodeWithText("Kitchen").performClick()
        assertEquals("media_player.kitchen", switchedTo)
    }

    @Test
    fun `idle player shows a hint and disables controls`() {
        composeTestRule.setContent {
            NowPlayingScreen(
                playerId = "media_player.kitchen",
                status = PlayerStatus("media_player.kitchen", "Kitchen", "idle"),
                players = players,
                onClose = {}, onPlayPause = {}, onPrevious = {}, onNext = {},
                onSeek = {}, onVolumeChange = {}, onSwitchPlayer = {}, onRefreshPlayers = {},
                clock = { 0L }
            )
        }

        composeTestRule.onNodeWithText("Nothing playing on Kitchen.\nScan a tag to start an album.").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Play").assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Next").assertIsNotEnabled()
    }

    @Test
    fun `mini player shows the track and toggles playback`() {
        var toggled = false
        var opened = false
        composeTestRule.setContent {
            MiniPlayer(status = playing, onClick = { opened = true }, onPlayPause = { toggled = true }, clock = { 0L })
        }

        composeTestRule.onNodeWithText("Miracle").assertIsDisplayed()
        composeTestRule.onNodeWithText("CHVRCHES · Sala de Jantar").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Pause").performClick()
        composeTestRule.onNodeWithTag("mini_player").performClick()
        assertEquals(true, toggled)
        assertEquals(true, opened)
    }

    @Test
    fun `formatTime handles minutes, hours and unknown`() {
        assertEquals("0:07", formatTime(7.9))
        assertEquals("3:28", formatTime(208.0))
        assertEquals("1:02:03", formatTime(3723.0))
        assertEquals("--:--", formatTime(null))
    }
}
