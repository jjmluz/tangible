package com.example.tangible.data

import android.util.Log
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Finds which streaming services a movie is on, using the Streaming Availability API. */
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

    /**
     * Where the movie can be watched, most preferred service first. Empty when it's on none of
     * them and has no IMDb ID for Stremio, or when the lookup fails.
     */
    suspend fun getMovieOptions(tmdbId: Int): List<MovieOption> {
        if (apiKey.isBlank()) return emptyList()
        return try {
            optionsFrom(api.getShow("movie/$tmdbId", REGION))
        } catch (e: Exception) {
            Log.e("Tangible", "Streaming availability lookup failed for tmdb=$tmdbId", e)
            emptyList()
        }
    }

    companion object {
        /** Availability is looked up for Portugal. */
        const val REGION = "pt"

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
