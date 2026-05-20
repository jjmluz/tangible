package com.example.tangible.data

import android.content.Context
import android.util.Log
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AdbRepository(private val context: Context) {
    private val TAG = "AdbRepository"
    private val privateKeyFile = File(context.filesDir, "adb_key")
    private val publicKeyFile = File(context.filesDir, "adb_key.pub")

    private fun getOrCreateKeyPair(): AdbKeyPair {
        if (!privateKeyFile.exists() || !publicKeyFile.exists()) {
            AdbKeyPair.generate(privateKeyFile, publicKeyFile)
        }
        return AdbKeyPair.read(privateKeyFile, publicKeyFile)
    }

    suspend fun launchSpotify(host: String, spotifyUri: String): Boolean =
        withContext(Dispatchers.IO) {
            var dadb: Dadb? = null
            try {
                val keyPair = getOrCreateKeyPair()
                dadb = Dadb.create(host, 5555, keyPair)
                val escaped = spotifyUri.replace("'", "'\\''")
                val result = dadb.shell(
                    "am start -a android.intent.action.VIEW -d '$escaped' com.spotify.tv.android"
                )
                Log.d(TAG, "am start exit=${result.exitCode} output=${result.allOutput}")
                result.exitCode == 0
            } catch (e: Exception) {
                Log.e(TAG, "Launch failed: ${e.message}")
                false
            } finally {
                try { dadb?.close() } catch (e: Exception) { }
            }
        }
}
