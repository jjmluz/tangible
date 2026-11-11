package com.example.tangible.data

import android.net.Uri

/**
 * Music tags hold an album as Music Assistant knows it.
 *
 * A Spotify album is `tangible://play?spotify_album=<Spotify album ID>`. That's the ID alone, so
 * the tag keeps working when Spotify is removed from Music Assistant and added again.
 *
 * Anything else (an album in the Music Assistant library, or on another provider) is
 * `tangible://play?album=<Music Assistant URI>`, e.g. `library://album/12`.
 */
object MusicTag {
    private const val SPOTIFY_ALBUM_PARAM = "spotify_album"
    private const val ALBUM_PARAM = "album"

    /** `spotify://album/<id>`, or with the provider instance: `spotify--abc123://album/<id>`. */
    private val spotifyAlbum = Regex("^spotify(?:--[^:/]+)?://album/([^/?#]+)$")

    /** The tag for an album, from its Music Assistant URI. */
    fun build(mediaUri: String): String {
        val spotifyId = spotifyAlbum.find(mediaUri)?.groupValues?.get(1)
        return if (spotifyId != null) {
            "tangible://play?$SPOTIFY_ALBUM_PARAM=${Uri.encode(spotifyId)}"
        } else {
            "tangible://play?$ALBUM_PARAM=${Uri.encode(mediaUri)}"
        }
    }

    /** The Music Assistant URI of the tag's album, or null when it isn't a music tag. */
    fun parse(uri: Uri): String? {
        if (uri.scheme != "tangible" || (uri.host != "play" && uri.host != null)) return null
        uri.getQueryParameter(SPOTIFY_ALBUM_PARAM)?.takeIf { it.isNotBlank() }?.let { return "spotify://album/$it" }
        return uri.getQueryParameter(ALBUM_PARAM)?.takeIf { it.isNotBlank() }
    }
}
