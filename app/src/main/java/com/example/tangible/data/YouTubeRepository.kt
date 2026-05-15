package com.example.tangible.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class YouTubeRepository(private val apiKey: String) {
    private val api: YouTubeApi

    init {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logger)
            .build()

        api = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(YouTubeApi::class.java)
    }

    suspend fun searchAlbum(albumTitle: String, artistName: String): String? {
        if (apiKey.isBlank()) return null
        
        return try {
            val query = "$artistName $albumTitle album"
            val response = api.search(query = query, apiKey = apiKey)
            
            // Try to find a "Topic" channel or official artist channel
            val officialResult = response.items.find { 
                it.snippet.channelTitle.contains("Topic", ignoreCase = true) || 
                it.snippet.channelTitle.contains(artistName, ignoreCase = true)
            } ?: response.items.firstOrNull()

            officialResult?.id?.playlistId?.let { playlistId ->
                // Construct an autoplaying watch link
                "https://music.youtube.com/watch?v=0&list=$playlistId&autoplay=1"
            }
        } catch (e: Exception) {
            null
        }
    }
}
