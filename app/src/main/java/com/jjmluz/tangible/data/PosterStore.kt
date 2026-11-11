package com.jjmluz.tangible.data

import android.content.SharedPreferences
import android.util.Log
import com.jjmluz.tangible.SavedTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Keeps a copy of each movie tag's poster on the phone, in [dir] as `<TMDB id>.jpg`.
 *
 * The poster links from the Streaming Availability API are signed and stop working after some
 * months, so the link saved with a tag (and in a backup) can't be relied on. When a poster isn't
 * on the phone and its saved link is dead, a new link is looked up with [lookUpPosterUrl].
 */
class PosterStore(
    private val dir: File,
    private val prefs: SharedPreferences,
    private val lookUpPosterUrl: suspend (tmdbId: Int) -> String?,
    private val client: OkHttpClient = OkHttpClient(),
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()

    /** The poster on the phone for each movie tag that has one, by tag ID. */
    fun stored(tags: List<SavedTag>): Map<String, File> =
        tags.mapNotNull { tag ->
            val file = tmdbId(tag)?.let(::fileFor)?.takeIf { it.isFile } ?: return@mapNotNull null
            tag.id to file
        }.toMap()

    /**
     * Fetches the posters that aren't on the phone yet and deletes those of movies that have no
     * tag any more. Failures are quiet: the poster is tried again on the next sync.
     */
    suspend fun sync(tags: List<SavedTag>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val movies = tags.mapNotNull { tag -> tmdbId(tag)?.let { it to tag } }.toMap()
            dir.mkdirs()
            dir.listFiles()?.forEach { file ->
                if (file.nameWithoutExtension.toIntOrNull() !in movies.keys) file.delete()
            }
            for ((tmdbId, tag) in movies) {
                val file = fileFor(tmdbId)
                if (file.isFile) continue
                val saved = tag.imageUrl?.let { download(it, file) } ?: Download.GONE
                // Only a dead link is worth a lookup: when the phone is offline it would fail too.
                if (saved == Download.GONE && lookUpAllowed(tmdbId)) {
                    prefs.edit().putLong(lookUpKey(tmdbId), now()).apply()
                    lookUpPosterUrl(tmdbId)?.let { download(it, file) }
                }
            }
        }
    }

    private fun fileFor(tmdbId: Int) = File(dir, "$tmdbId.jpg")

    private fun tmdbId(tag: SavedTag): Int? = if (tag.type == "MOVIE") MovieTag.tmdbId(tag.url) else null

    /** Lookups count against the API's monthly quota, so a movie gets one a day at most. */
    private fun lookUpAllowed(tmdbId: Int): Boolean =
        now() - prefs.getLong(lookUpKey(tmdbId), 0L) >= LOOK_UP_INTERVAL_MS

    private fun lookUpKey(tmdbId: Int) = "poster_lookup_$tmdbId"

    private enum class Download { SAVED, GONE, UNREACHABLE }

    private fun download(url: String, file: File): Download {
        val temp = File(dir, "${file.name}.tmp")
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return Download.GONE
                temp.outputStream().use { out -> response.body!!.byteStream().copyTo(out) }
            }
            if (temp.length() > 0 && temp.renameTo(file)) Download.SAVED else Download.GONE
        } catch (e: IOException) {
            Log.w("Tangible", "Couldn't download a poster: ${e.message}")
            Download.UNREACHABLE
        } catch (e: IllegalArgumentException) {
            // Not a link OkHttp can open.
            Download.GONE
        } finally {
            temp.delete()
        }
    }

    private companion object {
        const val LOOK_UP_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
