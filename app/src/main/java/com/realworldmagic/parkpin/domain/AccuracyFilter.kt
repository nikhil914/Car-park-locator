package com.realworldmagic.parkpin.domain

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Streaming location filter for guidance:
 *  - rejects fixes worse than [maxAccuracyM] and stale / out-of-order fixes;
 *  - rejects "teleport" outliers whose implied speed (beyond both accuracy circles) exceeds [maxSpeedMps];
 *    after [maxConsecutiveRejects] rejections in a row the filter re-anchors on the new fix
 *    (so a bad first fix can't lock it forever);
 *  - smooths accepted fixes with a simple accuracy-weighted Kalman filter (process noise [processNoiseMps]).
 *
 * Also provides [selectBest] for best-of-N fix selection when saving a spot.
 */
class AccuracyFilter(
    private val maxAccuracyM: Float = 50f,
    private val maxSpeedMps: Double = 30.0,
    private val processNoiseMps: Double = 3.0,
    private val maxConsecutiveRejects: Int = 3,
) {
    private var lastRaw: LocationFix? = null
    private var estimate: LocationFix? = null
    private var varianceM2: Double = 0.0
    private var rejects = 0

    /** Last smoothed estimate, or null if none yet. */
    val current: LocationFix? get() = estimate

    /** True if [fix] passes the plausibility checks against the last accepted fix. Does not mutate state. */
    fun isPlausible(fix: LocationFix): Boolean {
        if (!fix.point.isValid || !fix.accuracyM.isFinite() || fix.accuracyM < 0f) return false
        if (fix.accuracyM > maxAccuracyM) return false
        val last = lastRaw ?: return true
        if (fix.timeMs < last.timeMs) return false
        val dtS = (fix.timeMs - last.timeMs) / 1000.0
        val jump = GeoMath.distanceMeters(last.point, fix.point) - fix.accuracyM - last.accuracyM
        if (jump <= 0.0) return true
        if (dtS <= 0.0) return false
        return jump / dtS <= maxSpeedMps
    }

    /**
     * Feeds a fix. Returns the new smoothed fix if accepted, or null if rejected.
     */
    fun process(fix: LocationFix): LocationFix? {
        val basicOk = fix.point.isValid && fix.accuracyM.isFinite() && fix.accuracyM >= 0f &&
            fix.accuracyM <= maxAccuracyM
        if (!basicOk) return null
        if (!isPlausible(fix)) {
            rejects++
            if (rejects < maxConsecutiveRejects || (lastRaw != null && fix.timeMs < lastRaw!!.timeMs)) return null
            // Persistent disagreement: trust the new data, re-anchor.
            reAnchor(fix)
            return estimate
        }
        rejects = 0
        val prev = estimate
        val last = lastRaw
        if (prev == null || last == null) {
            reAnchor(fix)
            return estimate
        }
        val dtS = max(0.0, (fix.timeMs - last.timeMs) / 1000.0)
        varianceM2 += dtS * processNoiseMps * processNoiseMps
        val measVar = max(1.0, fix.accuracyM.toDouble() * fix.accuracyM)
        val k = varianceM2 / (varianceM2 + measVar)
        val lat = prev.point.lat + k * (fix.point.lat - prev.point.lat)
        val lng = prev.point.lng + k * shortestLngDelta(prev.point.lng, fix.point.lng)
        varianceM2 *= (1 - k)
        val alt = when {
            fix.altitudeM == null -> prev.altitudeM
            prev.altitudeM == null -> fix.altitudeM
            else -> prev.altitudeM + k * (fix.altitudeM - prev.altitudeM)
        }
        estimate = fix.copy(
            point = GeoPoint(lat, wrapLng(lng)),
            accuracyM = sqrt(varianceM2).toFloat(),
            altitudeM = alt,
        )
        lastRaw = fix
        return estimate
    }

    fun reset() {
        lastRaw = null
        estimate = null
        varianceM2 = 0.0
        rejects = 0
    }

    private fun reAnchor(fix: LocationFix) {
        lastRaw = fix
        estimate = fix
        varianceM2 = max(1.0, fix.accuracyM.toDouble() * fix.accuracyM)
        rejects = 0
    }

    companion object {
        /**
         * Best-of-N selection. Invalid fixes are dropped; with 3+ fixes, outliers far from the
         * component-wise median (distance > 2 * (own accuracy + median accuracy) + 5 m) are rejected.
         * Returns the most accurate survivor (latest wins ties), or the most accurate fix overall if
         * outlier rejection removed everything. Null for an empty / all-invalid list.
         */
        fun selectBest(fixes: List<LocationFix>): LocationFix? {
            val valid = fixes.filter { it.point.isValid && it.accuracyM.isFinite() && it.accuracyM >= 0f }
            if (valid.isEmpty()) return null
            val best = compareBy<LocationFix> { it.accuracyM }.thenByDescending { it.timeMs }
            if (valid.size < 3) return valid.minWithOrNull(best)
            val medLat = median(valid.map { it.point.lat })
            val medLng = median(valid.map { it.point.lng })
            val medAcc = median(valid.map { it.accuracyM.toDouble() })
            val center = GeoPoint(medLat, medLng)
            val inliers = valid.filter {
                GeoMath.distanceMeters(center, it.point) <= 2.0 * (it.accuracyM + medAcc) + 5.0
            }
            return (inliers.ifEmpty { valid }).minWithOrNull(best)
        }

        private fun median(values: List<Double>): Double {
            val s = values.sorted()
            val n = s.size
            return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
        }

        private fun shortestLngDelta(from: Double, to: Double): Double {
            var d = to - from
            if (d > 180) d -= 360
            if (d < -180) d += 360
            return d
        }

        private fun wrapLng(lng: Double): Double = when {
            lng > 180 -> lng - 360
            lng < -180 -> lng + 360
            else -> lng
        }
    }
}
