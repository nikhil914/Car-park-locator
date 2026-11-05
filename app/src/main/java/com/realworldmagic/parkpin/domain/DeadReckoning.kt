package com.realworldmagic.parkpin.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Where a position estimate came from. */
enum class PositionSource { GPS, DEAD_RECKONING, BLENDED }

/**
 * Current pedestrian-dead-reckoning (PDR) estimate.
 *
 * @param point estimated position.
 * @param uncertaintyM 68% horizontal radius: anchor accuracy + [DeadReckoning.driftFraction] × distance walked.
 * @param distanceSinceFixM path length walked since the anchoring GPS fix.
 * @param stepsSinceFix step events applied since the anchor.
 * @param anchorTimeMs time of the anchoring GPS fix.
 * @param lastUpdateMs time of the latest step (or the anchor time if no step yet).
 */
data class DeadReckoningEstimate(
    val point: GeoPoint,
    val uncertaintyM: Double,
    val distanceSinceFixM: Double,
    val stepsSinceFix: Int,
    val anchorTimeMs: Long,
    val lastUpdateMs: Long,
) {
    /** True once at least one step moved the estimate away from the GPS anchor. */
    val isDeadReckoned: Boolean get() = stepsSinceFix > 0
}

/** Result of [DeadReckoning.fuse]: a single best position for guidance. */
data class FusedPosition(val point: GeoPoint, val accuracyM: Float, val source: PositionSource, val timeMs: Long) {
    /** Adapter so the result can feed code that expects a [LocationFix] (e.g. GuidanceCalculator). */
    fun toLocationFix(altitudeM: Double? = null): LocationFix = LocationFix(
        point = point,
        accuracyM = accuracyM,
        altitudeM = altitudeM,
        timeMs = timeMs,
        provider = when (source) {
            PositionSource.GPS -> "gps"
            PositionSource.DEAD_RECKONING -> "pdr"
            PositionSource.BLENDED -> "fused"
        },
    )
}

/**
 * Pedestrian dead reckoning: advances a position estimate from the last good GPS fix using
 * step events and the compass heading. Pure Kotlin, no Android dependencies, not thread-safe
 * (drive it from one coroutine).
 *
 * Usage: call [onFix] for every GPS fix and [onStep] for every step-detector event, then read
 * [estimate] (or use [fuse] to combine with the latest GPS fix).
 *
 * @param stepLengthM metres per step; see [stepLengthFromHeightCm]. Defaults to [DEFAULT_STEP_LENGTH_M].
 * @param goodFixAccuracyM a fix at or below this accuracy (re-)anchors the estimate.
 * @param driftFraction uncertainty growth per metre walked (0.05 = 5% of distance).
 * @param maxUsefulDistanceM after this much walking without a good fix, [isReliable] turns false.
 */
