package com.realworldmagic.parkpin.domain

/**
 * Device heading.
 *
 * @param azimuthDeg true-north azimuth 0..360 of the direction the user is facing
 *   (top of phone when flat, camera direction when upright / camera mode).
 * @param pitchDeg in camera mode: camera elevation above the horizon (positive = looking up).
 *   Otherwise the raw `SensorManager.getOrientation` pitch in degrees.
 * @param rollDeg in camera mode: rotation of the screen about the camera axis, positive = phone
 *   turned clockwise as seen by the user (0 = upright portrait, 90 = landscape with the top of the
 *   phone pointing right). Otherwise the raw `SensorManager.getOrientation` roll in degrees.
 * @param accuracy sensor accuracy, one of the ACCURACY_* constants (mirrors SensorManager values).
 * @param accuracyDeg estimated heading error in degrees when the source provides one, else null.
 */
data class Heading(
    val azimuthDeg: Float,
    val pitchDeg: Float,
    val rollDeg: Float,
    val accuracy: Int,
    val accuracyDeg: Float? = null,
) {
    val needsCalibration: Boolean get() = accuracy <= ACCURACY_LOW

    companion object {
        const val ACCURACY_UNRELIABLE = 0
        const val ACCURACY_LOW = 1
        const val ACCURACY_MEDIUM = 2
        const val ACCURACY_HIGH = 3

        /**
         * Maps an estimated heading error (degrees, e.g. FusedOrientationProvider's
         * headingErrorDegrees) to an ACCURACY_* level. Unknown/invalid errors are UNRELIABLE.
         */
        fun accuracyFromErrorDeg(errorDeg: Float): Int = when {
            !errorDeg.isFinite() || errorDeg < 0f -> ACCURACY_UNRELIABLE
            errorDeg <= 10f -> ACCURACY_HIGH
            errorDeg <= 25f -> ACCURACY_MEDIUM
            errorDeg <= 60f -> ACCURACY_LOW
            else -> ACCURACY_UNRELIABLE
        }
    }
}
