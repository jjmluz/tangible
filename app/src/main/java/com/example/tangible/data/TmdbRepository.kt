package com.example.tangible.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TmdbRepository(private val apiKey: String) {
    private val api: TmdbApi

    init {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logger)
            .build()

        api = Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmdbApi::class.java)
    }

    suspend fun searchMovies(query: String): List<TmdbMovieResult> {
        if (apiKey.isBlank()) return emptyList()
        return try {
            api.searchMovies(query = query, apiKey = apiKey).results
        } catch (e: Exception) {
            emptyList()
        }
    }
}
