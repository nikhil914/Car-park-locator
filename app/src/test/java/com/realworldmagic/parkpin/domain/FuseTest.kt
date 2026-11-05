package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuseTest {
    private val start = GeoPoint(51.5007, -0.1246)

    private fun walked(steps: Int, heading: Double = 0.0): DeadReckoning =
        DeadReckoning().apply {
            onFix(LocationFix(start, 5f, timeMs = 0L))
            repeat(steps) { onStep(heading, 1_000L + it * 500L) }
        }

    @Test fun nothingGivesNull() {
        assertNull(DeadReckoning.fuse(null, null, 0L))
    }

    @Test fun goodFreshGpsWins() {
        val gps = LocationFix(GeoPoint(51.501, -0.124), 6f, timeMs = 60_000L)
        val f = DeadReckoning.fuse(gps, walked(100).estimate, nowMs = 61_000L)!!
        assertEquals(PositionSource.GPS, f.source)
        assertEquals(gps.point, f.point)
        assertEquals(6f, f.accuracyM, 0f)
    }

    @Test fun noGpsUsesDeadReckoning() {
        val dr = walked(100)
        val f = DeadReckoning.fuse(null, dr.estimate, nowMs = 60_000L)!!
        assertEquals(PositionSource.DEAD_RECKONING, f.source)
        assertEquals(dr.estimate!!.point, f.point)
        assertEquals(5f + 3.6f, f.accuracyM, 1e-4f)
    }

    @Test fun staleGpsIgnoredWhenDeadReckoningAvailable() {
        val gps = LocationFix(GeoPoint(51.6, -0.2), 4f, timeMs = 0L)
        val f = DeadReckoning.fuse(gps, walked(10).estimate, nowMs = 30_000L)!!
        assertEquals(PositionSource.DEAD_RECKONING, f.source)
    }

    @Test fun staleGpsUsedWhenNothingElse() {
        val gps = LocationFix(GeoPoint(51.6, -0.2), 40f, timeMs = 0L)
        val f = DeadReckoning.fuse(gps, null, nowMs = 30_000L)!!
        assertEquals(PositionSource.GPS, f.source)
        assertEquals(40f, f.accuracyM, 0f)
    }

    @Test fun poorGpsBlendsByInverseVariance() {
        val dr = walked(100) // uncertainty 5 + 3.6 = 8.6 m, 72 m north
        val drPoint = dr.estimate!!.point
        val gpsPoint = DeadReckoning.destination(drPoint, 90.0, 30.0) // 30 m east of PDR estimate
        val gps = LocationFix(gpsPoint, 30f, timeMs = 50_000L)
        val f = DeadReckoning.fuse(gps, dr.estimate, nowMs = 50_500L)!!
        assertEquals(PositionSource.BLENDED, f.source)
        val wg = 1.0 / (30.0 * 30.0)
        val wd = 1.0 / (8.6 * 8.6)
        val expectedShift = 30.0 * wg / (wg + wd)
        assertEquals(expectedShift, GeoMath.distanceMeters(drPoint, f.point), 0.05)
        assertEquals(1.0 / kotlin.math.sqrt(wg + wd), f.accuracyM.toDouble(), 1e-3)
        assertTrue(f.accuracyM < 8.6f)
    }

    @Test fun instanceFuseDropsUnreliableEstimate() {
        val dr = DeadReckoning(maxUsefulDistanceM = 5.0)
        dr.onFix(LocationFix(start, 5f, timeMs = 0L))
        repeat(20) { dr.onStep(0.0, 1_000L) }
        assertNull(dr.fuse(null, nowMs = 2_000L))
    }

    @Test fun toLocationFixCarriesProvider() {
        val f = FusedPosition(start, 9f, PositionSource.DEAD_RECKONING, 123L).toLocationFix(altitudeM = 12.0)
        assertEquals("pdr", f.provider)
        assertEquals(9f, f.accuracyM, 0f)
        assertEquals(123L, f.timeMs)
        assertEquals(12.0, f.altitudeM!!, 0.0)
    }

    @Test fun invalidGpsIgnored() {
        val gps = LocationFix(GeoPoint(Double.NaN, 0.0), 3f, timeMs = 0L)
        assertNull(DeadReckoning.fuse(gps, null, 0L))
    }
}
