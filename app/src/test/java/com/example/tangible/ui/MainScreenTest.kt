package com.example.tangible.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
            SavedTag("2", "Movie Title", "Director", "MOVIE", "url2")
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
        composeTestRule.onNodeWithText("Movie Title").assertIsDisplayed()
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
                sonosSpeakers = emptyList(),
                chromecastDevices = emptyList(),
                movieProviders = movieProviders
            )
        }

        // Expand Service dropdown (initially shows "Default")
        composeTestRule.onNodeWithText("Default").performClick()

        // Only Netflix should be shown.
        // Prime is disabled.
        // Disney is enabled but NOT in the tag.
        composeTestRule.onNodeWithText("Netflix (Subscription)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Prime Video").assertDoesNotExist()
        composeTestRule.onNodeWithText("Prime Video (Rent)").assertDoesNotExist()
        composeTestRule.onNodeWithText("Disney+").assertDoesNotExist()
    }
}
