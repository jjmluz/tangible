package com.example.tangible.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import com.example.tangible.SavedTag
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Assert.assertEquals
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
    fun `tapping a movie tag plays it, with no play button or edit dialog`() {
        val movie = SavedTag("1", "Dune", "2021", "MOVIE", "tangible://movie?tmdb=438631&mx=e7dc7b3a")
        val played = mutableListOf<String>()

        composeTestRule.setContent {
            MainScreen(
                tags = listOf(movie),
                selectedTagIds = emptySet(),
                onPlay = { played += it.id },
                onTagClick = {},
                onTagLongClick = {},
                viewMode = ViewMode.LIST
            )
        }

        composeTestRule.onNodeWithText("Movies").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithContentDescription("Play").assertDoesNotExist()
        composeTestRule.onNodeWithText("Dune").performClick()

        assertEquals(listOf("1"), played)
        composeTestRule.onNodeWithText("Edit Tag").assertDoesNotExist()
    }

    @Test
    fun `tapping a music tag plays it, with no play button or edit dialog`() {
        val music = SavedTag("1", "Kind of Blue", "Miles Davis", "MUSIC", "tangible://play?spotify_album=abc")
        val played = mutableListOf<String>()

        composeTestRule.setContent {
            MainScreen(
                tags = listOf(music),
                selectedTagIds = emptySet(),
                onPlay = { played += it.id },
                onTagClick = {},
                onTagLongClick = {},
                viewMode = ViewMode.LIST
            )
        }

        composeTestRule.onNodeWithContentDescription("Play").assertDoesNotExist()
        composeTestRule.onNodeWithText("Kind of Blue").performClick()

        assertEquals(listOf("1"), played)
        composeTestRule.onNodeWithText("Edit Tag").assertDoesNotExist()
    }

    @Test
    fun `tapping a music tag in the gallery plays it`() {
        val music = SavedTag("1", "Kind of Blue", "Miles Davis", "MUSIC", "tangible://play?spotify_album=abc")
        val played = mutableListOf<String>()

        composeTestRule.setContent {
            MainScreen(
                tags = listOf(music),
                selectedTagIds = emptySet(),
                onPlay = { played += it.id },
                onTagClick = {},
                onTagLongClick = {},
                viewMode = ViewMode.GALLERY
            )
        }

        composeTestRule.onNodeWithContentDescription("Play").assertDoesNotExist()
        composeTestRule.onNodeWithText("Kind of Blue").performClick()
        assertEquals(listOf("1"), played)
    }

    @Test
    fun `tapping in selection mode selects instead of playing`() {
        val music = SavedTag("1", "Kind of Blue", "Miles Davis", "MUSIC", "tangible://play?spotify_album=abc")
        val played = mutableListOf<String>()
        val clicked = mutableListOf<String>()

        composeTestRule.setContent {
            MainScreen(
                tags = listOf(music),
                selectedTagIds = setOf("1"),
                onPlay = { played += it.id },
                onTagClick = { clicked += it },
                onTagLongClick = {},
                viewMode = ViewMode.LIST
            )
        }

        composeTestRule.onNodeWithText("Kind of Blue").performClick()
        assertEquals(listOf("1"), clicked)
        assertEquals(emptyList<String>(), played)
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
