package com.example.tangible.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface DiscogsApi {
    @GET("database/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String = "master",
        @Query("per_page") perPage: Int = 10,
        @Header("Authorization") authHeader: String
    ): DiscogsSearchResponse
}

data class DiscogsSearchResponse(
    @SerializedName("results") val results: List<DiscogsSearchResult>
)

data class DiscogsSearchResult(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("thumb") val thumb: String,
    @SerializedName("cover_image") val coverImage: String? = null,
    @SerializedName("uri") val uri: String
) {
    val fullUrl: String get() = "https://www.discogs.com$uri"
    val displayImage: String get() = if (!coverImage.isNullOrBlank()) coverImage else thumb
    val artistName: String get() = if (title.contains(" - ")) title.substringBefore(" - ").trim() else ""
    val albumName: String get() = if (title.contains(" - ")) title.substringAfter(" - ").trim() else title
}
