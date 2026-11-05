package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingSmootherTest {
    @Test fun firstSampleIsTakenAsIs() {
        assertEquals(123.0, HeadingSmoother().update(123.0), 1e-9)
    }

    @Test fun wrapsFrom359To1ThroughZero() {
        val s = HeadingSmoother(alpha = 0.5)
        s.update(359.0)
        val v = s.update(1.0)
        assertEquals(0.0, v, 1e-9)
        val v2 = s.update(1.0)
        assertEquals(0.5, v2, 1e-9)
    }

    @Test fun wrapsFrom1To359() {
        val s = HeadingSmoother(alpha = 0.5)
        s.update(1.0)
        assertEquals(0.0, s.update(359.0), 1e-9)
        assertEquals(359.5, s.update(359.0), 1e-9)
    }

    @Test fun neverPassesThrough180OnSmallWrap() {
        val s = HeadingSmoother(alpha = 0.15)
        s.update(350.0)
        repeat(50) {
            val v = s.update(10.0)
            assertTrue("v=$v", v >= 350.0 || v <= 10.0)
        }
        assertEquals(10.0, s.value!!, 0.01)
    }

    @Test fun convergesWithDefaultAlpha() {
        val s = HeadingSmoother()
        s.update(0.0)
        assertEquals(13.5, s.update(90.0), 1e-9)
        repeat(100) { s.update(90.0) }
        assertEquals(90.0, s.value!!, 1e-3)
    }

    @Test fun snapThreshold() {
        val s = HeadingSmoother(alpha = 0.1, snapThresholdDeg = 90.0)
        s.update(0.0)
        assertEquals(170.0, s.update(170.0), 1e-9)
    }

    @Test fun ignoresNaNAndReset() {
        val s = HeadingSmoother()
        s.update(45.0)
        assertEquals(45.0, s.update(Double.NaN), 1e-9)
        s.reset()
        assertEquals(200.0, s.update(-160.0), 1e-9)
    }
}
