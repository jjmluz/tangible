package com.example.tangible.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApi {
    @GET("youtube/v3/search")
    suspend fun search(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "playlist",
        @Query("maxResults") maxResults: Int = 5,
        @Query("key") apiKey: String
    ): YouTubeSearchResponse
}

data class YouTubeSearchResponse(
    @SerializedName("items") val items: List<YouTubeSearchResult>
)

data class YouTubeSearchResult(
    @SerializedName("id") val id: YouTubeResourceId,
    @SerializedName("snippet") val snippet: YouTubeSnippet
)

data class YouTubeResourceId(
    @SerializedName("playlistId") val playlistId: String?
)

data class YouTubeSnippet(
    @SerializedName("title") val title: String,
    @SerializedName("channelTitle") val channelTitle: String
)
