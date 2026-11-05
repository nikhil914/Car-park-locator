package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for the review-hardening fixes in domain/. */
class ReviewHardeningTest {

    // --- GeoPoint: null island ---

    @Test fun nullIslandIsInvalid() {
        assertTrue(GeoPoint(0.0, 0.0).isNullIsland)
        assertFalse(GeoPoint(0.0, 0.0).isValid)
        // Points on the equator / prime meridian are still valid.
        assertTrue(GeoPoint(0.0, 0.0001).isValid)
        assertTrue(GeoPoint(51.4779, 0.0).isValid)
        assertFalse(GeoPoint(Double.NaN, 1.0).isValid)
        assertFalse(GeoPoint(91.0, 1.0).isValid)
    }

    @Test fun nullIslandFixRejectedByFilterAndSelectBest() {
        val bogus = LocationFix(GeoPoint(0.0, 0.0), 3f, timeMs = 1)
        val good = LocationFix(GeoPoint(37.0, -122.0), 20f, timeMs = 2)
        assertNull(AccuracyFilter().process(bogus))
        assertEquals(good, AccuracyFilter.selectBest(listOf(bogus, good)))
        assertNull(AccuracyFilter.selectBest(listOf(bogus)))
    }

    // --- FixFreshness ---

    @Test fun freshness() {
        val now = 1_000_000_000L
        val f = LocationFix(GeoPoint(37.0, -122.0), 5f, timeMs = now - 1_000)
        assertTrue(FixFreshness.isFresh(f, now))
        assertFalse(FixFreshness.isFresh(f.copy(timeMs = now - FixFreshness.MAX_LIVE_AGE_MS - 1), now))
        assertTrue(FixFreshness.isFresh(f.copy(timeMs = now - FixFreshness.MAX_LIVE_AGE_MS), now))
        // Small future skew tolerated, large not (bad GPS time).
        assertTrue(FixFreshness.isFresh(f.copy(timeMs = now + 5_000), now))
        assertFalse(FixFreshness.isFresh(f.copy(timeMs = now + FixFreshness.MAX_FUTURE_SKEW_MS + 1), now))
        assertEquals(1_000L, FixFreshness.ageMs(f, now))
    }

    @Test fun usable() {
        assertTrue(FixFreshness.isUsable(LocationFix(GeoPoint(37.0, -122.0), 5f, timeMs = 0)))
        assertFalse(FixFreshness.isUsable(LocationFix(GeoPoint(0.0, 0.0), 5f, timeMs = 0)))
        assertFalse(FixFreshness.isUsable(LocationFix(GeoPoint(37.0, -122.0), Float.NaN, timeMs = 0)))
        assertFalse(FixFreshness.isUsable(LocationFix(GeoPoint(37.0, -122.0), -1f, timeMs = 0)))
    }

    // --- ArrivalDetector ---

    @Test fun hugeAccuracyDoesNotDeclareArrivalFarAway() {
        val d = ArrivalDetector()
        // 250 m away with a 300 m cell fix: must not arrive.
        repeat(5) { d.update(250.0, 300f) }
        assertFalse(d.arrived)
        // Within the 30 m cap it still can.
        repeat(3) { d.update(28.0, 300f) }
        assertTrue(d.arrived)
    }

    @Test fun nonFiniteDistanceIgnored() {
        val d = ArrivalDetector()
        d.update(3.0, 4f); d.update(3.0, 4f)
        assertFalse(d.update(Double.NaN, 4f))
        // NaN neither counted nor reset the streak.
        assertTrue(d.update(3.0, 4f))
        assertTrue(d.update(Double.NaN, 4f))
        assertTrue(d.update(Double.POSITIVE_INFINITY, 4f))
        assertTrue(d.arrived)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidConfigRejected() {
        ArrivalDetector(requiredConsecutive = 0)
    }

    // --- NaN safety in formatting helpers ---

    @Test fun cardinalAndClockDoNotThrowOnNaN() {
        assertEquals("N", GeoMath.cardinal(Double.NaN))
        assertEquals("N", GeoMath.cardinal(Double.POSITIVE_INFINITY))
        assertEquals(12, ClockDirection.hour(Double.NaN))
        assertEquals("12 o'clock", ClockDirection.describe(Double.NEGATIVE_INFINITY))
    }

    // --- GuidanceCalculator ---

    private val spot = ParkingSpot(id = 1, point = GeoPoint(37.0, -122.0), accuracyM = 5f, savedAtMs = 0)

    @Test fun nanFixYieldsNoDistanceInsteadOfNaN() {
        val nanFix = LocationFix(GeoPoint(Double.NaN, -122.0), 5f, timeMs = 1)
        val s = GuidanceCalculator.calculate(spot, nanFix, Heading(10f, 0f, 0f, Heading.ACCURACY_HIGH))
        assertNull(s.distanceM)
        assertNull(s.bearingDeg)
        assertNull(s.relativeDeg)
        assertEquals("Waiting for location", GuidanceCalculator.describe(s, true))
    }

    @Test fun nanHeadingYieldsNoRelative() {
        val fix = LocationFix(GeoPoint(37.001, -122.0), 5f, timeMs = 1)
        val s = GuidanceCalculator.calculate(spot, fix, Heading(Float.NaN, 0f, 0f, Heading.ACCURACY_HIGH))
        assertNull(s.relativeDeg)
        // Falls back to the cardinal description without crashing.
        assertTrue(GuidanceCalculator.describe(s, true).startsWith("Car "))
    }

    @Test fun mockLocationFlagPropagates() {
        val fix = LocationFix(GeoPoint(37.001, -122.0), 5f, timeMs = 1, isMock = true)
        assertTrue(GuidanceCalculator.calculate(spot, fix, null).mockLocation)
        assertFalse(GuidanceCalculator.calculate(spot, fix.copy(isMock = false), null).mockLocation)
        assertFalse(GuidanceCalculator.calculate(spot, null, null).mockLocation)
    }
}
