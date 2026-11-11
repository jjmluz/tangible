package com.jjmluz.tangible.data

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

/** A country the Streaming Availability API covers, with the subscription services it knows there. */
data class Region(
    /** ISO 3166-1 alpha-2, lowercase, e.g. `pt`. */
    val code: String,
    val name: String,
    /** The services' API IDs and names, in the API's order. */
    val services: List<RegionService>
)

data class RegionService(val apiId: String, val name: String)

/** What a movie search uses from Settings. */
data class MovieSearchSettings(
    val region: String,
    /** The services the user has; Stremio included when it's on. */
    val services: Set<MovieService>
)

class StreamingAvailabilityException(message: String) : IOException(message)

/**
 * Searches movies and finds which streaming services they're on, using the Streaming Availability
 * API by Movie of the Night. The API key is the user's own, from Settings.
 */
class StreamingAvailabilityRepository(
    private val apiKey: () -> String,
    private val baseUrl: String = "https://api.movieofthenight.com/v4/"
) {
    private val api: StreamingAvailabilityApi by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("X-API-Key", apiKey().trim()).build())
            }
            .build()

        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StreamingAvailabilityApi::class.java)
    }

    /** Movies matching the title, each with where it can be watched among the user's services. */
    suspend fun searchMovies(query: String, settings: MovieSearchSettings): Result<List<MovieSearchResult>> =
        call("Movie search") {
            api.searchByTitle(query, settings.region).mapNotNull { resultFrom(it, settings) }
        }

    /** The countries the API covers, sorted by name. Also tells whether the API key works. */
    suspend fun regions(): Result<List<Region>> = call("Region lookup") {
        regionsFrom(api.countries())
    }

    /** A working link to the movie's poster, or null when the movie has none or the lookup fails. */
    suspend fun posterUrl(tmdbId: Int): String? =
        call("Poster lookup") { api.getShow("movie/$tmdbId").poster(POSTER_WIDTH) }.getOrNull()

    private suspend fun <T> call(what: String, block: suspend () -> T): Result<T> {
        if (apiKey().isBlank()) {
            return Result.failure(StreamingAvailabilityException(MISSING_KEY))
        }
        return try {
            Result.success(block())
        } catch (e: HttpException) {
            Log.w("Tangible", "$what failed: HTTP ${e.code()}")
            Result.failure(StreamingAvailabilityException(describeError(e.code())))
        } catch (e: Exception) {
            Log.w("Tangible", "$what failed: ${e.javaClass.simpleName}: ${e.message}")
            Result.failure(StreamingAvailabilityException("Couldn't reach the Streaming Availability API"))
        }
    }

    companion object {
        const val MISSING_KEY = "Add your Streaming Availability API key in Settings → Streaming services"

        /** Where users get an API key (free plan, no payment details). */
        const val KEY_SIGN_UP_URL = "https://developers.movieofthenight.com/"

        /** The page the API's terms ask apps to link to, with the attribution. */
        const val ATTRIBUTION_URL = "https://www.movieofthenight.com/about/api"

        private const val THUMB_WIDTH = "w240"
        private const val POSTER_WIDTH = "w480"

        internal fun describeError(code: Int): String = when (code) {
            401, 403 -> "The Streaming Availability API key isn't valid"
            429 -> "The Streaming Availability API key has used up its requests for now"
            400 -> "The Streaming Availability API doesn't cover the region in Settings"
            else -> "The Streaming Availability API answered with an error ($code)"
        }

        private fun ShowApiResponse.poster(width: String): String? =
            imageSet?.verticalPoster?.get(width)?.takeIf { it.isNotBlank() }

        /** Null for results that aren't a movie with a TMDB ID and a title. */
        internal fun resultFrom(response: ShowApiResponse, settings: MovieSearchSettings): MovieSearchResult? {
            val tmdbId = response.tmdbId?.removePrefix("movie/")?.toIntOrNull() ?: return null
            val title = response.title?.takeIf { it.isNotBlank() } ?: return null
            return MovieSearchResult(
                tmdbId = tmdbId,
                title = title,
                year = response.releaseYear?.toString().orEmpty(),
                thumbUrl = response.poster(THUMB_WIDTH),
                posterUrl = response.poster(POSTER_WIDTH),
                options = optionsFrom(response, settings)
            )
        }

        /**
         * Keeps what's included in a subscription (not rentals, purchases or paid add-on channels)
         * in the region, on the user's services, and adds Stremio when it's on and the movie has an
         * IMDb ID. Most preferred service first.
         */
        internal fun optionsFrom(response: ShowApiResponse, settings: MovieSearchSettings): List<MovieOption> {
            val subscriptions = response.streamingOptions?.get(settings.region).orEmpty()
                .filter { it.type == "subscription" }
                .mapNotNull { dto ->
                    val service = MovieService.fromApiId(dto.service.id) ?: return@mapNotNull null
                    service.extractId(dto.link)?.let { MovieOption(service, it) }
                }
            val stremio = response.imdbId
                ?.let { MovieService.STREMIO.extractId(it) }
                ?.let { MovieOption(MovieService.STREMIO, it) }

            return (subscriptions + listOfNotNull(stremio))
                .filter { it.service in settings.services }
                .distinctBy { it.service }
                .sortedBy { it.service.ordinal }
        }

        internal fun regionsFrom(countries: Map<String, CountryDto>): List<Region> =
            countries.mapNotNull { (key, dto) ->
                val code = (dto.countryCode ?: key).lowercase().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Region(
                    code = code,
                    name = dto.name?.takeIf { it.isNotBlank() } ?: code.uppercase(),
                    services = dto.services.orEmpty()
                        .filter { it.streamingOptionTypes?.get("subscription") == true }
                        .mapNotNull { s ->
                            val id = s.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            RegionService(id, s.name?.takeIf { it.isNotBlank() } ?: id)
                        }
                )
            }.sortedBy { it.name }
    }
}
