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
        deadbandM: Double = 1.5,
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
}
