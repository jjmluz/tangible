package com.example.tangible.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingAvailabilityRepositoryTest {

    private fun option(service: String, type: String, link: String) =
        StreamingOptionDto(ServiceInfoDto(service), type, link)

    private fun response(imdbId: String?, vararg options: StreamingOptionDto) =
        ShowApiResponse(imdbId, mapOf("pt" to options.toList()))

    @Test
    fun `subscription options become content IDs, most preferred service first`() {
        val options = StreamingAvailabilityRepository.optionsFrom(
            response(
                "tt1160419",
                option("prime", "subscription", "https://app.primevideo.com/detail?gti=amzn1.dv.gti.a8025548-3908-4562-8896-8fffd945cfb6"),
                option("hbo", "subscription", "https://play.hbomax.com/movie/e7dc7b3a-a494-4ef1-8107-f4308aa6bbf7"),
                option("disney", "subscription", "https://www.disneyplus.com/browse/entity-f6174ebf-cb92-453c-a52b-62bb3576e402"),
                option("netflix", "subscription", "https://www.netflix.com/title/80175798")
            )
        )

        assertEquals(
            listOf(
                MovieOption(MovieService.NETFLIX, "80175798"),
                MovieOption(MovieService.DISNEY, "f6174ebf-cb92-453c-a52b-62bb3576e402"),
                MovieOption(MovieService.HBO, "e7dc7b3a-a494-4ef1-8107-f4308aa6bbf7"),
                MovieOption(MovieService.PRIME, "a8025548-3908-4562-8896-8fffd945cfb6"),
                MovieOption(MovieService.STREMIO, "tt1160419")
            ),
            options
        )
    }

    @Test
    fun `rentals, purchases, add-ons and other services are left out`() {
        val options = StreamingAvailabilityRepository.optionsFrom(
            response(
                "tt0114709",
                option("prime", "rent", "https://www.primevideo.com/detail/0N4M7MPARPRVDWEMDJILO2I4TG?ref_=atv_dp"),
                option("prime", "buy", "https://www.primevideo.com/detail/0N4M7MPARPRVDWEMDJILO2I4TG?ref_=atv_dp"),
                option("prime", "addon", "https://app.primevideo.com/detail?gti=amzn1.dv.gti.11111111-2222-3333-4444-555555555555"),
                option("apple", "subscription", "https://tv.apple.com/pt/movie/toy-story/umc.cmc.qrdx9s4xrk47f4taowt4tx82")
            )
        )

        assertEquals(listOf(MovieOption(MovieService.STREMIO, "tt0114709")), options)
    }

    @Test
    fun `a service listed twice appears once`() {
        val options = StreamingAvailabilityRepository.optionsFrom(
            response(
                null,
                option("hbo", "subscription", "https://play.hbomax.com/movie/8f264e31-0dbe-4f05-a144-03c83021d831"),
                option("hbo", "subscription", "https://play.hbomax.com/movie/80bc4915-c826-499f-9961-b422b17559b6")
            )
        )

        assertEquals(listOf(MovieOption(MovieService.HBO, "8f264e31-0dbe-4f05-a144-03c83021d831")), options)
    }

    @Test
    fun `no options when the movie isn't in Portugal and has no IMDb ID`() {
        assertEquals(
            emptyList<MovieOption>(),
            StreamingAvailabilityRepository.optionsFrom(ShowApiResponse(null, null))
        )
        assertEquals(
            emptyList<MovieOption>(),
            StreamingAvailabilityRepository.optionsFrom(
                ShowApiResponse(null, mapOf("us" to listOf(option("netflix", "subscription", "https://www.netflix.com/title/1"))))
            )
        )
    }

    @Test
    fun `a search result carries the movie, its posters and where to watch it`() {
        val result = StreamingAvailabilityRepository.resultFrom(
            ShowApiResponse(
                imdbId = "tt1160419",
                streamingOptions = mapOf(
                    "pt" to listOf(option("hbo", "subscription", "https://play.hbomax.com/movie/e7dc7b3a-a494-4ef1-8107-f4308aa6bbf7"))
                ),
                tmdbId = "movie/438631",
                title = "Dune",
                releaseYear = 2021,
                imageSet = ImageSetDto(mapOf("w240" to "https://cdn/240.jpg?Expires=1", "w480" to "https://cdn/480.jpg?Expires=1"))
            )
        )

        assertEquals(
            MovieSearchResult(
                tmdbId = 438631,
                title = "Dune",
                year = "2021",
                thumbUrl = "https://cdn/240.jpg?Expires=1",
                posterUrl = "https://cdn/480.jpg?Expires=1",
                options = listOf(
                    MovieOption(MovieService.HBO, "e7dc7b3a-a494-4ef1-8107-f4308aa6bbf7"),
                    MovieOption(MovieService.STREMIO, "tt1160419")
                )
            ),
            result
        )
    }

    @Test
    fun `a search result without a year or posters still works`() {
        val result = StreamingAvailabilityRepository.resultFrom(
            ShowApiResponse(imdbId = null, streamingOptions = null, tmdbId = "movie/841", title = "Dune")
        )

        assertEquals(MovieSearchResult(841, "Dune", "", null, null, emptyList()), result)
    }

    @Test
    fun `results that aren't a movie with a title are skipped`() {
        assertNull(StreamingAvailabilityRepository.resultFrom(ShowApiResponse(null, null, tmdbId = "tv/1399", title = "Game of Thrones")))
        assertNull(StreamingAvailabilityRepository.resultFrom(ShowApiResponse(null, null, tmdbId = null, title = "Dune")))
        assertNull(StreamingAvailabilityRepository.resultFrom(ShowApiResponse(null, null, tmdbId = "movie/841", title = "")))
    }
}
