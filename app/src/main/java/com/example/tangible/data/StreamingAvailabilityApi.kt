package com.example.tangible.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface StreamingAvailabilityApi {
    @GET("countries")
    suspend fun getCountries(): Map<String, CountryDto>

    // id format: "movie/{tmdbId}" — encoded=true prevents Retrofit from percent-encoding the slash
    @GET("shows/{id}")
    suspend fun getShow(
        @Path("id", encoded = true) id: String,
        @Query("country") country: String
    ): ShowApiResponse
}

data class CountryDto(
    @SerializedName("countryCode") val countryCode: String,
    @SerializedName("name") val name: String,
    @SerializedName("services") val services: List<ServiceDto>
)

data class ServiceDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("imageSet") val images: ServiceImagesDto,
    @SerializedName("streamingOptionTypes") val supportedStreamingTypes: Map<String, Boolean>
)

data class ServiceImagesDto(
    @SerializedName("lightThemeImage") val lightThemeImage: String,
    @SerializedName("darkThemeImage") val darkThemeImage: String
)

data class ShowApiResponse(
    @SerializedName("streamingOptions") val streamingOptions: Map<String, List<StreamingOptionDto>>
)

data class StreamingOptionDto(
    @SerializedName("service") val service: ServiceInfoDto,
    @SerializedName("type") val type: String,
    @SerializedName("link") val link: String,
    @SerializedName("videoLink") val videoLink: String?
)

data class ServiceInfoDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String
)

// Public domain objects used by UI
data class StreamingOption(
    val serviceId: String,
    val serviceName: String,
    val type: String,
    val link: String,
    val allTypes: List<String> = emptyList()
)

data class MovieStreamingResult(
    val options: List<StreamingOption>,
    val encodedServiceIds: Set<String>,
    val tagUrl: String,
    val byteCount: Int
)
