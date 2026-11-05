package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccuracyFilterTest {
    private val base = GeoPoint(37.0, -122.0)
    private fun fix(dLatM: Double = 0.0, acc: Float = 5f, t: Long = 0L, dLngM: Double = 0.0) = LocationFix(
        point = GeoPoint(base.lat + dLatM / 111_195.0, base.lng + dLngM / (111_195.0 * Math.cos(Math.toRadians(37.0)))),
        accuracyM = acc,
        timeMs = t,
    )

    @Test fun selectBest_emptyAndSingle() {
        assertNull(AccuracyFilter.selectBest(emptyList()))
        val f = fix()
        assertEquals(f, AccuracyFilter.selectBest(listOf(f)))
    }

    @Test fun selectBest_picksLowestAccuracy() {
        val a = fix(acc = 20f, t = 1); val b = fix(2.0, 6f, 2); val c = fix(1.0, 12f, 3)
        assertEquals(b, AccuracyFilter.selectBest(listOf(a, b, c)))
    }

    @Test fun selectBest_tieGoesToLatest() {
        val a = fix(acc = 8f, t = 1); val b = fix(1.0, 8f, 5)
        assertEquals(b, AccuracyFilter.selectBest(listOf(a, b)))
    }

    @Test fun selectBest_rejectsOverconfidentOutlier() {
        val good = listOf(fix(0.0, 10f, 1), fix(2.0, 9f, 2), fix(-1.0, 11f, 3), fix(1.0, 8f, 4))
        val glitch = fix(500.0, 3f, 5)
        assertEquals(good[3], AccuracyFilter.selectBest(good + glitch))
    }

    @Test fun selectBest_dropsInvalid() {
        val bad = LocationFix(GeoPoint(Double.NaN, 0.0), 1f, timeMs = 1)
        val ok = fix(acc = 30f)
        assertEquals(ok, AccuracyFilter.selectBest(listOf(bad, ok)))
    }

    @Test fun process_rejectsPoorAccuracy() {
        val f = AccuracyFilter(maxAccuracyM = 50f)
        assertNull(f.process(fix(acc = 80f)))
        assertNotNull(f.process(fix(acc = 20f)))
    }

    @Test fun process_rejectsTeleportThenReanchors() {
        val f = AccuracyFilter(maxSpeedMps = 30.0, maxConsecutiveRejects = 3)
        assertNotNull(f.process(fix(0.0, 5f, 0)))
        assertNull(f.process(fix(2000.0, 5f, 1000)))
        assertNull(f.process(fix(2000.0, 5f, 2000)))
        val re = f.process(fix(2000.0, 5f, 3000))
        assertNotNull(re)
        assertEquals(2000.0, GeoMath.distanceMeters(base, re!!.point), 1.0)
    }

    @Test fun process_rejectsOutOfOrder() {
        val f = AccuracyFilter()
        f.process(fix(0.0, 5f, 5000))
        assertNull(f.process(fix(1.0, 5f, 1000)))
    }

    @Test fun process_smoothsTowardMeasurementAndShrinksAccuracy() {
        val f = AccuracyFilter()
        f.process(fix(0.0, 10f, 0))
        val s = f.process(fix(10.0, 10f, 1000))!!
        val moved = GeoMath.distanceMeters(base, s.point)
        assertTrue("moved=$moved", moved > 4.0 && moved < 7.0)
        assertTrue(s.accuracyM < 10f)
    }

    @Test fun process_walkingIsAccepted() {
        val f = AccuracyFilter()
        for (i in 0..10) assertNotNull(f.process(fix(i * 1.4, 5f, i * 1000L)))
    }
}
