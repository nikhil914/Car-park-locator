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

class BarometricFloorHintTest {
    private val e = BarometricFloorEstimator
    private val hour = 60L * 60 * 1000
    private val now = 1_000_000_000L

    @Test fun twoFloorsBelow() {
        val h = e.floorHint(1013.97f, 1013.25f, savedAtMs = now - 10 * 60_000, nowMs = now)!!
        assertEquals(-2, h.floors)
        assertEquals("Your car is ~2 floors below you", h.text)
        assertEquals("Estimated from air pressure. Weather changes can shift it.", h.caveat)
    }

    @Test fun oneFloorAboveAndSameLevel() {
        assertEquals("Your car is ~1 floor above you", e.floorHint(1012.89f, 1013.25f, now, now)!!.text)
        val same = e.floorHint(1013.30f, 1013.25f, now, now)!!
        assertEquals(0, same.floors)
        assertEquals("Your car is on the same level as you", same.text)
    }

    @Test fun olderSpotGetsStrongerCaveat() {
        val h = e.floorHint(1013.97f, 1013.25f, now - 3 * hour, now)!!
        assertEquals(
            "Estimated from air pressure. Saved 3h ago, so weather drift may put it off by a floor or more.",
            h.caveat,
        )
    }

    @Test fun hiddenWhenStaleMissingOrClockSkewed() {
        assertNull(e.floorHint(1013.97f, 1013.25f, now - 6 * hour, now))
        assertNull(e.floorHint(null, 1013.25f, now, now))
        assertNull(e.floorHint(1013.97f, null, now, now))
        assertNull(e.floorHint(1013.97f, 1013.25f, now + 1, now))
        assertNull(e.floorHint(0f, 1013.25f, now, now))
    }

    @Test fun justUnderMaxAgeStillShown() {
        assertEquals(-2, e.floorHint(1013.97f, 1013.25f, now - 6 * hour + 1, now)!!.floors)
    }
}
