package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockDirectionTest {
    @Test fun hours() {
        assertEquals(12, ClockDirection.hour(0.0))
        assertEquals(12, ClockDirection.hour(14.0))
        assertEquals(12, ClockDirection.hour(-14.0))
        assertEquals(1, ClockDirection.hour(30.0))
        assertEquals(2, ClockDirection.hour(60.0))
        assertEquals(3, ClockDirection.hour(90.0))
        assertEquals(6, ClockDirection.hour(180.0))
        assertEquals(6, ClockDirection.hour(-180.0))
        assertEquals(9, ClockDirection.hour(-90.0))
        assertEquals(11, ClockDirection.hour(-30.0))
        assertEquals(12, ClockDirection.hour(359.0))
    }

    @Test fun describe() {
        assertEquals("2 o'clock", ClockDirection.describe(55.0))
        assertEquals("12 o'clock", ClockDirection.describe(0.0))
    }
}
