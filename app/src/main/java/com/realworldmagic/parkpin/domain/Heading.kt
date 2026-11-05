package com.realworldmagic.parkpin.domain

/**
 * Device heading.
 *
 * @param azimuthDeg true-north azimuth 0..360 of the direction the user is facing
 *   (top of phone when flat, camera direction when upright / camera mode).
 * @param pitchDeg in camera mode: camera elevation above the horizon (positive = looking up).
 *   Otherwise the raw `SensorManager.getOrientation` pitch in degrees.
 * @param rollDeg roll in degrees.
 * @param accuracy sensor accuracy, one of the ACCURACY_* constants (mirrors SensorManager values).
 */
data class Heading(val azimuthDeg: Float, val pitchDeg: Float, val rollDeg: Float, val accuracy: Int) {
    val needsCalibration: Boolean get() = accuracy <= ACCURACY_LOW

    companion object {
        const val ACCURACY_UNRELIABLE = 0
        const val ACCURACY_LOW = 1
        const val ACCURACY_MEDIUM = 2
        const val ACCURACY_HIGH = 3
    }
}
