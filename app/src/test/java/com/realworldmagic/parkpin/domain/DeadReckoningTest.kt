package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadReckoningTest {
    private val start = GeoPoint(48.137154, 11.576124)
    private fun fix(p: GeoPoint = start, acc: Float = 5f, t: Long = 1_000L) = LocationFix(p, acc, timeMs = t)

    @Test fun noEstimateBeforeGoodFix() {
        val dr = DeadReckoning()
        assertNull(dr.estimate)
        assertNull(dr.onStep(0.0, 2_000L))
        assertFalse(dr.isReliable)
    }

    @Test fun poorFixDoesNotAnchor() {
        val dr = DeadReckoning()
        assertFalse(dr.onFix(fix(acc = 25f)))
        assertNull(dr.estimate)
    }

    @Test fun fixAtThresholdAnchors() {
        val dr = DeadReckoning()
        assertTrue(dr.onFix(fix(acc = 15f)))
        val e = dr.estimate!!
        assertEquals(start, e.point)
        assertEquals(15.0, e.uncertaintyM, 1e-9)
        assertFalse(e.isDeadReckoned)
    }

    @Test fun hundredStepsNorthIsAbout72m() {
        val dr = DeadReckoning()
        dr.onFix(fix())
        repeat(100) { i -> dr.onStep(0.0, 2_000L + i * 500L) }
        val e = dr.estimate!!
        assertEquals(72.0, GeoMath.distanceMeters(start, e.point), 0.01)
        assertEquals(0.0, GeoMath.initialBearingDeg(start, e.point), 0.01)
        assertEquals(100, e.stepsSinceFix)
        assertEquals(72.0, e.distanceSinceFixM, 1e-9)
        assertTrue(e.point.lat > start.lat)
        assertEquals(start.lng, e.point.lng, 1e-9)
    }

    @Test fun uncertaintyGrowsFivePercentOfDistance() {
        val dr = DeadReckoning()
        dr.onFix(fix(acc = 4f))
        repeat(100) { dr.onStep(90.0, 2_000L) }
        assertEquals(4.0 + 0.05 * 72.0, dr.estimate!!.uncertaintyM, 1e-9)
    }

    @Test fun walkEastThenBackReturnsToStart() {
        val dr = DeadReckoning()
        dr.onFix(fix())
        repeat(50) { dr.onStep(90.0, 2_000L) }
        assertEquals(36.0, GeoMath.distanceMeters(start, dr.estimate!!.point), 0.01)
        assertEquals(90.0, GeoMath.initialBearingDeg(start, dr.estimate!!.point), 0.05)
        repeat(50) { dr.onStep(270.0, 3_000L) }
        val e = dr.estimate!!
        assertEquals(0.0, GeoMath.distanceMeters(start, e.point), 0.01)
        // Path length keeps counting even though displacement is zero.
        assertEquals(72.0, e.distanceSinceFixM, 1e-9)
    }

    @Test fun squareLoopClosesWithinCentimetres() {
        val dr = DeadReckoning()
        dr.onFix(fix())
        for (h in listOf(0.0, 90.0, 180.0, 270.0)) repeat(25) { dr.onStep(h, 2_000L) }
        assertEquals(0.0, GeoMath.distanceMeters(start, dr.estimate!!.point), 0.05)
    }

    @Test fun diagonalWalkUsesHeading() {
        val dr = DeadReckoning()
        dr.onFix(fix())
        repeat(10) { dr.onStep(225.0, 2_000L) }
        val e = dr.estimate!!
        assertEquals(7.2, GeoMath.distanceMeters(start, e.point), 0.01)
        assertEquals(225.0, GeoMath.initialBearingDeg(start, e.point), 0.1)
    }

    @Test fun headingIsNormalized() {
        val a = DeadReckoning().apply { onFix(fix()); onStep(-90.0, 2_000L) }
        val b = DeadReckoning().apply { onFix(fix()); onStep(270.0, 2_000L) }
        assertEquals(a.estimate!!.point.lat, b.estimate!!.point.lat, 1e-12)
        assertEquals(a.estimate!!.point.lng, b.estimate!!.point.lng, 1e-12)
    }

    @Test fun multiStepEventEqualsSingleSteps() {
        val a = DeadReckoning().apply { onFix(fix()); onStep(StepEvent(2_000L, count = 10), 45.0) }
        val b = DeadReckoning().apply { onFix(fix()); repeat(10) { onStep(45.0, 2_000L) } }
        assertEquals(GeoMath.distanceMeters(start, a.estimate!!.point), GeoMath.distanceMeters(start, b.estimate!!.point), 1e-6)
        assertEquals(10, a.estimate!!.stepsSinceFix)
    }

    @Test fun customStepLengthFromHeight() {
        val len = DeadReckoning.stepLengthFromHeightCm(180.0)
        assertEquals(0.747, len, 1e-9)
        val dr = DeadReckoning(stepLengthM = len)
        dr.onFix(fix())
        repeat(100) { dr.onStep(0.0, 2_000L) }
        assertEquals(74.7, GeoMath.distanceMeters(start, dr.estimate!!.point), 0.01)
    }

    @Test fun implausibleHeightOrStepFallsBackToDefault() {
        assertEquals(0.72, DeadReckoning.stepLengthFromHeightCm(null), 0.0)
        assertEquals(0.72, DeadReckoning.stepLengthFromHeightCm(30.0), 0.0)
        assertEquals(0.72, DeadReckoning.stepLengthFromHeightCm(Double.NaN), 0.0)
        assertEquals(0.72, DeadReckoning(stepLengthM = -1.0).stepLengthM, 0.0)
        assertEquals(0.72, DeadReckoning(stepLengthM = Double.POSITIVE_INFINITY).stepLengthM, 0.0)
        val dr = DeadReckoning()
        dr.stepLengthM = 0.0
        assertEquals(0.72, dr.stepLengthM, 0.0)
        dr.stepLengthM = 0.8
        assertEquals(0.8, dr.stepLengthM, 0.0)
    }

    @Test fun goodFixResetsDistanceAndUncertainty() {
        val dr = DeadReckoning()
        dr.onFix(fix())
        repeat(200) { dr.onStep(0.0, 2_000L) }
        assertTrue(dr.estimate!!.uncertaintyM > 10.0)
        val newPoint = GeoPoint(48.138, 11.577)
        assertTrue(dr.onFix(fix(newPoint, acc = 8f, t = 5_000L)))
        val e = dr.estimate!!
        assertEquals(newPoint, e.point)
        assertEquals(0, e.stepsSinceFix)
        assertEquals(0.0, e.distanceSinceFixM, 0.0)
        assertEquals(8.0, e.uncertaintyM, 1e-9)
        assertEquals(5_000L, e.anchorTimeMs)
    }

    @Test fun poorFixWhileWalkingKeepsEstimate() {
        val dr = DeadReckoning()
        dr.onFix(fix())
        repeat(20) { dr.onStep(0.0, 2_000L) }
        val before = dr.estimate!!
        assertFalse(dr.onFix(fix(GeoPoint(48.2, 11.6), acc = 60f, t = 3_000L)))
        assertEquals(before, dr.estimate)
    }

    @Test fun rejectsInvalidInput() {
        val dr = DeadReckoning()
        assertFalse(dr.onFix(fix(GeoPoint(Double.NaN, 0.0))))
        assertFalse(dr.onFix(fix(acc = Float.NaN)))
        dr.onFix(fix())
        assertNull(dr.onStep(Double.NaN, 2_000L))
        assertNull(dr.onStep(0.0, 2_000L, count = 0))
        assertNull(dr.onStep(0.0, 500L)) // before anchor time
        assertFalse(dr.onFix(fix(acc = 3f, t = 500L))) // out-of-order fix
        assertEquals(0, dr.estimate!!.stepsSinceFix)
    }

    @Test fun becomesUnreliableAfterMaxDistance() {
        val dr = DeadReckoning(maxUsefulDistanceM = 10.0)
        dr.onFix(fix())
        repeat(13) { dr.onStep(0.0, 2_000L) }
        assertTrue(dr.isReliable)
        dr.onStep(0.0, 2_000L)
        assertFalse(dr.isReliable)
        assertNotNull(dr.estimate)
    }

    @Test fun resetClearsEverything() {
        val dr = DeadReckoning()
        dr.onFix(fix())
        dr.onStep(0.0, 2_000L)
        dr.reset()
        assertNull(dr.estimate)
        assertFalse(dr.isReliable)
    }

    @Test fun destinationCrossesAntimeridian() {
        val p = DeadReckoning.destination(GeoPoint(0.0, 179.9999), 90.0, 100.0)
        assertTrue(p.isValid)
        assertTrue(p.lng < 0)
        assertEquals(100.0, GeoMath.distanceMeters(GeoPoint(0.0, 179.9999), p), 0.01)
    }

    @Test fun lastUpdateTracksLatestStep() {
        val dr = DeadReckoning()
        dr.onFix(fix(t = 1_000L))
        dr.onStep(0.0, 4_000L)
        dr.onStep(0.0, 3_000L)
        assertEquals(4_000L, dr.estimate!!.lastUpdateMs)
    }
}
