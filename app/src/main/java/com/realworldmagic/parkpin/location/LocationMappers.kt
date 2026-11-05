package com.realworldmagic.parkpin.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
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
)

internal fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
