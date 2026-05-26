package com.example.tangible.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface TmdbApi {
    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbSearchResponse
}

data class TmdbSearchResponse(
    @SerializedName("results") val results: List<TmdbMovieResult>
)

data class TmdbMovieResult(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("overview") val overview: String,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("release_date") val releaseDate: String
) {
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w92$it" }
    val largePosterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
    val year: String get() = releaseDate.take(4)
}
