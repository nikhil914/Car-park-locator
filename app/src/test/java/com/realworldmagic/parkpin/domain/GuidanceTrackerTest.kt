package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceTrackerTest {
    private val start = GeoPoint(51.5007, -0.1246)
    private fun spot(id: Long, point: GeoPoint = start) = ParkingSpot(id = id, point = point, accuracyM = 5f, savedAtMs = 0L)
    private fun fix(point: GeoPoint = start, acc: Float = 5f, t: Long, mock: Boolean = false) =
        LocationFix(point, acc, timeMs = t, provider = "gps", isMock = mock)

    @Test fun rejectsTeleportingFix() {
        val t = GuidanceTracker()
        t.onSpot(spot(1, GeoPoint(51.51, -0.1246)), 0L)
        assertNotNull(t.onFix(fix(t = 1_000L), 1_000L))
        // 5 km away one second later: implausible, filtered out.
        assertNull(t.onFix(fix(GeoPoint(51.55, -0.1246), t = 2_000L), 2_000L))
        val p = t.snapshot().position!!
        assertTrue(GeoMath.distanceMeters(p.point, start) < 1.0)
    }

    @Test fun dropsVeryPoorFixes() {
        val t = GuidanceTracker()
        t.onSpot(spot(1), 0L)
        assertNull(t.onFix(fix(acc = 500f, t = 1_000L), 1_000L))
        assertNull(t.snapshot().position)
    }

    @Test fun arrivalResetsWhenSpotIdChanges() {
        val t = GuidanceTracker()
        t.onSpot(spot(1), 0L)
        repeat(3) { t.onFix(fix(t = 1_000L + it * 1_000L), 1_000L + it * 1_000L) }
        assertTrue(t.snapshot().arrived)
        // Same spot re-emitted (e.g. note edited): stays arrived.
        assertTrue(t.onSpot(spot(1).copy(note = "P2"), 4_000L).arrived)
        // Different spot: arrival state starts over.
        assertFalse(t.onSpot(spot(2), 4_000L).arrived)
    }

    @Test fun deadReckoningTakesOverWhenGpsGoesStale() {
        val t = GuidanceTracker()
        t.stepTracking = true
        t.onSpot(spot(1, GeoPoint(51.51, -0.1246)), 0L)
        t.onFix(fix(t = 1_000L), 1_000L)
        t.onHeading(0.0)
        repeat(50) { t.onStep(StepEvent(2_000L + it * 500L), 2_000L + it * 500L) }
        // 30 s after the last fix, GPS is stale: the estimate is dead-reckoned ~36 m north.
        val s = t.onTick(31_000L)
        assertTrue(s.estimated)
        val p = s.position!!
        assertEquals(GuidanceTracker.PROVIDER_PDR, p.provider)
        assertEquals(36.0, GeoMath.distanceMeters(start, p.point), 1.0)
        assertTrue(p.point.lat > start.lat)
        assertTrue(p.accuracyM > 5f)
    }

    @Test fun stepsIgnoredWhenTrackingOff() {
        val t = GuidanceTracker()
        t.onSpot(spot(1), 0L)
        t.onFix(fix(t = 1_000L), 1_000L)
        t.onHeading(90.0)
        assertNull(t.onStep(StepEvent(2_000L), 2_000L))
        assertFalse(t.onTick(60_000L).estimated)
    }

    @Test fun goodGpsKeepsOriginalFix() {
        val t = GuidanceTracker()
        t.stepTracking = true
        t.onSpot(spot(1), 0L)
        val s = t.onFix(fix(t = 1_000L), 1_000L)!!
        assertFalse(s.estimated)
        assertEquals("gps", s.position!!.provider)
    }

    @Test fun mockFlagFollowsRawFix() {
        val t = GuidanceTracker()
        t.onSpot(spot(1), 0L)
        assertTrue(t.onFix(fix(t = 1_000L, mock = true), 1_000L)!!.mockLocation)
        assertFalse(t.onFix(fix(t = 2_000L), 2_000L)!!.mockLocation)
    }
}
