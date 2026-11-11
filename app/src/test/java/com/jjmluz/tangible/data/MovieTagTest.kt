package com.jjmluz.tangible.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MovieTagTest {

    @Test
    fun `each service builds the link that opens on the TV`() {
        assertEquals(
            "https://www.netflix.com/title/80175798?source=30",
            MovieService.NETFLIX.tvLink("80175798")
        )
        assertEquals(
            "https://www.disneyplus.com/browse/entity-f6174ebf-cb92-453c-a52b-62bb3576e402",
            MovieService.DISNEY.tvLink("f6174ebf-cb92-453c-a52b-62bb3576e402")
        )
        assertEquals(
            "https://play.hbomax.com/movie/e7dc7b3a-a494-4ef1-8107-f4308aa6bbf7",
            MovieService.HBO.tvLink("e7dc7b3a-a494-4ef1-8107-f4308aa6bbf7")
        )
        assertEquals(
            "https://app.primevideo.com/detail?gti=amzn1.dv.gti.66e03765-50f3-4b7e-89ed-c31e9aea7ee0",
            MovieService.PRIME.tvLink("66e03765-50f3-4b7e-89ed-c31e9aea7ee0")
        )
        assertEquals(
            "https://tv.apple.com/movie/umc.cmc.qrdx9s4xrk47f4taowt4tx82",
            MovieService.APPLE.tvLink("umc.cmc.qrdx9s4xrk47f4taowt4tx82")
        )
        assertEquals(
            "stremio:///detail/movie/tt1160419/tt1160419",
            MovieService.STREMIO.tvLink("tt1160419")
        )
    }

    @Test
    fun `a tag holds the movie and one service, and reads back the same`() {
        val option = MovieOption(MovieService.HBO, "e7dc7b3a-a494-4ef1-8107-f4308aa6bbf7")

        val tag = MovieTag.build(438631, option)

        assertEquals("tangible://movie?tmdb=438631&mx=e7dc7b3a-a494-4ef1-8107-f4308aa6bbf7", tag)
        assertEquals(option, MovieTag.parse(Uri.parse(tag)))
    }

    @Test
    fun `a stremio tag reads back as stremio`() {
        val tag = MovieTag.build(438631, MovieOption(MovieService.STREMIO, "tt1160419"))

        assertEquals("tangible://movie?tmdb=438631&sm=tt1160419", tag)
        assertEquals(MovieOption(MovieService.STREMIO, "tt1160419"), MovieTag.parse(Uri.parse(tag)))
    }

    @Test
    fun `music tags, other links and movie tags without a service don't parse`() {
        assertNull(MovieTag.parse(Uri.parse("tangible://play?spotify_album=abc")))
        assertNull(MovieTag.parse(Uri.parse("https://www.netflix.com/title/80175798")))
        assertNull(MovieTag.parse(Uri.parse("tangible://movie?tmdb=438631")))
    }

    @Test
    fun `tags in the old format don't parse`() {
        assertNull(MovieTag.parse(Uri.parse("tangible://movie?tmdb_id=123&r=PT&nf=s:456&pv=r:789")))
    }

    @Test
    fun `the TMDB ID is read from a movie tag's URL`() {
        assertEquals(438631, MovieTag.tmdbId("tangible://movie?tmdb=438631&mx=e7dc7b3a"))
        assertNull(MovieTag.tmdbId("tangible://play?spotify_album=abc"))
        assertNull(MovieTag.tmdbId("tangible://movie?tmdb_id=123&nf=456"))
    }
}
