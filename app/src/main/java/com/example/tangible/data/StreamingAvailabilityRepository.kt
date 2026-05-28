package com.example.tangible.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// NDEF overhead is ~8 bytes; NTAG213 has 144 bytes user memory
const val NFC_NTAG213_BYTES = 136
const val NFC_NTAG215_BYTES = 496
const val NFC_NTAG216_BYTES = 872

data class ResolvedService(
    val typeCode: String?,  // "s"=subscription, "f"=free, "r"=rent, "b"=buy, null=legacy tag
    val displayName: String,
    val url: String
)

data class MovieProvider(
    val id: String,
    val name: String,
    val iconUrl: String,
    val isEnabled: Boolean = true
)

private data class ServiceConfig(
    val shortKey: String,
    val displayName: String,
    val extractId: (String) -> String?,
    val buildLink: (String) -> String
)

private val SERVICE_CONFIGS: Map<String, ServiceConfig> = mapOf(
    "netflix" to ServiceConfig(
        shortKey = "nf", displayName = "Netflix",
        extractId = { url -> Regex("/title/(\\d+)").find(url)?.groupValues?.get(1) },
        buildLink = { id -> "https://www.netflix.com/title/$id" }
    ),
    "prime" to ServiceConfig(
        shortKey = "pv", displayName = "Prime Video",
        extractId = { url -> Regex("/detail/([A-Z0-9]{10,})").find(url)?.groupValues?.get(1) },
        buildLink = { id -> "https://app.primevideo.com/detail/$id" }
    ),
    "disney" to ServiceConfig(
        shortKey = "dp", displayName = "Disney+",
        extractId = { url -> Regex("entity-([a-f0-9\\-]{36})").find(url)?.groupValues?.get(1) },
        buildLink = { id -> "https://www.disneyplus.com/browse/entity-$id" }
    ),
    "hbo" to ServiceConfig(
        shortKey = "mx", displayName = "HBO Max",
        extractId = { url -> Regex("/(?:movie|show|video)/([a-f0-9\\-]{36})").find(url)?.groupValues?.get(1) },
        buildLink = { id -> "https://play.max.com/movie/$id" }
    ),
    "apple" to ServiceConfig(
        shortKey = "at", displayName = "Apple TV+",
        extractId = { url -> Regex("(umc\\.cmc\\.[a-z0-9]+)").find(url)?.groupValues?.get(1) },
        buildLink = { id -> "https://tv.apple.com/movie/$id" }
    ),
    "stremio" to ServiceConfig(
        shortKey = "sm", displayName = "Stremio",
        extractId = { _ -> null }, // not populated from API; IMDb ID is injected directly
        buildLink = { id -> "stremio://detail/movie/$id" }
    )
)

// Insertion order matches priority: Netflix first, then major services; Stremio last as universal fallback
private val SERVICE_PRIORITY: List<String> =
    listOf("netflix", "prime", "disney", "hbo", "apple", "stremio")

// Short-key → config for read-time URL resolution
private val SERVICE_CONFIG_BY_KEY: Map<String, ServiceConfig> =
    SERVICE_CONFIGS.values.associateBy { it.shortKey }

// Short-key → Provider ID (e.g., "nf" -> "netflix")
private val PROVIDER_ID_BY_SHORT_KEY: Map<String, String> =
    SERVICE_CONFIGS.entries.associate { it.value.shortKey to it.key }

class StreamingAvailabilityRepository(private val rapidApiKey: String) {
    private val api: StreamingAvailabilityApi

