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

    suspend fun launchUri(host: String, uri: String, packageName: String = ""): Boolean =
        withContext(Dispatchers.IO) {
            var dadb: Dadb? = null
            try {
                val keyPair = getOrCreateKeyPair()
                dadb = Dadb.create(host, 5555, keyPair)
                val escaped = uri.replace("'", "'\\''")
                val cmd = if (packageName.isNotBlank())
                    "am start -a android.intent.action.VIEW -d '$escaped' $packageName"
                else
                    "am start -a android.intent.action.VIEW -d '$escaped'"
                val result = dadb.shell(cmd)
                val output = result.allOutput
                Log.d(TAG, "am start exit=${result.exitCode} output=$output")
                // am start can exit 0 even on failure — also check the output
                val failed = result.exitCode != 0 ||
                    (output.contains("Error", ignoreCase = true) &&
                     output.contains("not started", ignoreCase = true))
                !failed
            } catch (e: Exception) {
                Log.e(TAG, "Launch failed: ${e.message}")
                false
            } finally {
                try { dadb?.close() } catch (e: Exception) { }
            }
        }
}
