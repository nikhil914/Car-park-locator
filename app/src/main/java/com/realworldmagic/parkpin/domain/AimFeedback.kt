package com.realworldmagic.parkpin.domain

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Detects when the car is centred in the view (|relative bearing| ≤ [enterDeg]) with hysteresis:
 * it only leaves the centred state once |relative| > [exitDeg]. [update] returns true exactly on
 * the transition into the centred state, so the UI can fire a single haptic "tick".
 */
class CenterDetector(val enterDeg: Double = 5.0, val exitDeg: Double = 8.0) {
    init {
        require(exitDeg >= enterDeg) { "exitDeg must be >= enterDeg" }
    }

    var centered: Boolean = false
        private set

    /** @param relativeDeg relative bearing to the car, or null when unknown (resets the state). */
    fun update(relativeDeg: Double?): Boolean {
        if (relativeDeg == null || !relativeDeg.isFinite()) {
            centered = false
            return false
        }
        val off = abs(GeoMath.relativeBearingDeg(relativeDeg, 0.0))
        if (!centered && off <= enterDeg) {
            centered = true
            return true
        }
        if (centered && off > exitDeg) centered = false
        return false
    }
}

/** One vibration "ping" of the audio-free sonar: pulse length, amplitude (1..255) and gap to the next ping. */
data class SonarPing(val pulseMs: Long, val amplitude: Int, val intervalMs: Long)

/**
 * Audio-free "sonar" for visually impaired users: pings get faster and stronger as the phone
 * points closer to the car. Centred (≤ [CENTER_DEG]) gives a long, full-strength, fastest ping.
 */
object SonarPattern {
    const val CENTER_DEG = 5.0
    const val FASTEST_INTERVAL_MS = 300L
    const val SLOWEST_INTERVAL_MS = 1_600L

    fun ping(relativeDeg: Double): SonarPing {
        val off = if (relativeDeg.isFinite()) abs(GeoMath.relativeBearingDeg(relativeDeg, 0.0)) else 180.0
        if (off <= CENTER_DEG) return SonarPing(pulseMs = 90L, amplitude = 255, intervalMs = FASTEST_INTERVAL_MS)
        val f = (off / 180.0).coerceIn(0.0, 1.0) // 0 = ahead, 1 = behind
        val interval = FASTEST_INTERVAL_MS + (SLOWEST_INTERVAL_MS - FASTEST_INTERVAL_MS) * f
        val amplitude = (255 - 195 * f).roundToInt().coerceIn(1, 255)
        return SonarPing(pulseMs = 35L, amplitude = amplitude, intervalMs = interval.roundToLong())
    }
}
