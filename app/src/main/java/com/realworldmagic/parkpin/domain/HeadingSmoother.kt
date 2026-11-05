package com.realworldmagic.parkpin.domain

import kotlin.math.abs

/**
 * Circular exponential smoothing of a heading: moves the estimate by [alpha] times the shortest
 * signed angular difference, so 359 -> 1 goes through 0, never through 180.
 * Large jumps (> [snapThresholdDeg]) snap immediately, e.g. after picking the phone up.
 */
class HeadingSmoother(
    private val alpha: Double = 0.15,
    private val snapThresholdDeg: Double = 360.0,
) {
    init {
        require(alpha > 0.0 && alpha <= 1.0) { "alpha must be in (0, 1]" }
    }

    var value: Double? = null
        private set

    /** Feeds a raw heading in degrees and returns the smoothed heading in [0, 360). */
    fun update(rawDeg: Double): Double {
        if (!rawDeg.isFinite()) return value ?: 0.0
        val raw = GeoMath.normalizeDeg(rawDeg)
        val prev = value
        val next = if (prev == null) {
            raw
        } else {
            val delta = GeoMath.relativeBearingDeg(raw, prev)
            if (abs(delta) >= snapThresholdDeg) raw else GeoMath.normalizeDeg(prev + alpha * delta)
        }
        value = next
        return next
    }

    fun update(rawDeg: Float): Float = update(rawDeg.toDouble()).toFloat()

    fun reset() {
        value = null
    }
}
