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
    @SerializedName("uri") val uri: String,
    @SerializedName("artists") val artists: List<SpotifyArtist>,
    @SerializedName("images") val images: List<SpotifyImage>
) {
    val displayTitle: String get() = "${artists.firstOrNull()?.name ?: "Unknown"} - $name"
}

data class SpotifyArtist(
    @SerializedName("name") val name: String
)

data class SpotifyImage(
    @SerializedName("url") val url: String
)
