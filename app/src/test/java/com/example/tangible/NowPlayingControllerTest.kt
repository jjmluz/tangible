package com.example.tangible

import com.example.tangible.data.HomeAssistantRepository
import com.example.tangible.data.PlayerStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingControllerTest {

    private val repo = mockk<HomeAssistantRepository>()

    private fun status(
        id: String = "media_player.a",
        state: String = "playing",
        contentId: String = "track/1",
        positionSec: Double = 10.0
    ) = PlayerStatus(
        entityId = id, name = id, state = state, title = "Song", contentId = contentId,
        durationSec = 200.0, positionSec = positionSec, positionUpdatedAtMs = 0, volume = 0.5f
    )

    private fun TestScope.controller() = NowPlayingController(
        repository = repo,
        scope = this,
        settings = { "http://ha" to "token" },
        settleDelayMs = 700,
        confirmPollMs = 300,
        confirmTimeoutMs = 5000,
        startTimeoutMs = 15000,
        clock = { testScheduler.currentTime }
    )

    @Test
    fun `play-pause updates the UI immediately and sends pause`() = runTest {
        coEvery { repo.fetchPlayerStatus(any(), any(), "media_player.a") } returns Result.success(status())
        coEvery { repo.pause(any(), any(), any()) } returns Result.success(Unit)
        val controller = controller()
        controller.setPlayer("media_player.a")
        runCurrent()
        assertTrue(controller.status!!.isPlaying)

        controller.playPause()

        assertEquals("paused", controller.status!!.state)
        runCurrent()
        coVerify { repo.pause("http://ha", "token", "media_player.a") }
    }

    @Test
    fun `a poll that started before a command doesn't undo it`() = runTest {
        coEvery { repo.fetchPlayerStatus(any(), any(), any()) } returns Result.success(status())
        coEvery { repo.pause(any(), any(), any()) } returns Result.success(Unit)
        val controller = controller()
        controller.setPlayer("media_player.a")
        runCurrent()

        // A slow poll is in flight, still reporting "playing", when the user taps pause.
        coEvery { repo.fetchPlayerStatus(any(), any(), any()) } coAnswers {
            delay(300)
            Result.success(status(state = "playing"))
        }
        controller.refreshSoon()
        runCurrent()
        controller.playPause()
        advanceTimeBy(400)
        runCurrent()
        assertEquals("paused", controller.status!!.state)

        // After the command settles, the next read is trusted again.
        coEvery { repo.fetchPlayerStatus(any(), any(), any()) } returns Result.success(status(state = "paused"))
        advanceUntilIdle()
        assertEquals("paused", controller.status!!.state)
    }

    @Test
    fun `switching while idle just shows the other player`() = runTest {
        coEvery { repo.fetchPlayerStatus(any(), any(), "media_player.a") } returns Result.success(status(state = "idle"))
        coEvery { repo.fetchPlayerStatus(any(), any(), "media_player.b") } returns
            Result.success(status(id = "media_player.b", state = "idle"))
        val controller = controller()
        controller.setPlayer("media_player.a")
        runCurrent()

        controller.switchTo("media_player.b")
        runCurrent()

        assertEquals("media_player.b", controller.playerId)
        coVerify(exactly = 0) { repo.transferQueue(any(), any(), any(), any()) }
    }

    @Test
    fun `switching while playing moves the queue`() = runTest {
        coEvery { repo.fetchPlayerStatus(any(), any(), "media_player.a") } returns Result.success(status())
        coEvery { repo.fetchPlayerStatus(any(), any(), "media_player.b") } returns Result.success(status(id = "media_player.b"))
        coEvery { repo.transferQueue(any(), any(), any(), any()) } returns Result.success(Unit)
        val controller = controller()
        controller.setPlayer("media_player.a")
        runCurrent()

        controller.switchTo("media_player.b")
        assertTrue(controller.isTransferring)
        advanceUntilIdle()

        coVerify { repo.transferQueue("http://ha", "token", "media_player.a", "media_player.b") }
        assertEquals("media_player.b", controller.playerId)
        assertFalse(controller.isTransferring)
    }

    @Test
    fun `a failed transfer keeps the current player and shows the error`() = runTest {
        coEvery { repo.fetchPlayerStatus(any(), any(), "media_player.a") } returns Result.success(status())
        coEvery { repo.fetchPlayerStatus(any(), any(), "media_player.b") } returns Result.success(status(id = "media_player.b"))
        coEvery { repo.transferQueue(any(), any(), any(), any()) } returns Result.failure(Exception("HTTP 500: boom"))
        val controller = controller()
        controller.setPlayer("media_player.a")
        runCurrent()

        controller.switchTo("media_player.b")
        advanceUntilIdle()

        assertEquals("media_player.a", controller.playerId)
        assertEquals("HTTP 500: boom", controller.error)
    }

    @Test
    fun `resume keeps showing playing until the speaker catches up`() = runTest {
        coEvery { repo.fetchPlayerStatus(any(), any(), any()) } returns Result.success(status(state = "paused"))
        coEvery { repo.play(any(), any(), any()) } returns Result.success(Unit)
        val controller = controller()
        controller.setPlayer("media_player.a")
        runCurrent()

        controller.playPause()
        assertTrue(controller.status!!.isPlaying)

        // HA accepted the command but the speaker still reports paused for a while.
        advanceTimeBy(1500)
        assertTrue("must not flip back to paused", controller.status!!.isPlaying)

        coEvery { repo.fetchPlayerStatus(any(), any(), any()) } returns Result.success(status(state = "playing", positionSec = 11.0))
        advanceTimeBy(400)
        runCurrent()
        assertTrue(controller.status!!.isPlaying)
        assertEquals(11.0, controller.status!!.positionSec!!, 0.0)
    }

    @Test
    fun `if the speaker never resumes the real state is shown after the timeout`() = runTest {
        coEvery { repo.fetchPlayerStatus(any(), any(), any()) } returns Result.success(status(state = "paused"))
        coEvery { repo.play(any(), any(), any()) } returns Result.success(Unit)
        val controller = controller()
        controller.setPlayer("media_player.a")
        runCurrent()

        controller.playPause()
        advanceTimeBy(4000)
        assertTrue(controller.status!!.isPlaying)
        advanceUntilIdle()
        assertEquals("paused", controller.status!!.state)
    }

    @Test
    fun `a failed command shows the real state straight away`() = runTest {
        coEvery { repo.fetchPlayerStatus(any(), any(), any()) } returns Result.success(status(state = "paused"))
        coEvery { repo.play(any(), any(), any()) } returns Result.failure(Exception("HTTP 500: nope"))
        val controller = controller()
        controller.setPlayer("media_player.a")
        runCurrent()

        controller.playPause()
        assertTrue(controller.status!!.isPlaying)
        runCurrent()

        assertEquals("paused", controller.status!!.state)
        assertEquals("HTTP 500: nope", controller.error)
    }

    @Test
    fun `the start spinner stays until the new album is playing`() = runTest {
        coEvery { repo.fetchPlayerStatus(any(), any(), any()) } returns
            Result.success(status(state = "paused", contentId = "track/old", positionSec = 120.0))
        val controller = controller()
        controller.setPlayer("media_player.a")
        runCurrent()

        controller.playbackStarting("media_player.a")
        controller.playbackStarted(Result.success(Unit))
        advanceTimeBy(1000)
        assertTrue(controller.isStarting)
        assertEquals("track/old", controller.status!!.contentId)

        coEvery { repo.fetchPlayerStatus(any(), any(), any()) } returns
            Result.success(status(state = "playing", contentId = "track/new", positionSec = 0.0))
        advanceTimeBy(400)
        runCurrent()
        assertFalse(controller.isStarting)
        assertEquals("track/new", controller.status!!.contentId)
    }

    @Test
    fun `a failed start clears the spinner and shows the error`() = runTest {
        coEvery { repo.fetchPlayerStatus(any(), any(), any()) } returns Result.success(status(state = "idle"))
        val controller = controller()
        controller.setPlayer("media_player.a")
        runCurrent()

        controller.playbackStarting("media_player.a")
        controller.playbackStarted(Result.failure(Exception("Not found (404)")))
        runCurrent()

        assertFalse(controller.isStarting)
        assertEquals("Not found (404)", controller.error)
    }
}
