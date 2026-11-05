package com.realworldmagic.parkpin.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.tan

enum class ScreenEdge { LEFT, RIGHT, TOP, BOTTOM }

sealed interface ArProjectionResult {
    /** Target is visible at pixel ([x], [y]), origin top-left. */
    data class OnScreen(val x: Float, val y: Float) : ArProjectionResult

    /**
     * Target is outside the camera view. ([x], [y]) is the point on the screen border (inset by
     * the margin) where an edge indicator should be drawn; [arrowAngleDeg] is the indicator
     * rotation, clockwise from screen-up (90 = points right). [behind] = target is behind the user.
     */
    data class OffScreen(
        val edge: ScreenEdge,
        val x: Float,
        val y: Float,
        val arrowAngleDeg: Float,
        val behind: Boolean,
    ) : ArProjectionResult
}

/** Pinhole projection of a world direction into camera-preview screen coordinates. Pure. */
object ArProjection {
    /**
     * @param relativeBearingDeg target bearing minus camera azimuth (-180..180, positive = right).
     * @param elevationDeg target elevation above the horizon as seen from the eye (see [targetElevationDeg]).
     * @param devicePitchDeg camera elevation above the horizon (positive = camera tilted up).
     * @param horizontalFovDeg / [verticalFovDeg] camera field of view as displayed on screen.
     * @param marginPx inset used for edge indicators.
     */
    fun project(
        relativeBearingDeg: Double,
        elevationDeg: Double,
        devicePitchDeg: Double,
        horizontalFovDeg: Double,
        verticalFovDeg: Double,
        screenWidthPx: Float,
        screenHeightPx: Float,
        marginPx: Float = 0f,
    ): ArProjectionResult {
        require(horizontalFovDeg > 0 && horizontalFovDeg < 180 && verticalFovDeg > 0 && verticalFovDeg < 180) {
            "FOV must be in (0, 180)"
        }
        val cx = screenWidthPx / 2.0
        val cy = screenHeightPx / 2.0
        val rel = GeoMath.relativeBearingDeg(relativeBearingDeg, 0.0)
        val vAngle = (elevationDeg - devicePitchDeg).coerceIn(-89.0, 89.0)
        val behind = abs(rel) >= 90.0

        // Direction vector in screen space (+x right, +y down), in "half-screen" units.
        val dx: Double
        val dy: Double
        if (!behind) {
            dx = tan(Math.toRadians(rel)) / tan(Math.toRadians(horizontalFovDeg / 2))
            dy = -tan(Math.toRadians(vAngle)) / tan(Math.toRadians(verticalFovDeg / 2))
            if (abs(dx) <= 1.0 && abs(dy) <= 1.0) {
                return ArProjectionResult.OnScreen((cx + dx * cx).toFloat(), (cy + dy * cy).toFloat())
            }
        } else {
            dx = if (rel < 0) -1.0 else 1.0
            dy = 0.0
        }

        // Ray from the centre to the (inset) border, in pixels.
        val px = dx * cx
        val py = dy * cy
        val halfW = (cx - marginPx).coerceAtLeast(0.0)
        val halfH = (cy - marginPx).coerceAtLeast(0.0)
        val tx = if (px != 0.0) halfW / abs(px) else Double.POSITIVE_INFINITY
        val ty = if (py != 0.0) halfH / abs(py) else Double.POSITIVE_INFINITY
        val t = min(tx, ty)
        val edge = if (tx <= ty) {
            if (px < 0) ScreenEdge.LEFT else ScreenEdge.RIGHT
        } else {
            if (py < 0) ScreenEdge.TOP else ScreenEdge.BOTTOM
        }
        val arrow = Math.toDegrees(atan2(px, -py))
        return ArProjectionResult.OffScreen(
            edge = edge,
            x = (cx + px * t).toFloat(),
            y = (cy + py * t).toFloat(),
            arrowAngleDeg = arrow.toFloat(),
            behind = behind,
        )
    }

    /**
     * Elevation (deg) of a target [horizontalDistanceM] away whose base is [targetAltitudeDeltaM]
     * above the user's feet, seen from eyes/phone [eyeHeightM] above the feet.
     */
    fun targetElevationDeg(
        horizontalDistanceM: Double,
        targetAltitudeDeltaM: Double = 0.0,
        eyeHeightM: Double = 1.4,
    ): Double {
        val dz = targetAltitudeDeltaM - eyeHeightM
        if (horizontalDistanceM <= 0.0) return if (dz >= 0) 90.0 else -90.0
        return Math.toDegrees(atan2(dz, horizontalDistanceM))
    }
}
