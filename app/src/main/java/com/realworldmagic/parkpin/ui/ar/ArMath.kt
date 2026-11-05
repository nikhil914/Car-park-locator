package com.realworldmagic.parkpin.ui.ar

import kotlin.math.atan
import kotlin.math.tan

/** Field of view (degrees) of the camera as displayed in a FILL_CENTER preview. */
data class DisplayedFov(val horizontalDeg: Double, val verticalDeg: Double)

object ArMath {
    /** Default: ~60° horizontal in portrait, 4:3 sensor. */
    const val DEFAULT_TAN_HALF_H = 0.5773502691896257 // tan(30°)
    const val DEFAULT_TAN_HALF_V = DEFAULT_TAN_HALF_H * 4.0 / 3.0

    /**
     * @param tanHalfH tan(half horizontal FOV) of the full camera buffer in the current (portrait) orientation.
     * @param tanHalfV tan(half vertical FOV) of the full buffer.
     * FILL_CENTER crops whichever axis overflows; this returns the FOV that is actually visible.
     */
    fun displayedFov(tanHalfH: Double, tanHalfV: Double, viewW: Float, viewH: Float): DisplayedFov {
        if (viewW <= 0f || viewH <= 0f) return DisplayedFov(toDeg(tanHalfH), toDeg(tanHalfV))
        val bufferAspect = tanHalfH / tanHalfV
        val viewAspect = viewW.toDouble() / viewH
        return if (viewAspect < bufferAspect) {
            // View is narrower: full vertical FOV, horizontal cropped.
            DisplayedFov(toDeg(tanHalfV * viewAspect), toDeg(tanHalfV))
        } else {
            DisplayedFov(toDeg(tanHalfH), toDeg(tanHalfH / viewAspect))
        }
    }

    /** Portrait tan(half FOV) pair from sensor physical size (mm, landscape) and focal length (mm). */
    fun portraitTanHalfFov(sensorWidthMm: Float, sensorHeightMm: Float, focalMm: Float): Pair<Double, Double> {
        // In portrait the screen's horizontal axis maps to the sensor's short (height) side.
        val tanH = sensorHeightMm / (2.0 * focalMm)
        val tanV = sensorWidthMm / (2.0 * focalMm)
        return tanH to tanV
    }

    private fun toDeg(tanHalf: Double): Double = Math.toDegrees(2.0 * atan(tanHalf))

    @Suppress("unused")
    fun tanHalf(fovDeg: Double): Double = tan(Math.toRadians(fovDeg / 2.0))
}
