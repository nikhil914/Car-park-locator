package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarometricFloorEstimatorTest {
    private val e = BarometricFloorEstimator

    @Test fun altitudeAtSeaLevelIsZero() {
        assertEquals(0.0, e.altitudeM(1013.25f), 1e-6)
    }

    @Test fun altitudeMatchesStandardAtmosphere() {
        // ~899 hPa ≈ 1000 m in the standard atmosphere.
        assertEquals(1000.0, e.altitudeM(898.75f), 5.0)
    }

    @Test fun pressureDropOf1hPaIsAbout8m() {
        val h = e.heightOfCarAboveUserM(1012.25f, 1013.25f)!!
        assertEquals(8.3, h, 0.3)
    }

    @Test fun floorsDownAndUp() {
        // Car saved 2 floors (~6 m, ~0.72 hPa) below where the user now stands -> negative.
        assertEquals(-2, e.floorsToCar(1013.97f, 1013.25f))
        assertEquals(2, e.floorsToCar(1012.53f, 1013.25f))
    }

    @Test fun deadbandAndMinimumOneFloor() {
        assertEquals(0, e.floorsToCar(1013.25f, 1013.30f))
        // 2 m difference: past deadband but rounds to 1 floor
        assertEquals(1, e.floorsToCar(1013.01f, 1013.25f))
    }

    @Test fun invalidInputs() {
        assertNull(e.floorsToCar(0f, 1013f))
        assertNull(e.floorsToCar(1013f, Float.NaN))
    }

    @Test fun describe() {
        assertEquals("Car is on your level", e.describe(0))
        assertEquals("Car is about 1 floor below you", e.describe(-1))
        assertEquals("Car is about 3 floors above you", e.describe(3))
    }
}
