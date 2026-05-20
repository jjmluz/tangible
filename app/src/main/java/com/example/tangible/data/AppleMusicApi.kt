package com.example.tangible.data

import retrofit2.http.GET
import retrofit2.http.Query

data class iTunesSearchResponse(
    val resultCount: Int,
    val results: List<iTunesSearchResult>
)

data class iTunesSearchResult(
    val wrapperType: String,
    val collectionType: String?,
    val artistName: String,
    val collectionName: String,
    val collectionViewUrl: String,
    val collectionId: Long
)

interface AppleMusicApi {
    @GET("search")
    suspend fun search(
        @Query("term") query: String,
        @Query("entity") entity: String = "album",
        @Query("limit") limit: Int = 1
    ): iTunesSearchResponse
}
