package com.jjmluz.tangible.data

import android.net.Uri

/**
 * The streaming services movies can open on, in the order they're preferred when a movie is on
 * more than one. Stremio is last: it can play anything with an IMDb ID, so it's the fallback.
 *
 * @param apiId the service's ID in the Streaming Availability API; null for Stremio, which isn't there.
 * @param key the tag's query parameter that holds this service's content ID.
 */
enum class MovieService(
    val apiId: String?,
    val key: String,
    val displayName: String,
    private val idPattern: Regex,
    private val link: (String) -> String
) {
    NETFLIX(
        "netflix", "nf", "Netflix",
        Regex("/title/(\\d+)"),
        // Netflix only opens the title when the link ends with source=30.
        { "https://www.netflix.com/title/$it?source=30" }
    ),
    DISNEY(
        "disney", "dp", "Disney+",
        Regex("entity-([a-f0-9-]{36})"),
        { "https://www.disneyplus.com/browse/entity-$it" }
    ),
    HBO(
        "hbo", "mx", "HBO Max",
        Regex("/movie/([a-f0-9-]{36})"),
        { "https://play.hbomax.com/movie/$it" }
    ),
    PRIME(
        "prime", "pv", "Prime Video",
        Regex("gti=amzn1\\.dv\\.gti\\.([a-f0-9-]+)"),
        { "https://app.primevideo.com/detail?gti=amzn1.dv.gti.$it" }
    ),
    STREMIO(
        null, "sm", "Stremio",
        Regex("^(tt\\d+)$"),
        // The TV app wants the IMDb ID twice (as the item and as the video).
        { "stremio:///detail/movie/$it/$it" }
    );

    /** Pulls this service's content ID out of a link from the Streaming Availability API. */
    fun extractId(apiLink: String): String? = idPattern.find(apiLink)?.groupValues?.get(1)

    /** The deep link that opens this content on the TV. */
    fun tvLink(contentId: String): String = link(contentId)

    companion object {
        fun fromApiId(apiId: String): MovieService? = entries.find { it.apiId == apiId }

        fun fromKey(key: String): MovieService? = entries.find { it.key == key }

        /**
         * The services Tangible can open that the region has, plus Stremio, which isn't tied to a
         * region. With no region info yet, all of them.
         */
        fun availableIn(region: Region?): List<MovieService> =
            if (region == null) entries
            else entries.filter { it.apiId == null || region.services.any { s -> s.apiId == it.apiId } }
    }
}

/** A movie on one streaming service. */
data class MovieOption(val service: MovieService, val contentId: String) {
    val tvLink: String get() = service.tvLink(contentId)
}

/**
 * A movie from a search.
 *
 * @param options where it can be watched, most preferred service first; empty when it's on none
 *   of the services and has no IMDb ID for Stremio.
 */
data class MovieSearchResult(
    val tmdbId: Int,
    val title: String,
    val year: String,
    val thumbUrl: String?,
    val posterUrl: String?,
    val options: List<MovieOption>
)

/**
 * Movie tags look like `tangible://movie?tmdb=<TMDB id>&<service key>=<content id>`, e.g.
 * `tangible://movie?tmdb=398978&nf=80175798`. A tag holds one service, chosen when it's written.
 */
object MovieTag {
    fun build(tmdbId: Int, option: MovieOption): String =
        "tangible://movie?tmdb=$tmdbId&${option.service.key}=${Uri.encode(option.contentId)}"

    /** The TMDB ID in a movie tag's URL. */
    fun tmdbId(url: String): Int? {
        val uri = Uri.parse(url)
        if (uri.scheme != "tangible" || uri.host != "movie") return null
        return uri.getQueryParameter("tmdb")?.toIntOrNull()
    }

    fun parse(uri: Uri): MovieOption? {
        if (uri.scheme != "tangible" || uri.host != "movie") return null
        // Tags from before the Home Assistant change used tmdb_id and a different ID format.
        if (uri.getQueryParameter("tmdb") == null) return null
        return MovieService.entries.firstNotNullOfOrNull { service ->
            uri.getQueryParameter(service.key)
                ?.takeIf { it.isNotBlank() }
                ?.let { MovieOption(service, it) }
        }
    }
}
