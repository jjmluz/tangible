package com.jjmluz.tangible.ui

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
import androidx.compose.ui.test.performScrollTo
import com.jjmluz.tangible.data.MovieService
import com.jjmluz.tangible.data.Region
import com.jjmluz.tangible.data.RegionService
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w448dp-h998dp")
class MovieSettingsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val portugal = Region(
        "pt", "Portugal",
        listOf(RegionService("netflix", "Netflix"), RegionService("hbo", "HBO Max"), RegionService("apple", "Apple TV"), RegionService("hulu", "Hulu"))
    )
    private val germany = Region("de", "Germany", listOf(RegionService("wow", "WOW")))

    private fun setUp(
        region: String = "pt",
        apiKey: String = "key",
        services: Set<MovieService> = MovieService.entries.toSet(),
        onServicesChange: (Set<MovieService>) -> Unit = {},
        onRegionChange: (String) -> Unit = {}
    ) {
        composeTestRule.setContent {
            StreamingServicesScreen(
                onBackClick = {},
                apiKey = apiKey,
                isApiKeyValid = apiKey.isNotBlank(),
                regions = listOf(germany, portugal),
                region = region,
                onRegionChange = onRegionChange,
                services = services,
                onServicesChange = onServicesChange
            )
        }
    }

    @Test
    fun `only the services Tangible can open in the region are offered, plus Stremio`() {
        setUp()

        composeTestRule.onNodeWithTag("service_nf").assertIsOn()
        composeTestRule.onNodeWithTag("service_mx").assertIsOn()
        composeTestRule.onNodeWithTag("service_at").assertIsOn()
        composeTestRule.onNodeWithTag("service_sm").assertIsOn()
        composeTestRule.onNodeWithTag("service_dp").assertDoesNotExist()
        composeTestRule.onNodeWithTag("service_pv").assertDoesNotExist()
        composeTestRule.onNodeWithText("Tangible can't open these yet: Hulu.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `unticking a service removes it`() {
        val changes = mutableListOf<Set<MovieService>>()
        var services by mutableStateOf(MovieService.entries.toSet())
        composeTestRule.setContent {
            StreamingServicesScreen(
                onBackClick = {}, apiKey = "key", isApiKeyValid = true,
                regions = listOf(portugal), region = "pt",
                services = services, onServicesChange = { changes += it; services = it }
            )
        }

        composeTestRule.onNodeWithTag("service_nf").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("service_nf").assertIsOff()
        assertEquals(MovieService.entries.toSet() - MovieService.NETFLIX, changes.single())
    }

    @Test
    fun `the region is picked from the regions the API covers`() {
        val picked = mutableListOf<String>()
        setUp(onRegionChange = { picked += it })

        composeTestRule.onNodeWithText("Portugal").assertIsDisplayed()
        composeTestRule.onNodeWithTag("movie_region").performClick()
        composeTestRule.onNodeWithText("Germany").performClick()

        assertEquals(listOf("de"), picked)
    }

    @Test
    fun `a phone region the API doesn't cover asks for another`() {
        setUp(region = "aq")

        composeTestRule.onNodeWithText("Antarctica").assertIsDisplayed()
        composeTestRule.onNodeWithText("Movie search doesn't cover this region. Pick another.").assertIsDisplayed()
    }

    @Test
    fun `Settings sums up the region and the services ticked that it has`() {
        composeTestRule.setContent {
            SettingsScreen(
                onHomeAssistantClick = {}, onMusicAssistantClick = {}, onMovieTvClick = {},
                onStreamingServicesClick = {}, onExportClick = {}, onImportClick = {},
                regions = listOf(portugal), movieRegion = "pt",
                movieServices = setOf(MovieService.NETFLIX, MovieService.DISNEY, MovieService.STREMIO)
            )
        }

        // Disney+ isn't in Portugal's list, so it doesn't count.
        composeTestRule.onNodeWithText("Portugal · 2 services").assertIsDisplayed()
    }

    @Test
    fun `without a key it explains where to get one`() {
        setUp(apiKey = "")

        composeTestRule.onNodeWithText("Get a free API key").assertIsDisplayed()
        composeTestRule.onNodeWithText("Needed to write movie tags. Tags you've written work without it.").assertIsDisplayed()
    }
}
