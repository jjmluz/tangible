package com.example.tangible.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DiscogsRepository(private val token: String) {
    private val api: DiscogsApi

    init {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logger)
            .build()

        api = Retrofit.Builder()
            .baseUrl("https://api.discogs.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DiscogsApi::class.java)
    }

    suspend fun searchAlbums(query: String): List<DiscogsSearchResult> {
        if (token.isBlank()) return emptyList()
        return try {
            val response = api.search(query = query, authHeader = "Discogs token=$token")
            response.results
        } catch (e: Exception) {
            emptyList()
        }
    }
}
