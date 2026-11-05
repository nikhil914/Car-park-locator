package com.realworldmagic.parkpin.location

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

object LocationProviderFactory {
    fun create(context: Context): LocationProvider =
        if (isPlayServicesAvailable(context)) FusedLocationProvider(context) else PlatformLocationProvider(context)

    fun isPlayServicesAvailable(context: Context): Boolean = try {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    } catch (e: Throwable) {
        false
    }
}
