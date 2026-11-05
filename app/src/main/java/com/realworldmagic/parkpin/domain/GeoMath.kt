package com.realworldmagic.parkpin.domain

import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    /** Mean Earth radius (IUGG), metres. */
    const val EARTH_RADIUS_M = 6_371_008.8
    const val METERS_PER_FOOT = 0.3048
    const val METERS_PER_MILE = 1609.344

    private val CARDINALS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    /** Great-circle distance (haversine). */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val phi1 = Math.toRadians(a.lat)
        val phi2 = Math.toRadians(b.lat)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(b.lng - a.lng)
        val h = sin(dPhi / 2).let { it * it } +
            cos(phi1) * cos(phi2) * sin(dLambda / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(min(1.0, h)))
    }

    /** Initial great-circle bearing from [from] to [to], 0..360 clockwise from true north. 0 if identical. */
    fun initialBearingDeg(from: GeoPoint, to: GeoPoint): Double {
        if (from == to) return 0.0
        val phi1 = Math.toRadians(from.lat)
        val phi2 = Math.toRadians(to.lat)
        val dLambda = Math.toRadians(to.lng - from.lng)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return normalizeDeg(Math.toDegrees(atan2(y, x)))
    }

    /** Normalizes any angle into [0, 360). */
    fun normalizeDeg(d: Double): Double {
        if (!d.isFinite()) return d
        val r = d % 360.0
        val n = if (r < 0) r + 360.0 else r
        return if (n >= 360.0) 0.0 else n
    }

    /** Angle to turn from [heading] to face [targetBearing], in (-180, 180]. Positive = turn right. */
    fun relativeBearingDeg(targetBearing: Double, heading: Double): Double {
        val n = normalizeDeg(targetBearing - heading)
        return if (n > 180.0) n - 360.0 else n
    }

    /** "45 m", "1.2 km", "12 km", "150 ft", "0.8 mi". */
    fun formatDistance(meters: Double, metric: Boolean): String {
        val m = if (meters.isFinite() && meters > 0) meters else 0.0
        return if (metric) {
            when {
                m < 999.5 -> "${m.roundToInt()} m"
                m < 9_950.0 -> String.format(Locale.US, "%.1f km", m / 1000.0)
                else -> "${(m / 1000.0).roundToLong()} km"
            }
        } else {
            val feet = m / METERS_PER_FOOT
            val miles = m / METERS_PER_MILE
            when {
                feet < 999.5 -> "${feet.roundToInt()} ft"
                miles < 9.95 -> String.format(Locale.US, "%.1f mi", miles)
                else -> "${miles.roundToLong()} mi"
            }
        }
    }

    /** Walking time in whole minutes, rounded up (0 only for zero distance). */
    fun walkingEtaMinutes(meters: Double, speedMps: Double = 1.35): Int {
        if (!meters.isFinite() || meters <= 0.0 || speedMps <= 0.0) return 0
        return ceil(meters / speedMps / 60.0).toInt()
    }

    /** 8-point compass name of a bearing. */
    fun cardinal(bearingDeg: Double): String {
        val idx = (normalizeDeg(bearingDeg) / 45.0).roundToInt() % 8
        return CARDINALS[idx]
    }
}
