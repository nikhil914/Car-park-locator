package com.realworldmagic.parkpin.domain

import kotlin.math.roundToInt

/** Converts a relative bearing to a clock-face direction (12 = straight ahead, 3 = right, 6 = behind). */
object ClockDirection {
    /** Clock hour 1..12 for a relative bearing in degrees (any range). Non-finite input yields 12. */
    fun hour(relativeDeg: Double): Int {
        if (!relativeDeg.isFinite()) return 12
        val n = GeoMath.normalizeDeg(relativeDeg)
        val h = (n / 30.0).roundToInt() % 12
        return if (h == 0) 12 else h
    }

    /** e.g. "2 o'clock". */
    fun describe(relativeDeg: Double): String = "${hour(relativeDeg)} o'clock"
}
