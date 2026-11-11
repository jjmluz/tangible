package com.example.tangible.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.tangible.data.AlbumSearchResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WriteTagScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val album = AlbumSearchResult(uri = "spotify://album/abc", name = "Kind of Blue", artistName = "Miles Davis")

    private var isWriting by mutableStateOf(false)
    private var writeError by mutableStateOf<String?>(null)
    private var writeSucceeded by mutableStateOf(false)
    private val calls = mutableListOf<String>()

    /** Opens the screen, searches and picks the album, which starts waiting for a tag. */
    private fun openConfirmScreen() {
        composeTestRule.setContent {
            WriteTagScreen(
                onBackClick = {},
                isWriting = isWriting,
                writeError = writeError,
                writeSucceeded = writeSucceeded,
                onDone = { calls += "done" },
                onTagInfoUpdate = { isWriting = it != null },
                onWriteClick = {
                    calls += "write"
                    writeError = null
                    isWriting = true
                },
                onSearchAlbums = { Result.success(listOf(album)) }
            )
        }
        composeTestRule.onNodeWithText("Search artist or album").performTextInput("kind of")
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText("Kind of Blue").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Kind of Blue").performClick()
        composeTestRule.onNodeWithText("Hold tag near device...").assertIsDisplayed()
    }

    @Test
    fun `a written tag shows the success state and Done`() {
        openConfirmScreen()

        isWriting = false
        writeSucceeded = true

        composeTestRule.onNodeWithText("Tag written. Scan it to play.").assertIsDisplayed()
        // The badge sits on the cover's bottom corner, below the fold on the small test screen.
        composeTestRule.onNodeWithContentDescription("Success").assertExists()
        composeTestRule.onNodeWithText("Done").performClick()
        assertEquals(listOf("done"), calls)
    }

    @Test
    fun `a failed write shows the reason and Try again`() {
        openConfirmScreen()

        isWriting = false
        writeError = "Tag is read-only"

        composeTestRule.onNodeWithText("Couldn't write the tag").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tag is read-only").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Failed").assertExists()
        composeTestRule.onNodeWithText("Try again").performClick()
        assertEquals(listOf("write"), calls)
        composeTestRule.onNodeWithText("Hold tag near device...").assertIsDisplayed()
    }
}
