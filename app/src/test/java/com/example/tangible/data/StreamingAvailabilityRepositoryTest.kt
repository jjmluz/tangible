package com.example.tangible.data

import org.junit.Assert.assertEquals
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
}
