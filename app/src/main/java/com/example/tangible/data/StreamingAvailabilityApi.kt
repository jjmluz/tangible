package com.example.tangible.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface StreamingAvailabilityApi {
    // id format: "movie/{tmdbId}" — encoded=true prevents Retrofit from percent-encoding the slash
    @GET("shows/{id}")
    suspend fun getShow(
        @Path("id", encoded = true) id: String,
        @Query("country") country: String
    ): ShowApiResponse
}

data class ShowApiResponse(
    @SerializedName("imdbId") val imdbId: String?,
    @SerializedName("streamingOptions") val streamingOptions: Map<String, List<StreamingOptionDto>>?
)

data class StreamingOptionDto(
    @SerializedName("service") val service: ServiceInfoDto,
    @SerializedName("type") val type: String,
    @SerializedName("link") val link: String
)

data class ServiceInfoDto(
    @SerializedName("id") val id: String
)
