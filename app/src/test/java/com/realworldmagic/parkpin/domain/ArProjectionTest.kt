package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArProjectionTest {
    private val w = 1080f
    private val h = 1920f
    private fun p(rel: Double, elev: Double = 0.0, pitch: Double = 0.0, margin: Float = 0f) =
        ArProjection.project(rel, elev, pitch, 60.0, 90.0, w, h, margin)

    @Test fun straightAheadIsCentre() {
        val r = p(0.0) as ArProjectionResult.OnScreen
        assertEquals(540f, r.x, 0.01f)
        assertEquals(960f, r.y, 0.01f)
    }

    @Test fun halfFovIsAtEdge() {
        val r = p(30.0 - 1e-9) as ArProjectionResult.OnScreen
        assertEquals(1080f, r.x, 0.5f)
        val l = p(-15.0) as ArProjectionResult.OnScreen
        val expected = 540 - 540 * Math.tan(Math.toRadians(15.0)) / Math.tan(Math.toRadians(30.0))
        assertEquals(expected.toFloat(), l.x, 0.5f)
    }

    @Test fun pitchMovesTargetVertically() {
        // Camera tilted up 20 deg: horizon target appears below centre.
        val r = p(0.0, 0.0, 20.0) as ArProjectionResult.OnScreen
        assertTrue(r.y > 960f)
        val up = p(0.0, 20.0, 0.0) as ArProjectionResult.OnScreen
        assertTrue(up.y < 960f)
        val same = p(0.0, 20.0, 20.0) as ArProjectionResult.OnScreen
        assertEquals(960f, same.y, 0.01f)
    }

    @Test fun offScreenRightAndLeft() {
        val r = p(50.0) as ArProjectionResult.OffScreen
        assertEquals(ScreenEdge.RIGHT, r.edge)
        assertEquals(1080f, r.x, 0.01f)
        assertEquals(960f, r.y, 0.01f)
        assertEquals(90f, r.arrowAngleDeg, 0.01f)
        assertTrue(!r.behind)
        val l = p(-50.0, margin = 40f) as ArProjectionResult.OffScreen
        assertEquals(ScreenEdge.LEFT, l.edge)
        assertEquals(40f, l.x, 0.01f)
        assertEquals(-90f, l.arrowAngleDeg, 0.01f)
    }

    @Test fun offScreenTopAndBottom() {
        val t = p(0.0, 0.0, -60.0) as ArProjectionResult.OffScreen
        assertEquals(ScreenEdge.TOP, t.edge)
        assertEquals(0f, t.y, 0.01f)
        assertEquals(0f, t.arrowAngleDeg, 0.01f)
        val b = p(0.0, 0.0, 60.0) as ArProjectionResult.OffScreen
        assertEquals(ScreenEdge.BOTTOM, b.edge)
        assertEquals(180f, b.arrowAngleDeg, 0.01f)
    }

    @Test fun behindPointsToShorterTurn() {
        val r = p(170.0) as ArProjectionResult.OffScreen
        assertTrue(r.behind)
        assertEquals(ScreenEdge.RIGHT, r.edge)
        val l = p(-120.0) as ArProjectionResult.OffScreen
        assertTrue(l.behind)
        assertEquals(ScreenEdge.LEFT, l.edge)
        val exactly = p(180.0) as ArProjectionResult.OffScreen
        assertEquals(ScreenEdge.RIGHT, exactly.edge)
    }

    @Test fun relativeBearingIsNormalized() {
        val r = p(350.0) as ArProjectionResult.OnScreen
        assertTrue(r.x < 540f)
    }

    @Test fun elevationHelper() {
        assertEquals(-Math.toDegrees(Math.atan2(1.4, 50.0)), ArProjection.targetElevationDeg(50.0), 1e-9)
        assertEquals(45.0, ArProjection.targetElevationDeg(10.0, 11.4), 1e-9)
        assertEquals(-90.0, ArProjection.targetElevationDeg(0.0), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidFov() {
        ArProjection.project(0.0, 0.0, 0.0, 0.0, 90.0, w, h)
    }
}
