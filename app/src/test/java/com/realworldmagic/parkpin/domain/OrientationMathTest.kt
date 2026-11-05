package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class OrientationMathTest {
    /** Quaternion [x, y, z, w] for a rotation of [deg] about unit axis (ax, ay, az). */
    private fun axisAngle(ax: Double, ay: Double, az: Double, deg: Double): DoubleArray {
        val h = Math.toRadians(deg) / 2
        val s = sin(h)
        return doubleArrayOf(ax * s, ay * s, az * s, cos(h))
    }

    /** Hamilton product a * b (apply b first, then a). */
    private fun mul(a: DoubleArray, b: DoubleArray): DoubleArray {
        val (ax, ay, az, aw) = a.toList()
        val (bx, by, bz, bw) = b.toList()
        return doubleArrayOf(
            aw * bx + ax * bw + ay * bz - az * by,
            aw * by - ax * bz + ay * bw + az * bx,
            aw * bz + ax * by - ay * bx + az * bw,
            aw * bw - ax * bx - ay * by - az * bz,
        )
    }

    /**
     * Attitude of a phone whose back camera points at [azimuth] / [elevation] with the screen
     * turned clockwise (seen by the user) by [roll]. Device->world, ENU.
     */
    private fun pose(azimuth: Double, elevation: Double, roll: Double): DoubleArray {
        val yaw = axisAngle(0.0, 0.0, 1.0, -azimuth) // about world Up, clockwise from above
        val tilt = axisAngle(1.0, 0.0, 0.0, 90.0 + elevation) // stand the phone up, then tilt
        val spin = axisAngle(0.0, 0.0, 1.0, -roll) // about the device Z (optical) axis
        return mul(mul(yaw, tilt), spin)
    }

    private fun matrix(q: DoubleArray) = OrientationMath.rotationMatrixFromQuaternion(q[0], q[1], q[2], q[3])

    private fun angDist(a: Double, b: Double): Double {
        val d = GeoMath.normalizeDeg(a - b)
        return minOf(d, 360.0 - d)
    }

    @Test fun identityQuaternionIsIdentityMatrix() {
        val r = OrientationMath.rotationMatrixFromQuaternion(0.0, 0.0, 0.0, 1.0)
        val id = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
        for (i in 0 until 9) assertEquals(id[i], r[i], 1e-12)
    }

    @Test fun unnormalisedQuaternionIsNormalised() {
        val a = OrientationMath.rotationMatrixFromQuaternion(floatArrayOf(0f, 0f, 0.7071068f, 0.7071068f))
        val b = OrientationMath.rotationMatrixFromQuaternion(0.0, 0.0, 2.0, 2.0)
        for (i in 0 until 9) assertEquals(a[i], b[i], 1e-6)
    }

    @Test fun uprightFacingNorth() {
        // +90 deg about East: device Y -> Up, device Z -> South.
        val p = OrientationMath.cameraPose(matrix(axisAngle(1.0, 0.0, 0.0, 90.0)))
        assertEquals(0.0, angDist(p.azimuthDeg, 0.0), 1e-6)
        assertEquals(0.0, p.elevationDeg, 1e-6)
        assertEquals(0.0, p.rollDeg, 1e-6)
    }

    @Test fun recoversAzimuthElevationRollForManyHolds() {
        for (az in listOf(0.0, 45.0, 135.0, 180.0, 270.0, 359.5)) {
            for (el in listOf(-30.0, 0.0, 20.0, 60.0)) {
                for (roll in listOf(-30.0, 0.0, 45.0, 90.0, -90.0, 170.0)) {
                    val p = OrientationMath.cameraPose(matrix(pose(az, el, roll)))
                    val msg = "az=$az el=$el roll=$roll -> $p"
                    assertTrue(msg, angDist(p.azimuthDeg, az) < 1e-6)
                    assertEquals(msg, el, p.elevationDeg, 1e-6)
                    assertTrue(msg, angDist(p.rollDeg, roll) < 1e-6)
                }
            }
        }
    }

    @Test fun azimuthWrapsAroundNorth() {
        val p = OrientationMath.cameraPose(matrix(pose(-2.0, 0.0, 0.0)))
        assertEquals(358.0, p.azimuthDeg, 1e-6)
        val q = OrientationMath.cameraPose(matrix(pose(362.0, 10.0, 30.0)))
        assertEquals(2.0, q.azimuthDeg, 1e-6)
    }

    @Test fun rollDoesNotChangeAzimuth() {
        // Landscape hold (rolled 90) must still report the camera direction, not the phone top.
        val portrait = OrientationMath.cameraPose(matrix(pose(80.0, 5.0, 0.0)))
        val landscape = OrientationMath.cameraPose(matrix(pose(80.0, 5.0, 90.0)))
        assertEquals(portrait.azimuthDeg, landscape.azimuthDeg, 1e-6)
        assertEquals(portrait.elevationDeg, landscape.elevationDeg, 1e-6)
        assertEquals(90.0, landscape.rollDeg, 1e-6)
    }

    @Test fun lookingStraightDownUsesTopOfPhone() {
        // Phone flat, screen up, top pointing East: camera looks straight down.
        val flatEast = axisAngle(0.0, 0.0, 1.0, -90.0)
        val r = matrix(flatEast)
        val p = OrientationMath.cameraPose(r)
        assertEquals(-90.0, p.elevationDeg, 1e-6)
        assertEquals(0.0, angDist(p.azimuthDeg, 90.0), 1e-6)
        assertEquals(0.0, p.rollDeg, 0.0)
        assertEquals(90.0, OrientationMath.topAzimuthDeg(r)!!, 1e-6)
    }

    @Test fun topAzimuthIsNullWhenUpright() {
        assertNull(OrientationMath.topAzimuthDeg(matrix(pose(30.0, 0.0, 0.0))))
    }
}
