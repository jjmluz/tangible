package com.example.tangible.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeAssistantRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: HomeAssistantRepository
    private val baseUrl get() = server.url("/").toString()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = HomeAssistantRepository(retryDelayMs = 0)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `fetchPlayers renders the integration template and parses players sorted by name`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"entity_id":"media_player.whole_house","name":"Whole House","state":"idle"},
                   {"entity_id":"media_player.kitchen","name":"Kitchen","state":"playing"}]"""
            )
        )

        val players = repository.fetchPlayers(baseUrl, "token").getOrThrow()

        assertEquals(listOf("Kitchen", "Whole House"), players.map { it.name })
        assertEquals("media_player.kitchen", players[0].entityId)
        assertEquals("playing", players[0].state)

        val request = server.takeRequest()
        assertEquals("/api/template", request.path)
        assertEquals("POST", request.method)
        assertEquals("Bearer token", request.getHeader("Authorization"))
        val template = Gson().fromJson(request.body.readUtf8(), JsonObject::class.java)["template"].asString
        assertTrue(template.contains("integration_entities('music_assistant')"))
    }

    @Test
    fun `fetchPlayers returns empty list when there are no players`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        assertEquals(emptyList<MusicAssistantPlayer>(), repository.fetchPlayers(baseUrl, "token").getOrThrow())
    }

    @Test
    fun `playSpotifyAlbum calls music_assistant play_media with a Spotify album uri`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val result = repository.playSpotifyAlbum(baseUrl, "token", "media_player.whole_house", "4LH4d3cOWNNsVw41Gqt2kv")

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("/api/services/music_assistant/play_media", request.path)
        val body = Gson().fromJson(request.body.readUtf8(), JsonObject::class.java)
        assertEquals("media_player.whole_house", body["entity_id"].asString)
        assertEquals("spotify://album/4LH4d3cOWNNsVw41Gqt2kv", body["media_id"].asString)
        assertEquals("album", body["media_type"].asString)
        assertEquals("replace", body["enqueue"].asString)
    }

    @Test
    fun `network error is retried once`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val result = repository.playSpotifyAlbum(baseUrl, "token", "media_player.kitchen", "abc")

        assertTrue(result.isSuccess)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `HTTP errors are not retried and carry a readable message`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("401: Unauthorized"))

        val result = repository.fetchPlayers(baseUrl, "bad")

        assertTrue(result.isFailure)
        assertEquals("Home Assistant rejected the access token (401)", result.exceptionOrNull()?.message)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `HA service error message is surfaced`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"message":"Player not found"}"""))

        val result = repository.playSpotifyAlbum(baseUrl, "token", "media_player.gone", "abc")

        assertEquals("HTTP 400: Player not found", result.exceptionOrNull()?.message)
    }

    @Test
    fun `missing settings fail without a request`() = runBlocking {
        assertTrue(repository.fetchPlayers("", "token").isFailure)
        assertTrue(repository.fetchPlayers(baseUrl, " ").isFailure)
        assertTrue(repository.playSpotifyAlbum(baseUrl, "token", "", "abc").isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `normalizeUrl adds scheme and trims trailing slash`() {
        assertEquals("http://homeassistant.local:8123", HomeAssistantRepository.normalizeUrl(" homeassistant.local:8123/ "))
        assertEquals("https://ha.example.com", HomeAssistantRepository.normalizeUrl("https://ha.example.com/"))
    }

    @Test
    fun `fetchPlayerStatus parses track, artwork, position and volume`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"entity_id":"media_player.sala","state":"playing","attributes":{
                    "friendly_name":"Sala de Jantar","media_title":"Miracle","media_artist":"CHVRCHES",
                    "media_album_name":"Hansa Session EP","media_duration":208,"media_position":178,
                    "media_position_updated_at":"2026-11-09T18:11:12+00:00","volume_level":0.26,
                    "is_volume_muted":false,
                    "entity_picture":"http://192.168.1.82:8095/imageproxy/abc?size=512"}}"""
            )
        )

        val status = repository.fetchPlayerStatus(baseUrl, "token", "media_player.sala").getOrThrow()

        assertEquals("/api/states/media_player.sala", server.takeRequest().path)
        assertEquals("Sala de Jantar", status.name)
        assertTrue(status.isPlaying)
        assertTrue(status.isActive)
        assertEquals("Miracle", status.title)
        assertEquals("CHVRCHES", status.artist)
        assertEquals("Hansa Session EP", status.album)
        assertEquals("http://192.168.1.82:8095/imageproxy/abc?size=512", status.artworkUrl)
        assertEquals(208.0, status.durationSec!!, 0.0)
        assertEquals(0.26f, status.volume!!, 0.001f)
        // 10 seconds after the reported update, a playing track has moved on 10 seconds.
        val updatedAt = java.time.OffsetDateTime.parse("2026-11-09T18:11:12+00:00").toInstant().toEpochMilli()
        assertEquals(188.0, status.positionAt(updatedAt + 10_000)!!, 0.001)
        // ...but never past the end.
        assertEquals(208.0, status.positionAt(updatedAt + 60_000)!!, 0.001)
    }

    @Test
    fun `relative artwork paths are resolved against the HA url and missing media is handled`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"entity_id":"media_player.k","state":"idle","attributes":{
                    "friendly_name":"Kitchen","entity_picture":"/api/media_player_proxy/media_player.k?token=t"}}"""
            )
        )

        val status = repository.fetchPlayerStatus(baseUrl, "token", "media_player.k").getOrThrow()

        assertEquals(baseUrl.trimEnd('/') + "/api/media_player_proxy/media_player.k?token=t", status.artworkUrl)
        assertTrue(!status.hasMedia)
        assertTrue(!status.isActive)
        assertEquals(null, status.positionAt(0))
    }

    @Test
    fun `controls call the matching media_player services`() = runBlocking {
        repeat(5) { server.enqueue(MockResponse().setResponseCode(200).setBody("[]")) }

        repository.pause(baseUrl, "token", "media_player.k").getOrThrow()
        repository.play(baseUrl, "token", "media_player.k").getOrThrow()
        repository.nextTrack(baseUrl, "token", "media_player.k").getOrThrow()
        repository.seek(baseUrl, "token", "media_player.k", 42.5).getOrThrow()
        repository.setVolume(baseUrl, "token", "media_player.k", 1.4f).getOrThrow()

        val requests = List(5) { server.takeRequest() }
        assertEquals(
            listOf("media_pause", "media_play", "media_next_track", "media_seek", "volume_set").map { "/api/services/media_player/$it" },
            requests.map { it.path }
        )
        val bodies = requests.map { Gson().fromJson(it.body.readUtf8(), JsonObject::class.java) }
        bodies.forEach { assertEquals("media_player.k", it["entity_id"].asString) }
        assertEquals(42.5, bodies[3]["seek_position"].asDouble, 0.0)
        assertEquals(1.0, bodies[4]["volume_level"].asDouble, 0.0)
    }

    @Test
    fun `controls are not retried after a network error`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val result = repository.nextTrack(baseUrl, "token", "media_player.k")

        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `transferQueue moves the queue to the target player`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        repository.transferQueue(baseUrl, "token", "media_player.kitchen", "media_player.whole_house").getOrThrow()

        val request = server.takeRequest()
        assertEquals("/api/services/music_assistant/transfer_queue", request.path)
        val body = Gson().fromJson(request.body.readUtf8(), JsonObject::class.java)
        assertEquals("media_player.whole_house", body["entity_id"].asString)
        assertEquals("media_player.kitchen", body["source_player"].asString)
        assertTrue(body["auto_play"].asBoolean)
    }
}
