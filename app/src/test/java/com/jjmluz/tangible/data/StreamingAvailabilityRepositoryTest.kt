package com.jjmluz.tangible.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingAvailabilityRepositoryTest {

    private val all = MovieSearchSettings("pt", MovieService.entries.toSet())

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
                option("netflix", "subscription", "https://www.netflix.com/title/80175798"),
                option("apple", "subscription", "https://tv.apple.com/pt/movie/f1-the-movie/umc.cmc.3t6dvnnr87zwd4wmvpdx5came")
            ), all
        )

        assertEquals(
            listOf(
                MovieOption(MovieService.NETFLIX, "80175798"),
                MovieOption(MovieService.DISNEY, "f6174ebf-cb92-453c-a52b-62bb3576e402"),
                MovieOption(MovieService.HBO, "e7dc7b3a-a494-4ef1-8107-f4308aa6bbf7"),
                MovieOption(MovieService.PRIME, "a8025548-3908-4562-8896-8fffd945cfb6"),
                MovieOption(MovieService.APPLE, "umc.cmc.3t6dvnnr87zwd4wmvpdx5came"),
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
                option("apple", "buy", "https://tv.apple.com/pt/movie/toy-story/umc.cmc.qrdx9s4xrk47f4taowt4tx82"),
                option("hulu", "subscription", "https://www.hulu.com/movie/toy-story-8a0b6f8c-1b2a-4c7d-9e3f-0a1b2c3d4e5f")
            ), all
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
            ), all
        )

        assertEquals(listOf(MovieOption(MovieService.HBO, "8f264e31-0dbe-4f05-a144-03c83021d831")), options)
    }

    @Test
    fun `no options when the movie isn't in Portugal and has no IMDb ID`() {
        assertEquals(
            emptyList<MovieOption>(),
            StreamingAvailabilityRepository.optionsFrom(ShowApiResponse(null, null), all)
        )
        assertEquals(
            emptyList<MovieOption>(),
            StreamingAvailabilityRepository.optionsFrom(
                ShowApiResponse(null, mapOf("us" to listOf(option("netflix", "subscription", "https://www.netflix.com/title/1")))),
                all
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
            ), all
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
            ShowApiResponse(imdbId = null, streamingOptions = null, tmdbId = "movie/841", title = "Dune"), all
        )

        assertEquals(MovieSearchResult(841, "Dune", "", null, null, emptyList()), result)
    }

    @Test
    fun `results that aren't a movie with a title are skipped`() {
        assertNull(StreamingAvailabilityRepository.resultFrom(ShowApiResponse(null, null, tmdbId = "tv/1399", title = "Game of Thrones"), all))
        assertNull(StreamingAvailabilityRepository.resultFrom(ShowApiResponse(null, null, tmdbId = null, title = "Dune"), all))
        assertNull(StreamingAvailabilityRepository.resultFrom(ShowApiResponse(null, null, tmdbId = "movie/841", title = ""), all))
    }

    @Test
    fun `options are limited to the user's services`() {
        val options = StreamingAvailabilityRepository.optionsFrom(
            response(
                "tt1160419",
                option("netflix", "subscription", "https://www.netflix.com/title/80175798"),
                option("hbo", "subscription", "https://play.hbomax.com/movie/e7dc7b3a-a494-4ef1-8107-f4308aa6bbf7")
            ),
            MovieSearchSettings("pt", setOf(MovieService.HBO))
        )

        assertEquals(listOf(MovieOption(MovieService.HBO, "e7dc7b3a-a494-4ef1-8107-f4308aa6bbf7")), options)
    }

    @Test
    fun `options come from the region in Settings`() {
        val response = ShowApiResponse(
            null,
            mapOf(
                "pt" to listOf(option("hbo", "subscription", "https://play.hbomax.com/movie/e7dc7b3a-a494-4ef1-8107-f4308aa6bbf7")),
                "us" to listOf(option("netflix", "subscription", "https://www.netflix.com/title/80175798"))
            )
        )

        assertEquals(
            listOf(MovieOption(MovieService.NETFLIX, "80175798")),
            StreamingAvailabilityRepository.optionsFrom(response, all.copy(region = "us"))
        )
    }

    @Test
    fun `regions keep only subscription services and are sorted by name`() {
        fun service(id: String, name: String, subscription: Boolean) =
            CountryServiceDto(id, name, mapOf("subscription" to subscription, "rent" to !subscription))

        val regions = StreamingAvailabilityRepository.regionsFrom(
            mapOf(
                "pt" to CountryDto("pt", "Portugal", listOf(service("netflix", "Netflix", true), service("hulu", "Hulu", false))),
                "de" to CountryDto("DE", "Germany", listOf(service("wow", "WOW", true)))
            )
        )

        assertEquals(
            listOf(
                Region("de", "Germany", listOf(RegionService("wow", "WOW"))),
                Region("pt", "Portugal", listOf(RegionService("netflix", "Netflix")))
            ),
            regions
        )
    }

    @Test
    fun `the services Tangible can open in a region always include Stremio`() {
        val portugal = Region("pt", "Portugal", listOf(RegionService("netflix", "Netflix"), RegionService("hulu", "Hulu")))

        assertEquals(listOf(MovieService.NETFLIX, MovieService.STREMIO), MovieService.availableIn(portugal))
        assertEquals(MovieService.entries, MovieService.availableIn(null))
    }

    @Test
    fun `API errors are explained`() {
        assertEquals("The Streaming Availability API key isn't valid", StreamingAvailabilityRepository.describeError(401))
        assertEquals("The Streaming Availability API key isn't valid", StreamingAvailabilityRepository.describeError(403))
        assertEquals(
            "The Streaming Availability API key has used up its requests for now",
            StreamingAvailabilityRepository.describeError(429)
        )
    }

    @Test
    fun `nothing is requested without an API key`() = kotlinx.coroutines.runBlocking {
        val repository = StreamingAvailabilityRepository(apiKey = { "" }, baseUrl = "http://localhost:1/")

        val result = repository.searchMovies("dune", all)

        assertEquals(StreamingAvailabilityRepository.MISSING_KEY, result.exceptionOrNull()?.message)
        assertEquals(null, repository.posterUrl(438631))
    }
}
