package com.example.tangible.data

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Searches movies and finds which streaming services they're on, using the Streaming Availability API. */
class StreamingAvailabilityRepository(private val apiKey: String) {
    private val api: StreamingAvailabilityApi by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("X-API-Key", apiKey).build())
            }
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.movieofthenight.com/v4/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StreamingAvailabilityApi::class.java)
    }

    /** Movies matching the title, each with where it can be watched. Empty when the search fails. */
    suspend fun searchMovies(query: String): List<MovieSearchResult> {
        if (apiKey.isBlank()) return emptyList()
        return try {
            api.searchByTitle(query, REGION).mapNotNull { resultFrom(it) }
        } catch (e: Exception) {
            Log.e("Tangible", "Movie search failed", e)
            emptyList()
        }
    }

    /** A working link to the movie's poster, or null when the movie has none or the lookup fails. */
    suspend fun posterUrl(tmdbId: Int): String? {
        if (apiKey.isBlank()) return null
        return try {
            api.getShow("movie/$tmdbId", REGION).poster(POSTER_WIDTH)
        } catch (e: Exception) {
            Log.e("Tangible", "Poster lookup failed for tmdb=$tmdbId", e)
            null
        }
    }

    companion object {
        /** Availability is looked up for Portugal. */
        const val REGION = "pt"

        private const val THUMB_WIDTH = "w240"
        private const val POSTER_WIDTH = "w480"

        private fun ShowApiResponse.poster(width: String): String? =
            imageSet?.verticalPoster?.get(width)?.takeIf { it.isNotBlank() }

        /** Null for results that aren't a movie with a TMDB ID and a title. */
        internal fun resultFrom(response: ShowApiResponse): MovieSearchResult? {
            val tmdbId = response.tmdbId?.removePrefix("movie/")?.toIntOrNull() ?: return null
            val title = response.title?.takeIf { it.isNotBlank() } ?: return null
            return MovieSearchResult(
                tmdbId = tmdbId,
                title = title,
                year = response.releaseYear?.toString().orEmpty(),
                thumbUrl = response.poster(THUMB_WIDTH),
                posterUrl = response.poster(POSTER_WIDTH),
                options = optionsFrom(response)
            )
        }

        /**
         * Keeps what's included in a subscription (not rentals, purchases or paid add-on channels)
         * on the services in [MovieService], and adds Stremio when the movie has an IMDb ID.
         */
        internal fun optionsFrom(response: ShowApiResponse): List<MovieOption> {
            val subscriptions = response.streamingOptions?.get(REGION).orEmpty()
                .filter { it.type == "subscription" }
                .mapNotNull { dto ->
                    val service = MovieService.fromApiId(dto.service.id) ?: return@mapNotNull null
                    service.extractId(dto.link)?.let { MovieOption(service, it) }
                }
            val stremio = response.imdbId
                ?.let { MovieService.STREMIO.extractId(it) }
                ?.let { MovieOption(MovieService.STREMIO, it) }

            return (subscriptions + listOfNotNull(stremio))
                .distinctBy { it.service }
                .sortedBy { it.service.ordinal }
        }
    }
}
