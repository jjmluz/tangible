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
    val contentId: String? = null
) {
    val isPlaying: Boolean get() = state == "playing"
    val isAvailable: Boolean get() = state != "unavailable" && state != "off"
    val hasMedia: Boolean get() = !title.isNullOrBlank()
    /** Something is loaded and can be resumed or paused. */
    val isActive: Boolean get() = hasMedia && (state == "playing" || state == "paused")

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
 * Talks to Music Assistant through Home Assistant's REST API, using a long-lived access token.
 * Playback goes through the `music_assistant.play_media` service, so Music Assistant handles
 * the streaming provider (Spotify) and the speakers, including groups.
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

    /**
     * Lists the media players that belong to the Music Assistant integration. HA's REST API has
     * no entity-registry endpoint, so this renders a template that uses `integration_entities`.
     */
    suspend fun fetchPlayers(baseUrl: String, token: String): Result<List<MusicAssistantPlayer>> =
        call(baseUrl, token) { url ->
            val body = JsonObject().apply { addProperty("template", PLAYERS_TEMPLATE) }
            val request = authorized(url + "/api/template", token)
                .post(body.toString().toRequestBody(json))
                .build()
            execute(request) { text ->
                val array = gson.fromJson(text.trim().ifEmpty { "[]" }, JsonArray::class.java)
                array.mapNotNull { element ->
                    val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    val entityId = obj.get("entity_id")?.asString ?: return@mapNotNull null
                    MusicAssistantPlayer(
                        entityId = entityId,
                        name = obj.get("name")?.takeUnless { it.isJsonNull }?.asString ?: entityId,
                        state = obj.get("state")?.takeUnless { it.isJsonNull }?.asString ?: ""
                    )
                }.sortedBy { it.name.lowercase() }
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
        val state = obj.get("state")?.asString ?: "unknown"
        val duration = num("media_duration")
        var position = num("media_position")
        var positionUpdatedAt = parseTime(str("media_position_updated_at"))
        // HA only refreshes the position now and then. When a player pauses, the reported position
        // can be from before the pause (even the start of the track), so count forward from that
        // update to the moment the state changed to paused.
        val stateChangedAt = parseTime(obj.get("last_changed")?.takeUnless { it.isJsonNull }?.asString)
        if (state == "paused" && position != null && positionUpdatedAt != null &&
            stateChangedAt != null && stateChangedAt > positionUpdatedAt
        ) {
            val played = position + (stateChangedAt - positionUpdatedAt) / 1000.0
            position = duration?.let { played.coerceAtMost(it) } ?: played
            positionUpdatedAt = stateChangedAt
        }
        return PlayerStatus(
            entityId = entityId,
            name = str("friendly_name") ?: entityId,
            state = state,
            title = str("media_title"),
            artist = str("media_artist"),
            album = str("media_album_name"),
            artworkUrl = picture,
            durationSec = duration,
            positionSec = position,
            positionUpdatedAtMs = positionUpdatedAt,
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
        private val PLAYERS_TEMPLATE = """
            {%- set ns = namespace(players=[]) -%}
            {%- for e in integration_entities('music_assistant') if e.startswith('media_player.') -%}
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
            404 -> "Not found (404). Is Music Assistant set up in Home Assistant, and does the player still exist?"
            else -> {
                val message = runCatching {
                    Gson().fromJson(body, JsonObject::class.java)?.get("message")?.asString
                }.getOrNull()
                "HTTP $code" + (message ?: body.take(200)).let { if (it.isBlank()) "" else ": $it" }
            }
        }
    }
}
