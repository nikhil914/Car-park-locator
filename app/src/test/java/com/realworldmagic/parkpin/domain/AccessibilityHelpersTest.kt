package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityHelpersTest {
    @Test fun throttleFirstAnnouncementImmediate() {
        val t = AnnouncementThrottle(minIntervalMs = 8_000L)
        assertEquals(0L, t.delayBeforeAnnouncing("Car 40 m", 0L))
        assertEquals("Car 40 m", t.offer("Car 40 m", 0L))
    }

    @Test fun throttleSuppressesRepeatsAndSpam() {
        val t = AnnouncementThrottle(minIntervalMs = 8_000L)
        t.offer("Car 40 m", 0L)
        assertNull(t.delayBeforeAnnouncing("Car 40 m", 20_000L)) // same text never repeats
        assertNull(t.offer("Car 39 m", 1_000L))
        assertEquals(5_000L, t.delayBeforeAnnouncing("Car 38 m", 3_000L))
        assertEquals("Car 37 m", t.offer("Car 37 m", 8_000L))
        assertEquals("Car 37 m", t.lastText)
    }

    @Test fun throttleUrgentBypassesInterval() {
        val t = AnnouncementThrottle(minIntervalMs = 8_000L, urgentIntervalMs = 0L)
        t.offer("Car 9 m", 0L)
        assertTrue(t.shouldAnnounce("Arrived", 100L, urgent = true))
        assertFalse(t.shouldAnnounce("Car 8 m", 100L))
        t.reset()
        assertTrue(t.shouldAnnounce("Car 8 m", 100L))
    }

    @Test fun centerDetectorTicksOnceWithHysteresis() {
        val c = CenterDetector(enterDeg = 5.0, exitDeg = 8.0)
        assertFalse(c.update(20.0))
        assertTrue(c.update(4.0))
        assertFalse(c.update(-3.0)) // still centred: no second tick
        assertFalse(c.update(7.0)) // within exit band
        assertTrue(c.centered)
        assertFalse(c.update(9.0))
        assertFalse(c.centered)
        assertTrue(c.update(-5.0))
    }

    @Test fun centerDetectorHandlesWrapAndNull() {
        val c = CenterDetector()
        assertTrue(c.update(358.0)) // == -2°
        assertFalse(c.update(null))
        assertFalse(c.centered)
        assertFalse(c.update(Double.NaN))
        assertTrue(c.update(1.0))
    }

    @Test fun sonarFasterAndStrongerWhenAimedAtCar() {
        val ahead = SonarPattern.ping(2.0)
        val side = SonarPattern.ping(90.0)
        val behind = SonarPattern.ping(180.0)
        assertEquals(SonarPattern.FASTEST_INTERVAL_MS, ahead.intervalMs)
        assertEquals(255, ahead.amplitude)
        assertTrue(ahead.pulseMs > side.pulseMs)
        assertTrue(side.intervalMs in (ahead.intervalMs + 1) until behind.intervalMs)
        assertTrue(side.amplitude > behind.amplitude)
        assertEquals(SonarPattern.SLOWEST_INTERVAL_MS, behind.intervalMs)
        assertEquals(SonarPattern.ping(-90.0), SonarPattern.ping(90.0))
        assertEquals(SonarPattern.ping(270.0), SonarPattern.ping(-90.0))
        assertEquals(SonarPattern.SLOWEST_INTERVAL_MS, SonarPattern.ping(Double.NaN).intervalMs)
    }

    @Test fun spokenDistanceSpellsUnits() {
        assertEquals("45 meters", SpokenText.distance(45.0, metric = true))
        assertEquals("1 meter", SpokenText.distance(1.0, metric = true))
        assertEquals("1.2 kilometers", SpokenText.distance(1_200.0, metric = true))
        assertEquals("150 feet", SpokenText.distance(45.72, metric = false))
        assertEquals("1 foot", SpokenText.spellUnits("1 ft"))
        assertEquals("2.0 miles", SpokenText.spellUnits("2.0 mi"))
        assertEquals("--", SpokenText.spellUnits("--"))
    }
}
