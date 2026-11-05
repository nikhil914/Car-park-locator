package com.realworldmagic.parkpin.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.realworldmagic.parkpin.domain.GeoPoint
import com.realworldmagic.parkpin.domain.LocationFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Framework LocationManager provider for devices without Play Services. GPS, with NETWORK as fallback. */
class PlatformLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override val name: String get() = "platform"

    private fun availableProviders(): List<String> {
        val all = try { manager.allProviders } catch (e: Exception) { emptyList() }
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { it in all }
    }

    @SuppressLint("MissingPermission")
    override fun locationUpdates(intervalMs: Long): Flow<LocationFix> = callbackFlow {
        if (!appContext.hasLocationPermission()) {
            close()
            return@callbackFlow
        }
        var lastGpsTimeMs = 0L
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!location.isUsableLiveFix()) return
                val isGps = location.provider == LocationManager.GPS_PROVIDER
                if (isGps) lastGpsTimeMs = location.time
                // Prefer GPS: drop network fixes while GPS is fresh.
                if (!isGps && location.time - lastGpsTimeMs < intervalMs * 3) return
                trySend(location.toFix())
            }

            // Implemented explicitly: these are abstract on API < 30.
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        var registered = 0
        for (p in availableProviders()) {
            try {
                manager.requestLocationUpdates(p, intervalMs, 0f, listener, Looper.getMainLooper())
                registered++
            } catch (e: SecurityException) {
                Log.w(TAG, "Location permission missing for $p", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Provider $p unavailable", e)
            }
        }
        if (registered == 0) {
            close()
            return@callbackFlow
        }
        awaitClose { manager.removeUpdates(listener) }
    }

    override suspend fun currentFix(timeoutMs: Long): LocationFix? =
        withTimeoutOrNull(timeoutMs) {
            try {
                locationUpdates(500L).first()
            } catch (e: NoSuchElementException) {
                null
            }
        }

    @SuppressLint("MissingPermission")
    override suspend fun lastKnownFix(): LocationFix? {
        if (!appContext.hasLocationPermission()) return null
        return availableProviders()
            .mapNotNull { p ->
                try { manager.getLastKnownLocation(p) } catch (e: SecurityException) { null } catch (e: IllegalArgumentException) { null }
            }
            .filter { GeoPoint(it.latitude, it.longitude).isValid }
            .maxByOrNull { it.time }
            ?.toFix()
    }

    private companion object {
        const val TAG = "PlatformLocation"
    }
}
