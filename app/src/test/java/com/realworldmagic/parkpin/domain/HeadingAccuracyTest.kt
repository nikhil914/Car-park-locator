package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingAccuracyTest {
    @Test fun errorDegreesMapToLevels() {
        assertEquals(Heading.ACCURACY_HIGH, Heading.accuracyFromErrorDeg(0f))
        assertEquals(Heading.ACCURACY_HIGH, Heading.accuracyFromErrorDeg(10f))
        assertEquals(Heading.ACCURACY_MEDIUM, Heading.accuracyFromErrorDeg(10.5f))
        assertEquals(Heading.ACCURACY_MEDIUM, Heading.accuracyFromErrorDeg(25f))
        assertEquals(Heading.ACCURACY_LOW, Heading.accuracyFromErrorDeg(40f))
        assertEquals(Heading.ACCURACY_UNRELIABLE, Heading.accuracyFromErrorDeg(90f))
        assertEquals(Heading.ACCURACY_UNRELIABLE, Heading.accuracyFromErrorDeg(Float.NaN))
        assertEquals(Heading.ACCURACY_UNRELIABLE, Heading.accuracyFromErrorDeg(-1f))
    }

    @Test fun calibrationFollowsError() {
        fun h(err: Float) = Heading(0f, 0f, 0f, Heading.accuracyFromErrorDeg(err), err)
        assertFalse(h(8f).needsCalibration)
        assertFalse(h(20f).needsCalibration)
        assertTrue(h(35f).needsCalibration)
        assertEquals(35f, h(35f).accuracyDeg)
        assertNull(Heading(0f, 0f, 0f, Heading.ACCURACY_HIGH).accuracyDeg)
    }
}
