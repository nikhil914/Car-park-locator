package com.realworldmagic.parkpin.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.realworldmagic.parkpin.domain.FixFreshness
import com.realworldmagic.parkpin.domain.GeoPoint
import com.realworldmagic.parkpin.domain.LocationFix

/** Accuracy reported when a fix carries none. */
internal const val UNKNOWN_ACCURACY_M = 9_999f

internal fun Location.toFix(fallbackProvider: String = "unknown"): LocationFix = LocationFix(
    point = GeoPoint(latitude, longitude),
    accuracyM = if (hasAccuracy()) accuracy else UNKNOWN_ACCURACY_M,
    altitudeM = if (hasAltitude()) altitude else null,
    timeMs = time,
    provider = provider ?: fallbackProvider,
    isMock = isMockLocation(),
)

@Suppress("DEPRECATION")
internal fun Location.isMockLocation(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else isFromMockProvider

/**
 * Age of this fix on the monotonic clock (immune to wall-clock / GPS-time skew), or null when the
 * platform did not stamp elapsed realtime.
 */
internal fun Location.monotonicAgeMs(): Long? {
    val stamped = elapsedRealtimeNanos
    if (stamped <= 0L) return null
    return (SystemClock.elapsedRealtimeNanos() - stamped) / 1_000_000L
}

/**
 * True when a fix delivered by a live update is worth forwarding: real coordinates and not a
 * cached/historical replay older than [maxAgeMs].
 */
internal fun Location.isUsableLiveFix(maxAgeMs: Long = FixFreshness.MAX_LIVE_AGE_MS): Boolean {
    if (!GeoPoint(latitude, longitude).isValid) return false
    val age = monotonicAgeMs() ?: return true
    return age <= maxAgeMs
}

internal fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
