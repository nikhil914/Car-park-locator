package com.realworldmagic.parkpin.domain

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Camera pose derived from the full device attitude.
 *
 * @param azimuthDeg azimuth 0..360 of the back camera's optical axis (device -Z), in the frame of
 *   the attitude (magnetic or true north, whichever the source uses).
 * @param elevationDeg elevation of the optical axis above the horizon (positive = looking up).
 * @param rollDeg rotation of the screen about the optical axis, positive = phone turned clockwise
 *   as seen by the user looking at the screen (0 = upright portrait), range (-180, 180].
 */
data class CameraPose(val azimuthDeg: Double, val elevationDeg: Double, val rollDeg: Double)

/**
 * Pure orientation math on Android's sensor frames, no android.* imports.
 *
 * Device frame: +X right, +Y top of the phone, +Z out of the screen (towards the user), so the
 * back camera looks along -Z. World frame: X East, Y North, Z Up. A rotation matrix `r` is
 * row-major 3x3 (9 values) mapping device to world vectors, exactly as
 * `SensorManager.getRotationMatrixFromVector` produces: column j is device axis j in world coords.
 */
object OrientationMath {
    /**
     * Rotation matrix from a quaternion (x, y, z, w) such as TYPE_ROTATION_VECTOR values or
     * FusedOrientationProvider's attitude. The quaternion is normalised first.
     */
    fun rotationMatrixFromQuaternion(x: Double, y: Double, z: Double, w: Double): DoubleArray {
        val n = sqrt(x * x + y * y + z * z + w * w)
        if (n == 0.0 || !n.isFinite()) return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        val qx = x / n
        val qy = y / n
        val qz = z / n
        val qw = w / n
        return doubleArrayOf(
            1 - 2 * (qy * qy + qz * qz), 2 * (qx * qy - qz * qw), 2 * (qx * qz + qy * qw),
            2 * (qx * qy + qz * qw), 1 - 2 * (qx * qx + qz * qz), 2 * (qy * qz - qx * qw),
            2 * (qx * qz - qy * qw), 2 * (qy * qz + qx * qw), 1 - 2 * (qx * qx + qy * qy),
        )
    }

    /** Overload taking `[x, y, z, w, ...]` (extra rotation-vector fields are ignored). */
    fun rotationMatrixFromQuaternion(q: FloatArray): DoubleArray {
        require(q.size >= 4) { "quaternion needs 4 values" }
        return rotationMatrixFromQuaternion(q[0].toDouble(), q[1].toDouble(), q[2].toDouble(), q[3].toDouble())
    }

    /**
     * Camera azimuth / elevation / roll from a rotation matrix. Works for any hold: upright,
     * landscape, rolled, tilted. When the camera points (almost) straight down or up the azimuth
     * of the optical axis is undefined, so the azimuth of the top of the phone (looking down) or
     * its bottom (looking up) is used and roll is reported as 0.
     */
    fun cameraPose(r: DoubleArray): CameraPose {
        require(r.size >= 9) { "rotation matrix needs 9 values" }
        // Forward (camera) = -Z column.
        val fE = -r[2]
        val fN = -r[5]
        val fU = (-r[8]).coerceIn(-1.0, 1.0)
        val elevation = Math.toDegrees(asin(fU))
        return if (hypot(fE, fN) >= VERTICAL_EPS) {
            // World-up expressed in the image plane: (up . X, up . Y) = (r[6], r[7]).
            val roll = Math.toDegrees(atan2(-r[6], r[7]))
            CameraPose(GeoMath.normalizeDeg(Math.toDegrees(atan2(fE, fN))), elevation, roll)
        } else {
            val sign = if (fU < 0) 1.0 else -1.0
            val az = Math.toDegrees(atan2(sign * r[1], sign * r[4]))
            CameraPose(GeoMath.normalizeDeg(az), elevation, 0.0)
        }
    }

    /**
     * Azimuth (0..360) of the top of the phone (device +Y) projected on the horizontal plane, or
     * null when the phone is so upright that the projection is degenerate.
     */
    fun topAzimuthDeg(r: DoubleArray): Double? {
        require(r.size >= 9) { "rotation matrix needs 9 values" }
        if (hypot(r[1], r[4]) < VERTICAL_EPS) return null
        return GeoMath.normalizeDeg(Math.toDegrees(atan2(r[1], r[4])))
    }

    /** ~cos(84 deg): below this the horizontal projection is too short to trust. */
    private const val VERTICAL_EPS = 0.1
}
