package com.example.tangible.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import com.example.tangible.SavedTag
import com.example.tangible.data.MovieProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `MainScreen shows empty state when no tags`() {
        composeTestRule.setContent {
            MainScreen(
                tags = emptyList(),
                selectedTagIds = emptySet(),
                onPlay = {},
                onTagClick = {},
                onTagLongClick = {}
            )
        }

        composeTestRule.onNodeWithText("No tags yet.\nTap + to write your first tag.").assertIsDisplayed()
    }

    @Test
    fun `MainScreen shows tags when list is not empty`() {
        val tags = listOf(
            SavedTag("1", "Song Title", "Artist Name", "MUSIC", "url1"),
            SavedTag("2", "Another Song", "Director", "MUSIC", "url2")
        )

        composeTestRule.setContent {
            MainScreen(
                tags = tags,
                selectedTagIds = emptySet(),
                onPlay = {},
                onTagClick = {},
                onTagLongClick = {}
            )
        }

        composeTestRule.onNodeWithText("Song Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Artist Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Another Song").assertIsDisplayed()
        composeTestRule.onNodeWithText("Director").assertIsDisplayed()
    }

    @Test
    fun `TagDetailsModal for Movie only shows enabled and written services`() {
        val movieProviders = listOf(
            MovieProvider("netflix", "Netflix", "url1", isEnabled = true),
            MovieProvider("prime", "Prime Video", "url2", isEnabled = false),
            MovieProvider("disney", "Disney+", "url3", isEnabled = true)
        )
        // tangible://movie?tmdb_id=123&r=US&nf=s:456&pv=r:789 -> netflix and prime written
        val tag = SavedTag("1", "Inception", "Christopher Nolan", "MOVIE", "tangible://movie?tmdb_id=123&r=US&nf=s:456&pv=r:789")

        composeTestRule.setContent {
            TagDetailsModal(
                tag = tag,
                onDismiss = {},
                onUpdate = {},
                onDelete = {},
                chromecastDevices = emptyList(),
                movieProviders = movieProviders
            )
        }

        // Expand Service dropdown (initially shows "Default")
        composeTestRule.onAllNodesWithText("Default")[0].performClick()

        // Only Netflix should be shown.
        // Prime is disabled.
        // Disney is enabled but NOT in the tag.
        composeTestRule.onNodeWithText("Netflix (Subscription)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Prime Video").assertDoesNotExist()
        composeTestRule.onNodeWithText("Prime Video (Rent)").assertDoesNotExist()
        composeTestRule.onNodeWithText("Disney+").assertDoesNotExist()
    }

    @Test
    fun `TagDetailsModal for Music hides service and lists Music Assistant players`() {
        val tag = SavedTag("1", "Kind of Blue", "Miles Davis", "MUSIC", "tangible://play?spotify_album=abc")
        val players = listOf(
            com.example.tangible.data.MusicAssistantPlayer("media_player.kitchen", "Kitchen"),
            com.example.tangible.data.MusicAssistantPlayer("media_player.whole_house", "Whole House")
        )

        composeTestRule.setContent {
            TagDetailsModal(
                tag = tag,
                onDismiss = {},
                onUpdate = {},
                onDelete = {},
                chromecastDevices = emptyList(),
                movieProviders = emptyList(),
                maPlayers = players
            )
        }

        composeTestRule.onNodeWithText("Service").assertDoesNotExist()
        composeTestRule.onNodeWithText("Default").performClick()
        composeTestRule.onNodeWithText("Kitchen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Whole House").assertIsDisplayed()
        composeTestRule.onNodeWithText("This device").assertDoesNotExist()
    }

    @Test
    fun `NFC warning banner shows only when tag launch is blocked and Allow calls back`() {
        var fixClicks = 0
        val blocked = mutableStateOf(true)

        composeTestRule.setContent {
            MainScreen(
                tags = emptyList(),
                selectedTagIds = emptySet(),
                onPlay = {},
                onTagClick = {},
                onTagLongClick = {},
                isNfcTagLaunchBlocked = blocked.value,
                onFixNfcTagLaunch = { fixClicks++ }
            )
        }

        composeTestRule.onNodeWithText("NFC tags can't open Tangible").assertIsDisplayed()
        composeTestRule.onNodeWithText("Allow").performClick()
        assert(fixClicks == 1)

        blocked.value = false
        composeTestRule.onNodeWithText("NFC tags can't open Tangible").assertDoesNotExist()
        composeTestRule.onNodeWithText("No tags yet.\nTap + to write your first tag.").assertIsDisplayed()
    }
}
