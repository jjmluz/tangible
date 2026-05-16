package com.example.tangible.data

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(SPOTIFY_CAST_APP_ID)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null

    companion object {
        const val SPOTIFY_CAST_APP_ID = "CC32E753"
    }
}
