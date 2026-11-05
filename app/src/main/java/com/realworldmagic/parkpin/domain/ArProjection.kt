package com.realworldmagic.parkpin.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
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
     * @param deviceRollDeg camera roll about its optical axis, positive = phone turned clockwise
     *   as seen by the user (see [OrientationMath.cameraPose]). The world appears rotated by
     *   -roll on screen, so the projected offset from the centre is rotated by -roll.
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
        deviceRollDeg: Double = 0.0,
    ): ArProjectionResult {
        require(horizontalFovDeg > 0 && horizontalFovDeg < 180 && verticalFovDeg > 0 && verticalFovDeg < 180) {
            "FOV must be in (0, 180)"
        }
        val cx = screenWidthPx / 2.0
        val cy = screenHeightPx / 2.0
        val rel = GeoMath.relativeBearingDeg(relativeBearingDeg, 0.0)
        val vAngle = (elevationDeg - devicePitchDeg).coerceIn(-89.0, 89.0)
        val behind = abs(rel) >= 90.0

        // Offset from the centre in pixels for an un-rolled camera (+x right, +y down).
        var px: Double
        var py: Double
        if (!behind) {
            px = cx * tan(Math.toRadians(rel)) / tan(Math.toRadians(horizontalFovDeg / 2))
            py = -cy * tan(Math.toRadians(vAngle)) / tan(Math.toRadians(verticalFovDeg / 2))
        } else {
            px = if (rel < 0) -cx else cx
            py = 0.0
        }
        // Roll compensation: rotate the offset by -roll about the screen centre.
        val roll = if (deviceRollDeg.isFinite()) Math.toRadians(deviceRollDeg) else 0.0
        if (roll != 0.0) {
            val c = cos(roll)
            val s = sin(roll)
            val rx = px * c + py * s
            val ry = -px * s + py * c
            px = rx
            py = ry
        }
        if (!behind && abs(px) <= cx + EPS && abs(py) <= cy + EPS) {
            return ArProjectionResult.OnScreen((cx + px).toFloat(), (cy + py).toFloat())
        }

        // Ray from the centre to the (inset) border, in pixels.
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
     * Where the horizon (elevation 0) crosses the screen, for a camera at [devicePitchDeg] /
     * [deviceRollDeg]: the line passes through ([x], [y]) and is tilted [angleDeg] (clockwise
     * from screen-horizontal, = -roll). [visible] is false when the line misses the screen.
     */
    data class HorizonLine(val x: Float, val y: Float, val angleDeg: Float, val visible: Boolean)

    fun horizon(
        devicePitchDeg: Double,
        deviceRollDeg: Double,
        verticalFovDeg: Double,
        screenWidthPx: Float,
        screenHeightPx: Float,
    ): HorizonLine {
        require(verticalFovDeg > 0 && verticalFovDeg < 180) { "FOV must be in (0, 180)" }
        val cx = screenWidthPx / 2.0
        val cy = screenHeightPx / 2.0
        val pitch = (if (devicePitchDeg.isFinite()) devicePitchDeg else 0.0).coerceIn(-89.0, 89.0)
        val roll = if (deviceRollDeg.isFinite()) Math.toRadians(deviceRollDeg) else 0.0
        // Un-rolled: horizontal line at py below the centre (camera tilted up = horizon lower).
        val py = cy * tan(Math.toRadians(pitch)) / tan(Math.toRadians(verticalFovDeg / 2))
        val x = py * sin(roll)
        val y = py * cos(roll)
        // Distance from the centre to the line is |py|; it misses the screen if beyond the half-diagonal.
        val visible = abs(py) <= hypot(cx, cy)
        return HorizonLine((cx + x).toFloat(), (cy + y).toFloat(), Math.toDegrees(-roll).toFloat(), visible)
    }

    private const val EPS = 1e-6

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