    init {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("X-API-Key", rapidApiKey)
                        .build()
                )
            }
            .addInterceptor(logger)
            .build()

        api = Retrofit.Builder()
            .baseUrl("https://api.movieofthenight.com/v4/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StreamingAvailabilityApi::class.java)
    }

    suspend fun getServicesForRegion(region: String): List<MovieProvider> {
        val stremio = MovieProvider(
            id = "stremio",
            name = "Stremio",
            iconUrl = "https://www.stremio.com/website/stremio-logo-small-alt.png"
        )
        return try {
            val countries = api.getCountries()
            val country = countries[region.lowercase()] ?: return listOf(stremio)
            val regional = country.services
                .filter { service ->
                    SERVICE_CONFIGS.containsKey(service.id) &&
                    service.supportedStreamingTypes.any { (type, enabled) -> enabled && type != "addon" }
                }
                .map { service ->
                    MovieProvider(
                        id = service.id,
                        name = service.name,
                        iconUrl = service.images.lightThemeImage
                    )
                }
            regional + stremio
        } catch (e: Exception) {
            listOf(stremio)
        }
    }

    suspend fun getMovieStreamingResult(tmdbId: Int, region: String): MovieStreamingResult {
        val typeOrder = listOf("subscription", "free", "rent", "buy")

        var imdbId: String? = null
        val streamingOptions: List<StreamingOption> = if (rapidApiKey.isNotBlank()) {
            try {
                val response = api.getShow("movie/$tmdbId", region.lowercase())
                imdbId = response.imdbId
                (response.streamingOptions[region.lowercase()] ?: emptyList())
                    .filter { it.type != "addon" && SERVICE_CONFIGS.containsKey(it.service.id) }
                    .groupBy { it.service.id }
                    .map { (_, dtos) ->
                        val bestDto = dtos.minBy { typeOrder.indexOf(it.type).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                        val allTypes = dtos.map { it.type }
                            .distinct()
                            .sortedBy { typeOrder.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                        StreamingOption(
                            serviceId = bestDto.service.id,
                            serviceName = bestDto.service.name,
                            type = bestDto.type,
                            link = bestDto.link,
                            allTypes = allTypes
                        )
                    }
                    .sortedWith(compareBy(
                        { SERVICE_PRIORITY.indexOf(it.serviceId).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                        { it.serviceName }
                    ))
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val allOptions = if (imdbId != null) {
            streamingOptions + StreamingOption(
                serviceId = "stremio",
                serviceName = "Stremio",
                type = "free",
                link = "stremio://detail/movie/$imdbId",
                allTypes = listOf("free")
            )
        } else {
            streamingOptions
        }

        return buildResult(tmdbId, region, allOptions, imdbId)
    }

    private fun buildResult(
        tmdbId: Int,
        region: String,
        options: List<StreamingOption>,
        imdbId: String? = null
    ): MovieStreamingResult {
        val base = "tangible://movie?tmdb_id=$tmdbId&r=${region.uppercase()}"
        val sb = StringBuilder(base)
        val encoded = mutableSetOf<String>()

        for (option in options) {
            if (option.serviceId in encoded) continue // one entry per service (best type wins)
            if (option.serviceId == "stremio") continue // added separately below
            val config = SERVICE_CONFIGS[option.serviceId] ?: continue
            val contentId = config.extractId(option.link) ?: continue
            val typeCode = when (option.type) {
                "subscription" -> "s"
                "free"         -> "f"
                "rent"         -> "r"
                "buy"          -> "b"
                else           -> continue
            }
            val param = "&${config.shortKey}=${Uri.encode("$typeCode:$contentId")}"
            if (sb.length + param.length > NFC_NTAG215_BYTES) continue
            sb.append(param)
            encoded.add(option.serviceId)
        }

        if (imdbId != null) {
            val stremioKey = SERVICE_CONFIGS["stremio"]!!.shortKey
            val param = "&$stremioKey=${Uri.encode(imdbId)}"
            if (sb.length + param.length <= NFC_NTAG215_BYTES) {
                sb.append(param)
                encoded.add("stremio")
            }
        }

        val tagUrl = sb.toString()
        return MovieStreamingResult(
            options = options,
            encodedServiceIds = encoded,
            tagUrl = tagUrl,
            byteCount = tagUrl.toByteArray(Charsets.UTF_8).size
        )
    }

    companion object {
        // All known short keys in priority order — used when parsing a tag
        val ALL_SERVICE_KEYS: List<String> = SERVICE_PRIORITY.mapNotNull { id ->
            SERVICE_CONFIGS[id]?.shortKey
        }

        fun getProviderId(shortKey: String): String? = PROVIDER_ID_BY_SHORT_KEY[shortKey]

        private val scrapeClient = OkHttpClient()

        // Fetches the Prime Video web page for the given ASIN and extracts the GTI,
        // returning an app deep-link that actually works in the Prime Video app.
        suspend fun resolveGtiUrl(asin: String): String? = withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://www.primevideo.com/detail/$asin")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)")
                    .build()
                val body = scrapeClient.newCall(request).execute().use { it.body?.string() ?: "" }
                android.util.Log.d("Tangible", "resolveGtiUrl: fetched ${body.length} chars for asin=$asin")
                val gti = Regex("amzn1\\.dv\\.gti\\.[a-f0-9\\-]+").find(body)?.value
                android.util.Log.d("Tangible", "resolveGtiUrl: gti=$gti")
                gti?.let { "https://app.primevideo.com/detail?gti=$it" }
            } catch (e: Exception) {
                android.util.Log.e("Tangible", "resolveGtiUrl: failed for asin=$asin", e)
                null
            }
        }

        // Parses "typeCode:contentId" (or bare "contentId" for legacy tags without a type prefix).
        fun resolveService(shortKey: String, encodedValue: String): ResolvedService? {
            val colonIdx = encodedValue.indexOf(':')
            val typeCode = if (colonIdx > 0) encodedValue.substring(0, colonIdx) else null
            val contentId = if (colonIdx > 0) encodedValue.substring(colonIdx + 1) else encodedValue
            val config = SERVICE_CONFIG_BY_KEY[shortKey] ?: return null
            val url = config.buildLink(contentId)
            val typeLabel = when (typeCode) {
                "s"  -> "Subscription"
                "f"  -> "Free"
                "r"  -> "Rent"
                "b"  -> "Buy"
                else -> null
            }
            val displayName = config.displayName + (typeLabel?.let { " ($it)" } ?: "")
            return ResolvedService(typeCode = typeCode, displayName = displayName, url = url)
        }
    }
}
