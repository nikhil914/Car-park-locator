package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalDetectorTest {
    @Test fun requiresThreeConsecutiveFixes() {
        val d = ArrivalDetector()
        assertFalse(d.update(5.0, 4f))
        assertFalse(d.update(5.0, 4f))
        assertTrue(d.update(5.0, 4f))
    }

    @Test fun interruptedSequenceResetsCount() {
        val d = ArrivalDetector()
        d.update(5.0, 4f); d.update(5.0, 4f)
        assertFalse(d.update(15.0, 4f))
        assertFalse(d.update(5.0, 4f))
        assertFalse(d.update(5.0, 4f))
        assertTrue(d.update(5.0, 4f))
    }

    @Test fun enterRadiusGrowsWithAccuracy() {
        val d = ArrivalDetector()
        repeat(3) { d.update(12.0, 4f) }
        assertFalse(d.arrived)
        repeat(3) { d.update(12.0, 15f) }
        assertTrue(d.arrived)
    }

    @Test fun hysteresisKeepsArrivedBetween8And20m() {
        val d = ArrivalDetector()
        repeat(3) { d.update(3.0, 5f) }
        assertTrue(d.update(15.0, 5f))
        assertTrue(d.update(19.9, 5f))
        assertFalse(d.update(20.5, 5f))
        assertFalse(d.update(15.0, 5f))
    }

    @Test fun largeAccuracyDoesNotFlicker() {
        val d = ArrivalDetector()
        repeat(3) { d.update(25.0, 40f) }
        assertTrue(d.arrived)
        assertTrue(d.update(30.0, 40f))
        assertFalse(d.update(41.0, 40f))
    }

    @Test fun reset() {
        val d = ArrivalDetector()
        repeat(3) { d.update(1.0, 3f) }
        d.reset()
        assertFalse(d.arrived)
        assertFalse(d.update(1.0, 3f))
    }
}
