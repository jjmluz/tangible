package com.example.tangible.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SonosRepository {
    private val client = OkHttpClient()
    private val TAG = "SonosRepository"

    private data class SpotifyAccount(val sid: String, val sn: String, val svc: String)

    private suspend fun getSpotifyAccount(ip: String): SpotifyAccount = withContext(Dispatchers.IO) {
        try {
            val urls = listOf("http://$ip:1400/status/accounts", "http://$ip:1400/status/zpinfo")
            for (url in urls) {
                val xml = java.net.URL(url).openConnection().apply { connectTimeout = 2000 }.getInputStream().bufferedReader().use { it.readText() }
                val match = Regex("""Type="(2311|3079)"[^>]*?Serial="(\d+)"""").find(xml)
                if (match != null) {
                    val type = match.groupValues[1]
                    val sn = match.groupValues[2]
                    val sid = if (type == "3079") "12" else "9"
                    Log.d(TAG, "Found Spotify account: Type=$type, Serial=$sn, SID=$sid")
                    return@withContext SpotifyAccount(sid, sn, type)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Spotify account: ${e.message}")
        }
        SpotifyAccount("9", "1", "2311")
    }

    suspend fun playSpotifyUri(speakerIp: String, spotifyUri: String): Boolean = withContext(Dispatchers.IO) {
        val coordinatorIp = getCoordinatorIp(speakerIp) ?: speakerIp
        val speakerUuid = getSpeakerUuid(coordinatorIp) ?: return@withContext false

        val account = getSpotifyAccount(coordinatorIp)
        val encodedUri = spotifyUri.replace(":", "%3a")

        if (spotifyUri.startsWith("spotify:track:")) {
            // Tracks: set transport URI directly then play
            val uri = "x-sonos-spotify:${encodedUri}?sid=${account.sid}&flags=8232&sn=${account.sn}"
            val metadata = buildMetadata(encodedUri, account.svc)
            Log.d(TAG, "Playing track: $uri")
            if (!setAVTransportURI(coordinatorIp, uri, metadata)) return@withContext false
            delay(200)
            return@withContext play(coordinatorIp)
        } else {
            // Albums/playlists: clear queue, add content, seek to track 1, play
            // URI prefix 0006206c for Spotify containers; no sid/flags/sn in the URI itself
            val uri = "x-rincon-cpcontainer:0006206c${encodedUri}"
            val metadata = buildMetadata(encodedUri, account.svc)
            Log.d(TAG, "Playing album/playlist: $uri")

            clearQueue(coordinatorIp)
            setSourceToQueue(coordinatorIp, speakerUuid)
            delay(200)

            if (!addToQueue(coordinatorIp, uri, metadata)) return@withContext false
            delay(200)

            seekToTrack(coordinatorIp, 1)
            delay(100)

            return@withContext play(coordinatorIp)
        }
    }

    // Metadata format matching node-sonos-http-api's getSpotifyMetadata():
    // - item id uses "00030020" prefix + encoded URI
    // - upnp:class is musicTrack (same for tracks and containers)
    // - no <res> element needed
    private fun buildMetadata(encodedUri: String, serviceType: String): String {
        return """<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:r="urn:schemas-rinconnetworks-com:metadata-1-0/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"><item id="00030020${encodedUri}" restricted="true"><upnp:class>object.item.audioItem.musicTrack</upnp:class><desc id="cdudn" nameSpace="urn:schemas-rinconnetworks-com:metadata-1-0/">SA_RINCON${serviceType}_X_#Svc${serviceType}-0-Token</desc></item></DIDL-Lite>"""
    }

    private fun sendSoapRequest(ip: String, service: String, action: String, body: String): Boolean {
        val request = Request.Builder()
            .url("http://$ip:1400/MediaRenderer/$service/Control")
            .addHeader("Content-Type", "text/xml; charset=\"utf-8\"")
            .addHeader("SOAPACTION", "\"urn:schemas-upnp-org:service:$service:1#$action\"")
            .post(body.toRequestBody("text/xml".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "$action failed: ${response.code} $responseBody")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "SOAP Error ($action): ${e.message}")
            false
        }
    }

    suspend fun getSpeakerUuid(ip: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "http://$ip:1400/xml/device_description.xml"
            val xml = java.net.URL(url).openConnection().apply { connectTimeout = 1000 }.getInputStream().bufferedReader().use { it.readText() }
            xml.substringAfter("<UDN>uuid:").substringBefore("</UDN>")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching UUID for $ip: ${e.message}")
            null
        }
    }

    suspend fun joinGroup(slaveIp: String, masterUuid: String): Boolean = withContext(Dispatchers.IO) {
        val soapBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
            <InstanceID>0</InstanceID>
            <CurrentURI>x-rincon:$masterUuid</CurrentURI>
            <CurrentURIMetaData></CurrentURIMetaData>
        </u:SetAVTransportURI>
    </s:Body>
</s:Envelope>""".trimIndent()
        sendSoapRequest(slaveIp, "AVTransport", "SetAVTransportURI", soapBody)
    }

    suspend fun becomeMaster(ip: String): Boolean = withContext(Dispatchers.IO) {
        val soapBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/envelope/">
    <s:Body>
        <u:BecomeCoordinatorOfStandaloneGroup xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
            <InstanceID>0</InstanceID>
        </u:BecomeCoordinatorOfStandaloneGroup>
    </s:Body>
</s:Envelope>""".trimIndent()
        sendSoapRequest(ip, "AVTransport", "BecomeCoordinatorOfStandaloneGroup", soapBody)
    }

    suspend fun stop(ip: String): Boolean = withContext(Dispatchers.IO) {
        val soapBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/envelope/">
    <s:Body>
        <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
            <InstanceID>0</InstanceID>
        </u:Stop>
    </s:Body>
</s:Envelope>""".trimIndent()
        sendSoapRequest(ip, "AVTransport", "Stop", soapBody)
    }

    // Find the coordinator IP for a speaker's group.
    // Sonos topology XML has attributes in order: group, coordinator, uuid, location.
    // We use a line-based search to avoid relying on attribute ordering in a regex.
    private fun getCoordinatorIp(speakerIp: String): String? {
        return try {
            val url = "http://$speakerIp:1400/status/topology"
            val xml = java.net.URL(url).openConnection().apply { connectTimeout = 2000 }.getInputStream().bufferedReader().use { it.readText() }

            val speakerLine = xml.lines().firstOrNull { it.contains("""location="http://$speakerIp:1400/"""") }
                ?: return null
            val groupId = Regex("""group="([^"]+)"""").find(speakerLine)?.groupValues?.get(1)
                ?: return null

            val coordLine = xml.lines().firstOrNull {
                it.contains("""group="$groupId"""") && it.contains("""coordinator="true"""")
            } ?: return null

            Regex("""location="http://([^:]+):1400/"""").find(coordLine)?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting coordinator: ${e.message}")
            null
        }
    }

    private fun setAVTransportURI(ip: String, uri: String, metadata: String): Boolean {
        val escapedMetadata = metadata
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")
        val soapBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
            <InstanceID>0</InstanceID>
            <CurrentURI>${uri.replace("&", "&amp;")}</CurrentURI>
            <CurrentURIMetaData>$escapedMetadata</CurrentURIMetaData>
        </u:SetAVTransportURI>
    </s:Body>
</s:Envelope>""".trimIndent()
        return sendSoapRequest(ip, "AVTransport", "SetAVTransportURI", soapBody)
    }

    private fun addToQueue(ip: String, uri: String, metadata: String): Boolean {
        val escapedMetadata = metadata
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")
        val soapBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:AddURIToQueue xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
            <InstanceID>0</InstanceID>
            <EnqueuedURI>${uri.replace("&", "&amp;")}</EnqueuedURI>
            <EnqueuedURIMetaData>$escapedMetadata</EnqueuedURIMetaData>
            <DesiredFirstTrackNumberEnqueued>0</DesiredFirstTrackNumberEnqueued>
            <EnqueueAsNext>0</EnqueueAsNext>
        </u:AddURIToQueue>
    </s:Body>
</s:Envelope>""".trimIndent()
        return sendSoapRequest(ip, "AVTransport", "AddURIToQueue", soapBody)
    }

    private fun setSourceToQueue(ip: String, uuid: String): Boolean {
        val soapBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
            <InstanceID>0</InstanceID>
            <CurrentURI>x-rincon-queue:$uuid#0</CurrentURI>
            <CurrentURIMetaData></CurrentURIMetaData>
        </u:SetAVTransportURI>
    </s:Body>
</s:Envelope>""".trimIndent()
        return sendSoapRequest(ip, "AVTransport", "SetAVTransportURI", soapBody)
    }

    private fun clearQueue(ip: String): Boolean {
        val soapBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:RemoveAllTracksFromQueue xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
            <InstanceID>0</InstanceID>
        </u:RemoveAllTracksFromQueue>
    </s:Body>
</s:Envelope>""".trimIndent()
        return sendSoapRequest(ip, "AVTransport", "RemoveAllTracksFromQueue", soapBody)
    }

    private fun seekToTrack(ip: String, trackNo: Int): Boolean {
        val soapBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:Seek xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
            <InstanceID>0</InstanceID>
            <Unit>TRACK_NR</Unit>
            <Target>$trackNo</Target>
        </u:Seek>
    </s:Body>
</s:Envelope>""".trimIndent()
        return sendSoapRequest(ip, "AVTransport", "Seek", soapBody)
    }

    private fun play(ip: String): Boolean {
        val soapBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
            <InstanceID>0</InstanceID>
            <Speed>1</Speed>
        </u:Play>
    </s:Body>
</s:Envelope>""".trimIndent()
        return sendSoapRequest(ip, "AVTransport", "Play", soapBody)
    }

    suspend fun getPlayingInfo(speakerIp: String) = withContext(Dispatchers.IO) {
        val soapBody = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
    <s:Body>
        <u:GetPositionInfo xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
            <InstanceID>0</InstanceID>
        </u:GetPositionInfo>
    </s:Body>
</s:Envelope>""".trimIndent()
        sendSoapRequest(speakerIp, "AVTransport", "GetPositionInfo", soapBody)
    }
}
