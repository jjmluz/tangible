package com.example.tangible.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicTagTest {

    @Test
    fun `a Spotify album's tag holds its Spotify ID`() {
        val expected = "tangible://play?spotify_album=6dVIqQ8qmQ5GBnJ9shOYGE"
        assertEquals(expected, MusicTag.build("spotify://album/6dVIqQ8qmQ5GBnJ9shOYGE"))
        assertEquals(expected, MusicTag.build("spotify--abc123://album/6dVIqQ8qmQ5GBnJ9shOYGE"))
    }

    @Test
    fun `any other album's tag holds its Music Assistant URI`() {
        assertEquals("tangible://play?album=library%3A%2F%2Falbum%2F12", MusicTag.build("library://album/12"))
        assertEquals("tangible://play?album=spotify%3A%2F%2Ftrack%2Fabc", MusicTag.build("spotify://track/abc"))
    }

    @Test
    fun `tags give back the URI Music Assistant plays`() {
        assertEquals(
            "spotify://album/6dVIqQ8qmQ5GBnJ9shOYGE",
            MusicTag.parse(Uri.parse("tangible://play?spotify_album=6dVIqQ8qmQ5GBnJ9shOYGE"))
        )
        assertEquals("library://album/12", MusicTag.parse(Uri.parse(MusicTag.build("library://album/12"))))
    }

    @Test
    fun `movie tags, other links and empty tags aren't music`() {
        assertNull(MusicTag.parse(Uri.parse("tangible://movie?tmdb=438631&nf=80175798")))
        assertNull(MusicTag.parse(Uri.parse("https://open.spotify.com/album/6dVIqQ8qmQ5GBnJ9shOYGE")))
        assertNull(MusicTag.parse(Uri.parse("tangible://play")))
        assertNull(MusicTag.parse(Uri.parse("tangible://play?spotify_album=")))
    }
}
