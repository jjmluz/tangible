package com.jjmluz.tangible.data

import android.content.Context
import android.content.SharedPreferences
import com.jjmluz.tangible.SavedTag
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PosterStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var prefs: SharedPreferences
    private lateinit var dir: File
    private var nowMs = 1_000_000_000_000L
    private val lookUps = mutableListOf<Int>()
    private var lookUpResult: String? = null

    private val store by lazy {
        PosterStore(dir, prefs, lookUpPosterUrl = { lookUps += it; lookUpResult }, now = { nowMs })
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dir = File(folder.root, "posters")
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("posters", Context.MODE_PRIVATE)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun link(path: String) = server.url(path).toString()

    private fun movie(tmdbId: Int, imageUrl: String?) =
        SavedTag("tag-$tmdbId", "Movie $tmdbId", "2021", "MOVIE", "tangible://movie?tmdb=$tmdbId&mx=abc", imageUrl)

    @Test
    fun `a movie's poster is saved from the tag's link`() = runBlocking {
        server.enqueue(MockResponse().setBody("poster"))
        val tag = movie(438631, link("/dune.jpg"))

        store.sync(listOf(tag))

        val file = store.stored(listOf(tag)).getValue(tag.id)
        assertEquals("438631.jpg", file.name)
        assertEquals("poster", file.readText())
        assertTrue(lookUps.isEmpty())
    }

    @Test
    fun `a poster that's on the phone isn't fetched again`() = runBlocking {
        server.enqueue(MockResponse().setBody("poster"))
        val tags = listOf(movie(438631, link("/dune.jpg")))

        store.sync(tags)
        store.sync(tags)

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `an expired link is replaced by a looked up one`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setBody("fresh poster"))
        lookUpResult = link("/fresh.jpg")
        val tag = movie(438631, link("/expired.jpg"))

        store.sync(listOf(tag))

        assertEquals(listOf(438631), lookUps)
        assertEquals("fresh poster", store.stored(listOf(tag)).getValue(tag.id).readText())
    }

    @Test
    fun `a tag without a link gets a looked up poster`() = runBlocking {
        server.enqueue(MockResponse().setBody("fresh poster"))
        lookUpResult = link("/fresh.jpg")
        val tag = movie(438631, null)

        store.sync(listOf(tag))

        assertEquals("fresh poster", store.stored(listOf(tag)).getValue(tag.id).readText())
    }

    @Test
    fun `a movie is looked up once a day at most`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(403)) }
        val tags = listOf(movie(438631, link("/expired.jpg")))

        store.sync(tags)
        store.sync(tags)
        assertEquals(listOf(438631), lookUps)

        nowMs += 24 * 60 * 60 * 1000L
        store.sync(tags)
        assertEquals(listOf(438631, 438631), lookUps)
        assertTrue(store.stored(tags).isEmpty())
    }

    @Test
    fun `nothing is looked up when the link can't be reached`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val tags = listOf(movie(438631, link("/dune.jpg")))

        store.sync(tags)

        assertTrue(lookUps.isEmpty())
        assertTrue(store.stored(tags).isEmpty())
    }

    @Test
    fun `the poster of a deleted tag is removed`() = runBlocking {
        server.enqueue(MockResponse().setBody("poster"))
        store.sync(listOf(movie(438631, link("/dune.jpg"))))
        assertTrue(File(dir, "438631.jpg").isFile)

        store.sync(emptyList())

        assertFalse(File(dir, "438631.jpg").exists())
    }

    @Test
    fun `music tags are left alone`() = runBlocking {
        val album = SavedTag("1", "OK Computer", "Radiohead", "MUSIC", "tangible://play?spotify_album=abc", link("/cover.jpg"))

        store.sync(listOf(album))

        assertEquals(0, server.requestCount)
        assertTrue(store.stored(listOf(album)).isEmpty())
    }
}
