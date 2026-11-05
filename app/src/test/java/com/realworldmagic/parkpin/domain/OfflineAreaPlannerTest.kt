package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAreaPlannerTest {
    private val p = OfflineAreaPlanner
    private val london = GeoPoint(51.5007, -0.1246)

    @Test fun knownTileCoordinates() {
        // Big Ben at z15 is tile x=16372, y=10897 on the OSM slippy map.
        assertEquals(16372, p.tileX(london.lng, 15))
        assertEquals(10897, p.tileY(london.lat, 15))
        assertEquals(0, p.tileX(-180.0, 3))
        assertEquals(7, p.tileX(180.0, 3))
        assertEquals(0, p.tileY(89.0, 3))
    }

    @Test fun boxIsAboutOneKilometer() {
        val b = p.boundingBoxAround(london)
        val heightM = GeoMath.distanceMeters(GeoPoint(b.south, london.lng), GeoPoint(b.north, london.lng))
        val widthM = GeoMath.distanceMeters(GeoPoint(london.lat, b.west), GeoPoint(london.lat, b.east))
        assertEquals(1000.0, heightM, 15.0)
        assertEquals(1000.0, widthM, 15.0)
    }

    @Test fun defaultPlanStaysUnderCap() {
        val centers = listOf(london, GeoPoint(0.0, 10.0), GeoPoint(-33.86, 151.21), GeoPoint(64.14, -21.94))
        for (c in centers) {
            val plan = p.plan(c)
            assertNotNull(plan)
            plan!!
            assertTrue("tiles ${plan.tileCount}", plan.tileCount <= OfflineAreaPlanner.MAX_TILES)
            assertEquals(14, plan.minZoom)
            assertTrue(plan.maxZoom in 14..17)
            assertEquals(p.tileCount(plan.box, plan.minZoom, plan.maxZoom), plan.tileCount)
        }
    }

    @Test fun planUsesFullZoomRangeForOneKm() {
        assertEquals(17, p.plan(london)!!.maxZoom)
    }

    @Test fun planLowersMaxZoomWhenOverCap() {
        val full = p.plan(london)!!
        val cap = full.tileCount - 1
        val plan = p.plan(london, maxTiles = cap)!!
        assertTrue(plan.maxZoom < 17)
        assertTrue(plan.tileCount <= cap)
    }

    @Test fun planNullWhenMinZoomAloneExceedsCap() {
        assertNull(p.plan(london, halfSizeM = 20_000.0))
    }

    @Test fun tileCountGrowsWithZoom() {
        val b = p.boundingBoxAround(london)
        assertTrue(p.tileCount(b, 16) > p.tileCount(b, 15))
        assertTrue(p.tileCount(b, 17) > p.tileCount(b, 16))
        assertEquals(p.tileCount(b, 15) + p.tileCount(b, 16), p.tileCount(b, 15, 16))
    }
}
