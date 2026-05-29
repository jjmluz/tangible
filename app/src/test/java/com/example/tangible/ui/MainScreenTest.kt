package com.example.tangible.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.tangible.SavedTag
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
            MainScreen(tags = emptyList(), onPlay = {})
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
            MainScreen(tags = tags, onPlay = {})
        }

        composeTestRule.onNodeWithText("Song Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Artist Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("Movie Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Director").assertIsDisplayed()
    }
}
