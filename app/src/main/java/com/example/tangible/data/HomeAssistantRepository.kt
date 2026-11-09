package com.example.tangible.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A Music Assistant player (or player group) as exposed by Home Assistant. */
data class MusicAssistantPlayer(
    val entityId: String,
    val name: String,
    val state: String = ""
)

/** A TV exposed by Home Assistant's Android TV Remote integration. */
data class TvDevice(
    val entityId: String,
    val name: String
)

/** What a player is doing right now, read from its Home Assistant media_player state. */
data class PlayerStatus(
    val entityId: String,
    val name: String,
    val state: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationSec: Double? = null,
    val positionSec: Double? = null,
    /** When HA last updated [positionSec], in epoch millis. */
    val positionUpdatedAtMs: Long? = null,
    val volume: Float? = null,
    val isMuted: Boolean = false,
    /** The current track's ID, e.g. `spotify--abc://track/<id>`; changes when the track does. */
    val contentId: String? = null,
    /** When the state (playing, paused, idle…) last changed, in epoch millis. */
    val stateChangedAtMs: Long? = null
) {
    val isPlaying: Boolean get() = state == "playing"
    val isAvailable: Boolean get() = state != "unavailable" && state != "off"
    val hasMedia: Boolean get() = !title.isNullOrBlank()
    /**
     * Something is loaded and can be resumed or paused. Some Music Assistant players (Sonos) report
     * a paused track as "idle", so idle with a track loaded counts too.
     */
    val isActive: Boolean get() = hasMedia && (state == "playing" || state == "paused" || state == "idle")
    /** Identifies the current track, to tell when it changes. */
    val trackKey: String? get() = contentId ?: title

    /** HA only reports the position when it changes, so add the time elapsed since then while playing. */
    fun positionAt(nowMs: Long): Double? {
        val base = positionSec ?: return null
        val elapsed = if (isPlaying && positionUpdatedAtMs != null) {
            ((nowMs - positionUpdatedAtMs) / 1000.0).coerceAtLeast(0.0)
        } else 0.0
        val position = base + elapsed
        return durationSec?.let { position.coerceAtMost(it) } ?: position
    }
}

/**
 * Talks to Home Assistant's REST API, using a long-lived access token.
 * Music goes through the `music_assistant.play_media` service, so Music Assistant handles
 * the streaming provider (Spotify) and the speakers, including groups.
 * Movies open on a TV as a deep link, through the Android TV Remote integration.
 */
class HomeAssistantRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val gson: Gson = Gson(),
    private val retryDelayMs: Long = 1500
) {
    private val tag = "HomeAssistantRepository"
    private val json = "application/json".toMediaType()

    /** Lists the media players that belong to the Music Assistant integration. */
    suspend fun fetchPlayers(baseUrl: String, token: String): Result<List<MusicAssistantPlayer>> =
        fetchMediaPlayers(baseUrl, token, "music_assistant") { entityId, name, state ->
            MusicAssistantPlayer(entityId, name, state)
        }.map { players -> players.sortedBy { it.name.lowercase() } }

    /** Lists the TVs that belong to the Android TV Remote integration. */
    suspend fun fetchTvs(baseUrl: String, token: String): Result<List<TvDevice>> =
        fetchMediaPlayers(baseUrl, token, "androidtv_remote") { entityId, name, _ ->
            TvDevice(entityId, name)
        }.map { tvs -> tvs.sortedBy { it.name.lowercase() } }

    /**
     * Opens a deep link (e.g. a Netflix title URL) on the TV. The streaming app that handles the
     * link comes to the front on that title.
     */
    suspend fun openOnTv(baseUrl: String, token: String, entityId: String, url: String): Result<Unit> {
        if (entityId.isBlank()) {
            return Result.failure(IllegalArgumentException("No TV selected"))
        }
        return callService(baseUrl, token, "media_player", "play_media", retryOnNetworkError = true) {
            addProperty("entity_id", entityId)
            addProperty("media_content_type", "url")
            addProperty("media_content_id", url)
        }
    }

    /**
     * HA's REST API has no entity-registry endpoint, so this renders a template that uses
     * `integration_entities` to find one integration's media players.
     */
    private suspend fun <T> fetchMediaPlayers(
        baseUrl: String,
        token: String,
        integration: String,
        create: (entityId: String, name: String, state: String) -> T
    ): Result<List<T>> =
        call(baseUrl, token) { url ->
            val body = JsonObject().apply { addProperty("template", mediaPlayersTemplate(integration)) }
            val request = authorized(url + "/api/template", token)
                .post(body.toString().toRequestBody(json))
                .build()
            execute(request) { text ->
                val array = gson.fromJson(text.trim().ifEmpty { "[]" }, JsonArray::class.java)
                array.mapNotNull { element ->
                    val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    val entityId = obj.get("entity_id")?.asString ?: return@mapNotNull null
                    create(
                        entityId,
                        obj.get("name")?.takeUnless { it.isJsonNull }?.asString ?: entityId,
                        obj.get("state")?.takeUnless { it.isJsonNull }?.asString ?: ""
                    )
                }
            }
        }

    /** Replaces the player's queue with the given Spotify album and starts playing it. */
    suspend fun playSpotifyAlbum(
        baseUrl: String,
        token: String,
        entityId: String,
        spotifyAlbumId: String
    ): Result<Unit> {
        if (entityId.isBlank()) {
            return Result.failure(IllegalArgumentException("No Music Assistant player selected"))
        }
        return call(baseUrl, token) { url ->
            val body = JsonObject().apply {
                addProperty("entity_id", entityId)
                addProperty("media_id", "spotify://album/$spotifyAlbumId")
                addProperty("media_type", "album")
                addProperty("enqueue", "replace")
            }
            val request = authorized(url + "/api/services/music_assistant/play_media", token)
                .post(body.toString().toRequestBody(json))
                .build()
            execute(request) { }
        }
    }

    /** Reads the player's current state, track and volume. */
    suspend fun fetchPlayerStatus(baseUrl: String, token: String, entityId: String): Result<PlayerStatus> =
        call(baseUrl, token) { url ->
            val request = authorized("$url/api/states/$entityId", token).get().build()
            execute(request) { text -> parseStatus(gson.fromJson(text, JsonObject::class.java), url) }
        }

    suspend fun play(baseUrl: String, token: String, entityId: String) =
        mediaPlayerService(baseUrl, token, entityId, "media_play")

    suspend fun pause(baseUrl: String, token: String, entityId: String) =
        mediaPlayerService(baseUrl, token, entityId, "media_pause")

    suspend fun nextTrack(baseUrl: String, token: String, entityId: String) =
        mediaPlayerService(baseUrl, token, entityId, "media_next_track")

    suspend fun previousTrack(baseUrl: String, token: String, entityId: String) =
        mediaPlayerService(baseUrl, token, entityId, "media_previous_track")

    suspend fun seek(baseUrl: String, token: String, entityId: String, positionSec: Double) =
        mediaPlayerService(baseUrl, token, entityId, "media_seek") { addProperty("seek_position", positionSec) }

    suspend fun setVolume(baseUrl: String, token: String, entityId: String, level: Float) =
        mediaPlayerService(baseUrl, token, entityId, "volume_set") {
            addProperty("volume_level", level.coerceIn(0f, 1f))
        }

    /** Moves the queue (and playback) from one Music Assistant player to another. */
    suspend fun transferQueue(baseUrl: String, token: String, fromEntityId: String, toEntityId: String): Result<Unit> =
        callService(baseUrl, token, "music_assistant", "transfer_queue", retryOnNetworkError = false) {
            addProperty("entity_id", toEntityId)
            addProperty("source_player", fromEntityId)
            addProperty("auto_play", true)
        }

    // Controls aren't retried: a retry after a lost response could skip two tracks.
    private suspend fun mediaPlayerService(
        baseUrl: String,
        token: String,
        entityId: String,
        service: String,
        extra: JsonObject.() -> Unit = {}
    ): Result<Unit> = callService(baseUrl, token, "media_player", service, retryOnNetworkError = false) {
        addProperty("entity_id", entityId)
        extra()
    }

    private suspend fun callService(
        baseUrl: String,
        token: String,
        domain: String,
        service: String,
        retryOnNetworkError: Boolean,
        body: JsonObject.() -> Unit
    ): Result<Unit> = call(baseUrl, token, retryOnNetworkError) { url ->
        val payload = JsonObject().apply(body)
        val request = authorized("$url/api/services/$domain/$service", token)
            .post(payload.toString().toRequestBody(json))
            .build()
        execute(request) { }
    }

    private fun parseStatus(obj: JsonObject, baseUrl: String): PlayerStatus {
        val attrs = obj.getAsJsonObject("attributes") ?: JsonObject()
        fun str(key: String) = attrs.get(key)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
        fun num(key: String) = attrs.get(key)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asDouble }.getOrNull() }
        val entityId = obj.get("entity_id").asString
        // Music Assistant sets an absolute image URL; other players get HA's relative proxy path.
        val picture = str("entity_picture")?.let { if (it.startsWith("http")) it else baseUrl + it }
        return PlayerStatus(
            entityId = entityId,
            name = str("friendly_name") ?: entityId,
            state = obj.get("state")?.asString ?: "unknown",
            title = str("media_title"),
            artist = str("media_artist"),
            album = str("media_album_name"),
            artworkUrl = picture,
            durationSec = num("media_duration"),
            positionSec = num("media_position"),
            positionUpdatedAtMs = parseTime(str("media_position_updated_at")),
            stateChangedAtMs = parseTime(obj.get("last_changed")?.takeUnless { it.isJsonNull }?.asString),
            volume = num("volume_level")?.toFloat(),
            isMuted = attrs.get("is_volume_muted")?.takeUnless { it.isJsonNull }?.asBoolean ?: false,
            contentId = str("media_content_id")
        )
    }

    private fun parseTime(value: String?): Long? = value?.let {
        runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
    }

    private fun authorized(url: String, token: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer ${token.trim()}")
        .header("Content-Type", "application/json")

    private fun <T> execute(request: Request, parse: (String) -> T): T =
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw HomeAssistantException(describeError(response.code, text))
            }
            parse(text)
        }

    /**
     * Validates the settings, then runs [block] on the IO dispatcher. A network error is retried
     * once, because a tag is often scanned right as the phone wakes and Wi-Fi isn't back yet.
     */
    private suspend fun <T> call(
        baseUrl: String,
        token: String,
        retryOnNetworkError: Boolean = true,
        block: (cleanUrl: String) -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        if (token.isBlank()) return@withContext Result.failure(IllegalArgumentException("Access token is empty"))
        val cleanUrl = normalizeUrl(baseUrl)
        try {
            Result.success(
                try {
                    block(cleanUrl)
                } catch (e: IOException) {
                    if (e is HomeAssistantException || !retryOnNetworkError) throw e
                    Log.w(tag, "Network error, retrying once: ${e.message}")
                    delay(retryDelayMs)
                    block(cleanUrl)
                }
            )
        } catch (e: Exception) {
            Log.e(tag, "Home Assistant request failed", e)
            Result.failure(e)
        }
    }

    class HomeAssistantException(message: String) : IOException(message)

    companion object {
        private fun mediaPlayersTemplate(integration: String) = """
            {%- set ns = namespace(players=[]) -%}
            {%- for e in integration_entities('$integration') if e.startswith('media_player.') -%}
            {%- set ns.players = ns.players + [{'entity_id': e, 'name': state_attr(e, 'friendly_name') or e, 'state': states(e)}] -%}
            {%- endfor -%}
            {{ ns.players | tojson }}
        """.trimIndent()

        internal fun normalizeUrl(url: String): String {
            var trimmed = url.trim().trimEnd('/')
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                trimmed = "http://$trimmed"
            }
            return trimmed
        }

        internal fun describeError(code: Int, body: String): String = when (code) {
            401 -> "Home Assistant rejected the access token (401)"
            404 -> "Not found (404). Is the integration set up in Home Assistant, and does the player or TV still exist?"
            else -> {
                val message = runCatching {
                    Gson().fromJson(body, JsonObject::class.java)?.get("message")?.asString
                }.getOrNull()
                "HTTP $code" + (message ?: body.take(200)).let { if (it.isBlank()) "" else ": $it" }
            }
        }
    }
}