class DeadReckoning(
    stepLengthM: Double = DEFAULT_STEP_LENGTH_M,
    val goodFixAccuracyM: Float = GOOD_FIX_ACCURACY_M,
    val driftFraction: Double = DEFAULT_DRIFT_FRACTION,
    val maxUsefulDistanceM: Double = DEFAULT_MAX_USEFUL_DISTANCE_M,
) {
    init {
        require(goodFixAccuracyM > 0f) { "goodFixAccuracyM must be > 0" }
        require(driftFraction >= 0.0 && driftFraction.isFinite()) { "driftFraction must be >= 0" }
    }

    /** Metres per step. Invalid values (≤ 0, NaN, > 2.5 m) fall back to [DEFAULT_STEP_LENGTH_M]. */
    var stepLengthM: Double = sanitizeStepLength(stepLengthM)
        set(value) { field = sanitizeStepLength(value) }

    private var anchor: LocationFix? = null
    private var current: GeoPoint? = null
    private var distance = 0.0
    private var steps = 0
    private var lastUpdate = 0L

    /** Null until the first good GPS fix arrives. */
    val estimate: DeadReckoningEstimate?
        get() {
            val a = anchor ?: return null
            val p = current ?: return null
            return DeadReckoningEstimate(
                point = p,
                uncertaintyM = a.accuracyM + driftFraction * distance,
                distanceSinceFixM = distance,
                stepsSinceFix = steps,
                anchorTimeMs = a.timeMs,
                lastUpdateMs = lastUpdate,
            )
        }

    /** False before the first good fix or after walking more than [maxUsefulDistanceM] without one. */
    val isReliable: Boolean get() = anchor != null && distance <= maxUsefulDistanceM

    /**
     * Feeds a GPS fix. A fix with accuracy ≤ [goodFixAccuracyM] resets the estimate to that fix
     * (distance and step count go back to 0). Poorer fixes are ignored here; combine them with
     * [fuse]. Returns true when the fix re-anchored the estimate.
     */
    fun onFix(fix: LocationFix): Boolean {
        if (!fix.point.isValid || !fix.accuracyM.isFinite() || fix.accuracyM < 0f) return false
        if (fix.accuracyM > goodFixAccuracyM) return false
        val prev = anchor
        if (prev != null && fix.timeMs < prev.timeMs) return false // out-of-order
        anchor = fix
        current = fix.point
        distance = 0.0
        steps = 0
        lastUpdate = fix.timeMs
        return true
    }

    /**
     * Applies [count] steps walked towards [headingDeg] (true-north azimuth of the direction
     * of travel). Ignored before the first good fix or for non-finite headings.
     * Returns the updated estimate, or null if nothing changed.
     */
    fun onStep(headingDeg: Double, timeMs: Long, count: Int = 1): DeadReckoningEstimate? {
        if (count <= 0 || !headingDeg.isFinite()) return null
        val p = current ?: return null
        val a = anchor ?: return null
        if (timeMs < a.timeMs) return null // step happened before the anchor fix
        val d = stepLengthM * count
        current = destination(p, GeoMath.normalizeDeg(headingDeg), d)
        distance += d
        steps += count
        lastUpdate = maxOf(lastUpdate, timeMs)
        return estimate
    }

    /** Forgets everything (e.g. when guidance stops). */
    fun reset() {
        anchor = null
        current = null
        distance = 0.0
        steps = 0
        lastUpdate = 0L
    }

    /** Convenience: [fuse] with this instance's [estimate] and [goodFixAccuracyM]. */
    fun fuse(gps: LocationFix?, nowMs: Long, gpsMaxAgeMs: Long = DEFAULT_GPS_MAX_AGE_MS): FusedPosition? =
        fuse(gps, if (isReliable) estimate else null, nowMs, goodFixAccuracyM, gpsMaxAgeMs)

    companion object {
        const val DEFAULT_STEP_LENGTH_M = 0.72
        const val GOOD_FIX_ACCURACY_M = 15f
        const val DEFAULT_DRIFT_FRACTION = 0.05
        const val DEFAULT_MAX_USEFUL_DISTANCE_M = 600.0
        const val DEFAULT_GPS_MAX_AGE_MS = 10_000L

        /** Typical walking step ≈ 0.415 × body height. Returns the default for implausible heights. */
        fun stepLengthFromHeightCm(heightCm: Double?): Double {
            if (heightCm == null || !heightCm.isFinite() || heightCm < 100.0 || heightCm > 250.0) {
                return DEFAULT_STEP_LENGTH_M
            }
            return heightCm / 100.0 * 0.415
        }

        private fun sanitizeStepLength(v: Double): Double =
            if (v.isFinite() && v > 0.0 && v <= 2.5) v else DEFAULT_STEP_LENGTH_M

        /** Point reached travelling [distanceM] from [start] on initial bearing [bearingDeg] (great circle). */
        fun destination(start: GeoPoint, bearingDeg: Double, distanceM: Double): GeoPoint {
            if (distanceM == 0.0) return start
            val delta = distanceM / GeoMath.EARTH_RADIUS_M
            val theta = Math.toRadians(bearingDeg)
            val phi1 = Math.toRadians(start.lat)
            val lambda1 = Math.toRadians(start.lng)
            val sinPhi2 = sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta)
            val phi2 = asin(sinPhi2.coerceIn(-1.0, 1.0))
            val lambda2 = lambda1 + atan2(
                sin(theta) * sin(delta) * cos(phi1),
                cos(delta) - sin(phi1) * sinPhi2,
            )
            var lng = Math.toDegrees(lambda2)
            lng = ((lng + 540.0) % 360.0) - 180.0
            return GeoPoint(Math.toDegrees(phi2), lng)
        }

        /**
         * Picks / blends the best position for guidance.
         *
         * - Fresh GPS fix with accuracy ≤ [goodFixAccuracyM]: GPS wins outright.
         * - Fresh but poor GPS and a dead-reckoned estimate: inverse-variance weighted blend
         *   (weights 1/σ²); combined accuracy is 1/√(1/σgps² + 1/σpdr²).
         * - GPS missing or older than [gpsMaxAgeMs]: dead-reckoned estimate if present, else the stale GPS.
         * - Neither: null.
         */
        fun fuse(
            gps: LocationFix?,
            dr: DeadReckoningEstimate?,
            nowMs: Long,
            goodFixAccuracyM: Float = GOOD_FIX_ACCURACY_M,
            gpsMaxAgeMs: Long = DEFAULT_GPS_MAX_AGE_MS,
        ): FusedPosition? {
            val g = gps?.takeIf { it.point.isValid && it.accuracyM.isFinite() && it.accuracyM >= 0f }
            val d = dr?.takeIf { it.point.isValid && it.uncertaintyM.isFinite() && it.uncertaintyM >= 0.0 }
            val gpsFresh = g != null && nowMs - g.timeMs <= gpsMaxAgeMs
            if (g != null && gpsFresh && g.accuracyM <= goodFixAccuracyM) {
                return FusedPosition(g.point, g.accuracyM, PositionSource.GPS, g.timeMs)
            }
            if (g != null && gpsFresh && d != null) {
                val sg = maxOf(g.accuracyM.toDouble(), 1.0)
                val sd = maxOf(d.uncertaintyM, 1.0)
                val wg = 1.0 / (sg * sg)
                val wd = 1.0 / (sd * sd)
                val t = wg / (wg + wd) // fraction of the way from PDR towards GPS
                val acc = 1.0 / sqrt(wg + wd)
                return FusedPosition(interpolate(d.point, g.point, t), acc.toFloat(), PositionSource.BLENDED, maxOf(g.timeMs, d.lastUpdateMs))
            }
            if (d != null) {
                return FusedPosition(d.point, d.uncertaintyM.toFloat(), PositionSource.DEAD_RECKONING, d.lastUpdateMs)
            }
            if (g != null) return FusedPosition(g.point, g.accuracyM, PositionSource.GPS, g.timeMs)
            return null
        }

        /** Linear interpolation in lat/lng (fine for the < 1 km spans PDR deals with). */
        private fun interpolate(a: GeoPoint, b: GeoPoint, t: Double): GeoPoint {
            var dLng = b.lng - a.lng
            if (dLng > 180.0) dLng -= 360.0
            if (dLng < -180.0) dLng += 360.0
            var lng = a.lng + dLng * t
            lng = ((lng + 540.0) % 360.0) - 180.0
            return GeoPoint(a.lat + (b.lat - a.lat) * t, lng)
        }
    }
}
