package com.realworldmagic.parkpin.domain

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Converts barometric pressure differences into an approximate floor offset.
 * Uses the international barometric formula (same as `SensorManager.getAltitude`).
 * Only pressure *deltas* are meaningful; weather drift (~1 hPa/hour) limits this to short absences.
 */
object BarometricFloorEstimator {
    const val STANDARD_SEA_LEVEL_HPA = 1013.25f
    const val DEFAULT_FLOOR_HEIGHT_M = 3.0

    /** Height differences below this (m) are reported as "same level" (half a typical garage floor). */
    const val DEFAULT_DEADBAND_M = 1.5

    /**
     * Weather can move pressure by ~1 hPa/hour (~8 m) in unsettled conditions; at or beyond this
     * age the estimate is too unreliable to show at all.
     */
    const val MAX_RELIABLE_AGE_MS = 6L * 60 * 60 * 1000

    /** Below this age the drift is usually well under one floor. */
    const val FRESH_AGE_MS = 60L * 60 * 1000

    /** Altitude (m) for [pressureHpa] relative to [seaLevelHpa]. */
    fun altitudeM(pressureHpa: Float, seaLevelHpa: Float = STANDARD_SEA_LEVEL_HPA): Double {
        require(pressureHpa > 0f && seaLevelHpa > 0f) { "pressure must be positive" }
        return 44330.0 * (1.0 - (pressureHpa.toDouble() / seaLevelHpa).pow(1.0 / 5.255))
    }

    /** Height of the car above the user (m): positive = car is higher. Null on invalid input. */
    fun heightOfCarAboveUserM(spotPressureHpa: Float, currentPressureHpa: Float): Double? {
        if (!(spotPressureHpa > 0f) || !(currentPressureHpa > 0f)) return null
        return altitudeM(spotPressureHpa) - altitudeM(currentPressureHpa)
    }

    /**
     * Floors to climb to reach the car: positive = go up, negative = go down, 0 = same level.
     * Differences smaller than [deadbandM] are treated as same level.
     */
    fun floorsToCar(
        spotPressureHpa: Float,
        currentPressureHpa: Float,
        floorHeightM: Double = DEFAULT_FLOOR_HEIGHT_M,
        deadbandM: Double = DEFAULT_DEADBAND_M,
    ): Int? {
        require(floorHeightM > 0) { "floorHeightM must be positive" }
        val h = heightOfCarAboveUserM(spotPressureHpa, currentPressureHpa) ?: return null
        if (abs(h) < deadbandM) return 0
        val floors = (h / floorHeightM).roundToInt()
        return if (floors == 0) (if (h > 0) 1 else -1) else floors
    }

    /** "Car is about 2 floors above you" / "Car is on your level". */
    fun describe(floorsToCar: Int): String = when {
        floorsToCar == 0 -> "Car is on your level"
        abs(floorsToCar) == 1 -> "Car is about 1 floor ${if (floorsToCar > 0) "above" else "below"} you"
        else -> "Car is about ${abs(floorsToCar)} floors ${if (floorsToCar > 0) "above" else "below"} you"
    }

    /** "Your car is ~2 floors below you" / "Your car is on the same level as you". */
    fun describeForGuide(floorsToCar: Int): String {
        val n = abs(floorsToCar)
        val dir = if (floorsToCar > 0) "above" else "below"
        return when (n) {
            0 -> "Your car is on the same level as you"
            1 -> "Your car is ~1 floor $dir you"
            else -> "Your car is ~$n floors $dir you"
        }
    }

    /**
     * Floor hint for the guide screen, or null when it should not be shown: a reading is
     * missing/invalid, the clock went backwards, or the spot was saved [maxAgeMs] or more ago
     * (weather drift makes older pressure deltas meaningless).
     */
    fun floorHint(
        spotPressureHpa: Float?,
        currentPressureHpa: Float?,
        savedAtMs: Long,
        nowMs: Long,
        maxAgeMs: Long = MAX_RELIABLE_AGE_MS,
        floorHeightM: Double = DEFAULT_FLOOR_HEIGHT_M,
        deadbandM: Double = DEFAULT_DEADBAND_M,
    ): FloorHint? {
        if (spotPressureHpa == null || currentPressureHpa == null) return null
        val ageMs = nowMs - savedAtMs
        if (ageMs < 0 || ageMs >= maxAgeMs) return null
        val floors = floorsToCar(spotPressureHpa, currentPressureHpa, floorHeightM, deadbandM) ?: return null
        val caveat = if (ageMs < FRESH_AGE_MS) {
            "Estimated from air pressure. Weather changes can shift it."
        } else {
            val hours = ageMs / (60L * 60 * 1000)
            "Estimated from air pressure. Saved ${hours}h ago, so weather drift may put it off by a floor or more."
        }
        return FloorHint(floors, describeForGuide(floors), caveat)
    }
}

/** Presentation-ready floor estimate: [floors] > 0 means the car is above the user. */
data class FloorHint(val floors: Int, val text: String, val caveat: String)
