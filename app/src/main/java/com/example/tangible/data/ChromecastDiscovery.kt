package com.example.tangible.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class ChromecastDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int
)

class ChromecastDiscovery(private val context: Context) {
    private val TAG = "ChromecastDiscovery"
    private val SERVICE_TYPE = "_googlecast._tcp."

    suspend fun discoverDevices(): List<ChromecastDevice> = withContext(Dispatchers.IO) {
        val devices = ConcurrentHashMap<String, ChromecastDevice>()
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Chromecast discovery started")
            }
            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Chromecast discovery stopped")
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Chromecast service found: ${serviceInfo.serviceName}")
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Resolve failed for ${info.serviceName}: $errorCode")
                    }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host?.hostAddress ?: return
                        // "fn" attribute is the Chromecast friendly name
                        val friendlyName = info.attributes["fn"]?.let { String(it) }
                            ?: info.serviceName
                        val device = ChromecastDevice(
                            id = info.serviceName,
                            name = friendlyName,
                            host = host,
                            port = info.port
                        )
                        Log.d(TAG, "Resolved Chromecast: $friendlyName at $host:${info.port}")
                        devices[device.id] = device
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Chromecast service lost: ${serviceInfo.serviceName}")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            delay(4000)
        } catch (e: Exception) {
            Log.e(TAG, "Discovery error", e)
        } finally {
            try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (e: Exception) { }
        }

        devices.values.sortedBy { it.name }
    }
}
