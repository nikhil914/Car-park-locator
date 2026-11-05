package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArProjectionRollTest {
    private val w = 1080f
    private val h = 1920f
    private fun p(rel: Double, roll: Double, elev: Double = 0.0, pitch: Double = 0.0, hFov: Double = 60.0, vFov: Double = 90.0) =
        ArProjection.project(rel, elev, pitch, hFov, vFov, w, h, 0f, roll)

    @Test fun rollZeroMatchesDefault() {
        for (rel in listOf(-20.0, 0.0, 12.0, 50.0, 170.0)) {
            assertEquals(ArProjection.project(rel, 0.0, 5.0, 60.0, 90.0, w, h), p(rel, 0.0, pitch = 5.0))
        }
    }

    @Test fun centreIsInvariantUnderRoll() {
        for (roll in listOf(-30.0, 0.0, 90.0, 180.0)) {
            val r = p(0.0, roll) as ArProjectionResult.OnScreen
            assertEquals(540f, r.x, 0.01f)
            assertEquals(960f, r.y, 0.01f)
        }
    }

    @Test fun roll90MovesRightTargetUp() {
        // Phone turned 90 deg clockwise: something to the right of the camera axis shows above centre.
        val unrolled = p(10.0, 0.0) as ArProjectionResult.OnScreen
        val offset = unrolled.x - 540f
        val r = p(10.0, 90.0) as ArProjectionResult.OnScreen
        assertEquals(540f, r.x, 0.5f)
        assertEquals(960f - offset, r.y, 0.5f)
    }

    @Test fun rollMinus30RotatesOffset() {
        val unrolled = p(10.0, 0.0) as ArProjectionResult.OnScreen
        val d = (unrolled.x - 540f).toDouble()
        val r = p(10.0, -30.0) as ArProjectionResult.OnScreen
        val c = Math.cos(Math.toRadians(-30.0))
        val s = Math.sin(Math.toRadians(-30.0))
        assertEquals((540 + d * c).toFloat(), r.x, 0.5f)
        assertEquals((960 - d * s).toFloat(), r.y, 0.5f)
        assertTrue(r.y > 960f) // turned counter-clockwise: right-hand target drops below centre
    }

    @Test fun rollWrapAroundBearing() {
        // 355 deg relative = 5 deg left; with roll 90 it shows below centre.
        val r = p(355.0, 90.0) as ArProjectionResult.OnScreen
        assertEquals(540f, r.x, 0.5f)
        assertTrue(r.y > 960f)
        // Roll of 360 is the same as 0.
        val a = p(20.0, 360.0) as ArProjectionResult.OnScreen
        val b = p(20.0, 0.0) as ArProjectionResult.OnScreen
        assertEquals(b.x, a.x, 0.01f)
        assertEquals(b.y, a.y, 0.01f)
    }

    @Test fun offScreenEdgeFollowsRoll() {
        // 50 deg right is off-screen; turned 90 deg clockwise it is off the TOP edge.
        val r = p(50.0, 90.0) as ArProjectionResult.OffScreen
        assertEquals(ScreenEdge.TOP, r.edge)
        assertEquals(0f, r.arrowAngleDeg, 0.01f)
        // Behind-right with roll 90 also points up.
        val b = p(170.0, 90.0) as ArProjectionResult.OffScreen
        assertTrue(b.behind)
        assertEquals(ScreenEdge.TOP, b.edge)
    }

    @Test fun rolledPointCanLeaveNarrowScreen() {
        // 25 deg right fits the width unrolled; rolled 90 it maps onto the long axis and still fits.
        assertTrue(p(25.0, 0.0) is ArProjectionResult.OnScreen)
        assertTrue(p(25.0, 90.0) is ArProjectionResult.OnScreen)
        // Elevation 40 deg fits the 90 deg vertical FOV unrolled but not the narrow width when rolled 90.
        assertTrue(p(0.0, 0.0, elev = 40.0) is ArProjectionResult.OnScreen)
        assertTrue(p(0.0, 90.0, elev = 40.0) is ArProjectionResult.OffScreen)
    }

    @Test fun nanRollIsIgnored() {
        assertEquals(p(10.0, 0.0), p(10.0, Double.NaN))
    }

    @Test fun horizonLevelAndCentred() {
        val hz = ArProjection.horizon(0.0, 0.0, 90.0, w, h)
        assertEquals(540f, hz.x, 0.01f)
        assertEquals(960f, hz.y, 0.01f)
        assertEquals(0f, hz.angleDeg, 0.01f)
        assertTrue(hz.visible)
    }

    @Test fun horizonDropsWhenLookingUp() {
        val hz = ArProjection.horizon(20.0, 0.0, 90.0, w, h)
        assertEquals((960 + 960 * Math.tan(Math.toRadians(20.0))).toFloat(), hz.y, 0.5f)
        assertEquals(540f, hz.x, 0.01f)
    }

    @Test fun horizonTiltsWithRoll() {
        val hz = ArProjection.horizon(0.0, -30.0, 90.0, w, h)
        assertEquals(30f, hz.angleDeg, 0.01f)
        val rolled = ArProjection.horizon(20.0, 90.0, 90.0, w, h)
        assertEquals(-90f, rolled.angleDeg, 0.01f)
        assertTrue(rolled.x > 540f)
        assertEquals(960f, rolled.y, 0.5f)
    }

    @Test fun horizonInvisibleWhenLookingStraightDown() {
        assertFalse(ArProjection.horizon(-85.0, 0.0, 60.0, w, h).visible)
    }
}
