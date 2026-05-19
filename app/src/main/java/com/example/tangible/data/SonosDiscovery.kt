package com.example.tangible.data

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URL

data class SonosSpeaker(
    val ip: String,
    val name: String,
    val model: String
)

class SonosDiscovery(private val context: Context) {
    private val TAG = "SonosDiscovery"

    suspend fun discoverSpeakers(): List<SonosSpeaker> = withContext(Dispatchers.IO) {
        val speakers = mutableMapOf<String, SonosSpeaker>()
        
        // 1. Try Multicast Discovery first
        val multicastSpeakers = discoverViaMulticast()
        multicastSpeakers.forEach { speakers[it.ip] = it }
        
        // 2. If nothing found, try a targeted scan of the current subnet
        if (speakers.isEmpty()) {
            Log.d(TAG, "Multicast discovery failed, trying subnet scan...")
            val subnetSpeakers = scanSubnet()
            subnetSpeakers.forEach { speakers[it.ip] = it }
        }

        speakers.values.toList()
    }

    private suspend fun discoverViaMulticast(): List<SonosSpeaker> = withContext(Dispatchers.IO) {
        val speakers = mutableMapOf<String, SonosSpeaker>()
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("SonosDiscoveryLock")
        
        val socket = DatagramSocket()
        socket.soTimeout = 2000
        socket.broadcast = true

        val ssdpQuery = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n" +
                "\r\n"

        try {
            lock.acquire()
            val group = InetAddress.getByName("239.255.255.250")
            val packet = DatagramPacket(ssdpQuery.toByteArray(), ssdpQuery.length, group, 1900)
            socket.send(packet)
            
            // Also try broadcast
            val broadcastPacket = DatagramPacket(ssdpQuery.toByteArray(), ssdpQuery.length, 
                InetAddress.getByName("255.255.255.255"), 1900)
            socket.send(broadcastPacket)

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 3000) {
                val buffer = ByteArray(1024)
                val receivePacket = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    val location = response.lines().find { it.startsWith("LOCATION:", ignoreCase = true) }
                        ?.substringAfter(":")?.trim()

                    location?.let { loc ->
                        fetchDeviceDescription(loc)?.let { speakers[it.ip] = it }
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Multicast error", e)
        } finally {
            if (lock.isHeld) lock.release()
            socket.close()
        }
        speakers.values.toList()
    }

    private suspend fun scanSubnet(): List<SonosSpeaker> = withContext(Dispatchers.IO) {
        val wifiIp = getWifiIpAddress()
        if (wifiIp == null) return@withContext emptyList<SonosSpeaker>()
        
        val baseIp = wifiIp.hostAddress?.substringBeforeLast(".") ?: return@withContext emptyList<SonosSpeaker>()
        
        Log.d(TAG, "Scanning subnet $baseIp.0/24")

        // We scan 1-254 concurrently. Sonos always uses port 1400 for its description XML.
        (1..254).map { i ->
            async {
                val targetIp = "$baseIp.$i"
                if (targetIp == wifiIp.hostAddress) return@async null
                
                probeIp(targetIp)
            }
        }.awaitAll().filterNotNull()
    }

    private fun probeIp(ip: String): SonosSpeaker? {
        return try {
            val url = "http://$ip:1400/xml/device_description.xml"
            fetchDeviceDescription(url)
        } catch (e: Exception) {
            null
        }
    }

    private fun getWifiIpAddress(): InetAddress? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress
            if (ipAddress == 0) return null
            val bytes = byteArrayOf(
                (ipAddress and 0xff).toByte(),
                (ipAddress shr 8 and 0xff).toByte(),
                (ipAddress shr 16 and 0xff).toByte(),
                (ipAddress shr 24 and 0xff).toByte()
            )
            InetAddress.getByAddress(bytes)
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchDeviceDescription(url: String): SonosSpeaker? {
        return try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 2000 
            connection.readTimeout = 2000
            val xml = connection.getInputStream().bufferedReader().use { it.readText() }
            
            if (xml.contains("Sonos") || xml.contains("ZonePlayer")) {
                val friendlyName = xml.substringAfter("<friendlyName>").substringBefore("</friendlyName>")
                val modelName = xml.substringAfter("<modelName>").substringBefore("</modelName>")
                val ip = URL(url).host
                Log.d(TAG, "Successfully fetched description for $friendlyName at $ip")
                return SonosSpeaker(ip, friendlyName, modelName)
            }
            null
        } catch (e: Exception) {
            // Log.v(TAG, "No Sonos at $url")
            null
        }
    }
}
