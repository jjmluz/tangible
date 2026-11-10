package com.example.tangible.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

interface SpotifyApi {
    @FormUrlEncoded
    @POST("https://accounts.spotify.com/api/token")
    suspend fun getToken(
        @Field("grant_type") grantType: String = "client_credentials",
        @Header("Authorization") authHeader: String
    ): SpotifyTokenResponse

    @GET("v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String = "album",
        @Query("limit") limit: Int = 10,
        @Header("Authorization") authHeader: String
    ): SpotifySearchResponse
}

data class SpotifyTokenResponse(
    @SerializedName("access_token") val accessToken: String
)

data class SpotifySearchResponse(
    @SerializedName("albums") val albums: SpotifyAlbumResponse
)

data class SpotifyAlbumResponse(
    @SerializedName("items") val items: List<SpotifyAlbum>
)

data class SpotifyAlbum(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("artists") val artists: List<SpotifyArtist>? = null,
    // Largest first (640, 300 and 64 px).
    @SerializedName("images") val images: List<SpotifyImage>? = null,
    @SerializedName("release_date") val releaseDate: String? = null
) {
    val artistName: String get() = artists.orEmpty().joinToString(", ") { it.name }
    val coverUrl: String? get() = images?.firstOrNull()?.url
    val thumbUrl: String? get() = images?.lastOrNull()?.url
    val year: String get() = releaseDate.orEmpty().take(4)
}

data class SpotifyArtist(
    @SerializedName("name") val name: String
)

data class SpotifyImage(
    @SerializedName("url") val url: String
)
