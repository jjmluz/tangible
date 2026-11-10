package com.example.tangible.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface StreamingAvailabilityApi {
    @GET("shows/search/title")
    suspend fun searchByTitle(
        @Query("title") title: String,
        @Query("country") country: String,
        @Query("show_type") showType: String = "movie"
    ): List<ShowApiResponse>

    // id format: "movie/{tmdbId}" — encoded=true prevents Retrofit from percent-encoding the slash
    @GET("shows/{id}")
    suspend fun getShow(
        @Path("id", encoded = true) id: String,
        @Query("country") country: String
    ): ShowApiResponse
}

data class ShowApiResponse(
    @SerializedName("imdbId") val imdbId: String?,
    @SerializedName("streamingOptions") val streamingOptions: Map<String, List<StreamingOptionDto>>?,
    /** `movie/<TMDB id>`. */
    @SerializedName("tmdbId") val tmdbId: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("releaseYear") val releaseYear: Int? = null,
    @SerializedName("imageSet") val imageSet: ImageSetDto? = null
)

data class ImageSetDto(
    /** Poster links by width (`w240`, `w360`, `w480`, `w600`, `w720`). They're signed and expire after some months. */
    @SerializedName("verticalPoster") val verticalPoster: Map<String, String>? = null
)

data class StreamingOptionDto(
    @SerializedName("service") val service: ServiceInfoDto,
    @SerializedName("type") val type: String,
    @SerializedName("link") val link: String
)

data class ServiceInfoDto(
    @SerializedName("id") val id: String
)
