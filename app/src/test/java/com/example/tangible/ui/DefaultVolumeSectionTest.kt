package com.example.tangible.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w448dp-h998dp")
class DefaultVolumeSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `off by default, switching on suggests 30 percent and switching off clears it`() {
        val changes = mutableListOf<Int?>()
        var volume by mutableStateOf<Int?>(null)
        composeTestRule.setContent {
            Column {
                DefaultVolumeSection(volume = volume, onVolumeChange = { changes += it; volume = it })
            }
        }

        composeTestRule.onNodeWithTag("default_volume_switch").assertIsOff()
        composeTestRule.onNodeWithText("Albums start at whatever volume the player is at").assertIsDisplayed()
        composeTestRule.onNodeWithTag("default_volume_slider").assertDoesNotExist()

        composeTestRule.onNodeWithTag("default_volume_switch").performClick()
        composeTestRule.onNodeWithTag("default_volume_switch").assertIsOn()
        composeTestRule.onNodeWithText("30%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Albums start at 30% when you scan a tag or tap play").assertIsDisplayed()

        composeTestRule.onNodeWithTag("default_volume_switch").performClick()
        assertEquals(listOf(30, null), changes)
    }

    @Test
    fun `shows the saved level`() {
        composeTestRule.setContent {
            Column { DefaultVolumeSection(volume = 45, onVolumeChange = {}) }
        }

        composeTestRule.onNodeWithTag("default_volume_switch").assertIsOn()
        composeTestRule.onNodeWithText("45%").assertIsDisplayed()
    }
}
