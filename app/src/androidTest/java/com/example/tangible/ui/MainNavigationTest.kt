package com.example.tangible.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.tangible.MainActivity
import org.junit.Rule
import org.junit.Test

class MainNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testNavigationToSettingsAndBack() {
        // Start at Collection (default)
        composeTestRule.onNodeWithText("Tangible").assertIsDisplayed()

        // Navigate to Settings
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        
        // Verify Settings screen is shown
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Music Assistant").assertIsDisplayed()

        // Navigate back to Collection
        composeTestRule.onNodeWithContentDescription("Collection").performClick()
        
        // Verify back on Collection
        composeTestRule.onNodeWithText("Tangible").assertIsDisplayed()
    }

    @Test
    fun testNavigationToWriteTag() {
        // Click FAB to go to Write Tag
        composeTestRule.onNodeWithContentDescription("Write Tag").performClick()

        // Verify Write Tag screen is shown
        composeTestRule.onNodeWithText("Write Tag").assertIsDisplayed()
        
        // Check for common elements in Write Tag screen
        composeTestRule.onNodeWithText("ALBUM").assertIsDisplayed()
        composeTestRule.onNodeWithText("MOVIE").assertIsDisplayed()
    }
}
