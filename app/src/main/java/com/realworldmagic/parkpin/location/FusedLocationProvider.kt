package com.realworldmagic.parkpin.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.realworldmagic.parkpin.domain.LocationFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Play Services fused provider (GPS + Wi-Fi + cell + sensors). Works offline with GPS. */
class FusedLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(appContext)

    override val name: String get() = "fused"

    @SuppressLint("MissingPermission")
    override fun locationUpdates(intervalMs: Long): Flow<LocationFix> = callbackFlow {
        if (!appContext.hasLocationPermission()) {
            close()
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis((intervalMs / 2).coerceAtLeast(100L))
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // Batched results can include cached/historical fixes; drop stale and bogus ones.
                for (location in result.locations) {
                    if (location.isUsableLiveFix()) trySend(location.toFix("fused"))
                }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { e ->
                    // e.g. ApiException when location services are unavailable: complete quietly
                    // (contract: never throw), consumers fall back to last-known / "no fix".
                    Log.w(TAG, "requestLocationUpdates failed", e)
                    close()
                }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission revoked", e)
            close()
            return@callbackFlow
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentFix(timeoutMs: Long): LocationFix? {
        if (!appContext.hasLocationPermission()) return null
        val cts = CancellationTokenSource()
        return try {
            withTimeoutOrNull(timeoutMs) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                    ?.takeIf { it.isUsableLiveFix() }
                    ?.toFix("fused")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission revoked", e)
            null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "getCurrentLocation failed", e)
            null
        } finally {
            cts.cancel()
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun lastKnownFix(): LocationFix? {
        if (!appContext.hasLocationPermission()) return null
        return try {
            client.lastLocation.await()?.toFix("fused")?.takeIf { it.point.isValid }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    private companion object {
        const val TAG = "FusedLocation"
    }
}
