package com.example.tangible.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppleMusicRepository {
    private val api: AppleMusicApi

    init {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logger)
            .build()

        api = Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AppleMusicApi::class.java)
    }

    suspend fun searchAlbum(albumTitle: String, artistName: String): String? {
        return try {
            val query = "$artistName $albumTitle"
            val response = api.search(query = query)
            response.results.firstOrNull()?.collectionViewUrl
        } catch (e: Exception) {
            null
        }
    }
}
