package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceCalculatorTest {
    private val spot = ParkingSpot(id = 1, point = GeoPoint(0.0, 0.001), accuracyM = 5f, savedAtMs = 0)
    private val fix = LocationFix(GeoPoint(0.0, 0.0), 5f, timeMs = 1)

    @Test fun emptyInputs() {
        val s = GuidanceCalculator.calculate(null, null, null)
        assertNull(s.distanceM); assertNull(s.bearingDeg); assertNull(s.relativeDeg)
        assertFalse(s.arrived); assertFalse(s.gpsWeak); assertFalse(s.compassNeedsCalibration)
    }

    @Test fun distanceBearingWithoutHeading() {
        val s = GuidanceCalculator.calculate(spot, fix, null)
        assertEquals(111.2, s.distanceM!!, 0.2)
        assertEquals(90.0, s.bearingDeg!!, 1e-6)
        assertNull(s.relativeDeg)
        assertEquals("Car 111 m E", GuidanceCalculator.describe(s, true))
    }

    @Test fun relativeBearingUsesHeading() {
        val heading = Heading(azimuthDeg = 30f, pitchDeg = 0f, rollDeg = 0f, accuracy = Heading.ACCURACY_HIGH)
        val s = GuidanceCalculator.calculate(spot, fix, heading)
        assertEquals(60.0, s.relativeDeg!!, 1e-4)
        assertEquals("Car at 2 o'clock, 111 m", GuidanceCalculator.describe(s, true))
    }

    @Test fun relativeBearingWraps() {
        val heading = Heading(350f, 0f, 0f, Heading.ACCURACY_HIGH)
        val north = spot.copy(point = GeoPoint(0.001, 0.0))
        assertEquals(10.0, GuidanceCalculator.calculate(north, fix, heading).relativeDeg!!, 1e-4)
    }

    @Test fun flags() {
        val weak = fix.copy(accuracyM = 45f)
        val lowCompass = Heading(0f, 0f, 0f, Heading.ACCURACY_LOW)
        val s = GuidanceCalculator.calculate(spot, weak, lowCompass)
        assertTrue(s.gpsWeak)
        assertTrue(s.compassNeedsCalibration)
        val ok = GuidanceCalculator.calculate(spot, fix.copy(accuracyM = 30f), lowCompass.copy(accuracy = Heading.ACCURACY_MEDIUM))
        assertFalse(ok.gpsWeak)
        assertFalse(ok.compassNeedsCalibration)
    }

    @Test fun arrivedRequiresDistance() {
        assertFalse(GuidanceCalculator.calculate(spot, null, null, arrived = true).arrived)
        assertTrue(GuidanceCalculator.calculate(spot, fix, null, arrived = true).arrived)
    }

    @Test fun withDetector() {
        val near = LocationFix(GeoPoint(0.0, 0.00098), 4f, timeMs = 1)
        val det = ArrivalDetector()
        assertFalse(GuidanceCalculator.calculate(spot, near, null, det).arrived)
        assertFalse(GuidanceCalculator.calculate(spot, near, null, det).arrived)
        val s = GuidanceCalculator.calculate(spot, near, null, det)
        assertTrue(s.arrived)
        assertEquals("You have arrived at your car", GuidanceCalculator.describe(s, false))
    }

    @Test fun describeImperial() {
        val s = GuidanceCalculator.calculate(spot, fix, Heading(90f, 0f, 0f, 3))
        assertEquals("Car at 12 o'clock, 365 ft", GuidanceCalculator.describe(s, false))
    }
}
