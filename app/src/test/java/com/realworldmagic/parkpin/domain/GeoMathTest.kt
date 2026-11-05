package com.realworldmagic.parkpin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    private val london = GeoPoint(51.5074, -0.1278)
    private val paris = GeoPoint(48.8566, 2.3522)
    private val nyc = GeoPoint(40.7128, -74.0060)
    private val la = GeoPoint(34.0522, -118.2437)

    @Test fun distance_londonParis() {
        assertEquals(343_500.0, GeoMath.distanceMeters(london, paris), 1_500.0)
    }

    @Test fun distance_nycLa() {
        assertEquals(3_936_000.0, GeoMath.distanceMeters(nyc, la), 10_000.0)
    }

    @Test fun distance_isSymmetricAndZeroForSamePoint() {
        assertEquals(GeoMath.distanceMeters(london, paris), GeoMath.distanceMeters(paris, london), 1e-6)
        assertEquals(0.0, GeoMath.distanceMeters(london, london), 1e-9)
    }

    @Test fun distance_oneDegreeLatitude() {
        val d = GeoMath.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0))
        assertEquals(111_195.0, d, 5.0)
    }

    @Test fun distance_shortParkingLotScale() {
        // ~0.0001 deg lat = 11.1 m
        val d = GeoMath.distanceMeters(GeoPoint(37.0, -122.0), GeoPoint(37.0001, -122.0))
        assertEquals(11.12, d, 0.05)
    }

    @Test fun distance_acrossAntimeridian() {
        val d = GeoMath.distanceMeters(GeoPoint(0.0, 179.9), GeoPoint(0.0, -179.9))
        assertEquals(22_239.0, d, 20.0)
    }

    @Test fun distance_poleToPole() {
        val d = GeoMath.distanceMeters(GeoPoint(90.0, 0.0), GeoPoint(-90.0, 0.0))
        assertEquals(Math.PI * GeoMath.EARTH_RADIUS_M, d, 1.0)
    }

    @Test fun distance_antipodal() {
        val d = GeoMath.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(0.0, 180.0))
        assertEquals(Math.PI * GeoMath.EARTH_RADIUS_M, d, 1.0)
    }

    @Test fun bearing_cardinalDirections() {
        val o = GeoPoint(0.0, 0.0)
        assertEquals(0.0, GeoMath.initialBearingDeg(o, GeoPoint(1.0, 0.0)), 1e-9)
        assertEquals(90.0, GeoMath.initialBearingDeg(o, GeoPoint(0.0, 1.0)), 1e-9)
        assertEquals(180.0, GeoMath.initialBearingDeg(o, GeoPoint(-1.0, 0.0)), 1e-9)
        assertEquals(270.0, GeoMath.initialBearingDeg(o, GeoPoint(0.0, -1.0)), 1e-9)
    }

    @Test fun bearing_londonParis() {
        assertEquals(148.1, GeoMath.initialBearingDeg(london, paris), 0.5)
    }

    @Test fun bearing_nycLa() {
        assertEquals(273.7, GeoMath.initialBearingDeg(nyc, la), 0.5)
    }

    @Test fun bearing_acrossAntimeridianIsEast() {
        assertEquals(90.0, GeoMath.initialBearingDeg(GeoPoint(0.0, 179.9), GeoPoint(0.0, -179.9)), 0.01)
        assertEquals(270.0, GeoMath.initialBearingDeg(GeoPoint(0.0, -179.9), GeoPoint(0.0, 179.9)), 0.01)
    }

    @Test fun bearing_fromNorthPoleIsSouth() {
        assertEquals(180.0, GeoMath.initialBearingDeg(GeoPoint(90.0, 0.0), GeoPoint(45.0, 0.0)), 1e-6)
    }

    @Test fun bearing_samePointIsZeroAndAlwaysInRange() {
        assertEquals(0.0, GeoMath.initialBearingDeg(paris, paris), 0.0)
        for (lat in -80..80 step 20) for (lng in -170..170 step 34) {
            val b = GeoMath.initialBearingDeg(GeoPoint(10.0, 10.0), GeoPoint(lat.toDouble(), lng.toDouble()))
            assertTrue("$b", b >= 0.0 && b < 360.0)
        }
    }

    @Test fun normalize() {
        assertEquals(0.0, GeoMath.normalizeDeg(0.0), 0.0)
        assertEquals(0.0, GeoMath.normalizeDeg(360.0), 0.0)
        assertEquals(0.0, GeoMath.normalizeDeg(-360.0), 0.0)
        assertEquals(350.0, GeoMath.normalizeDeg(-10.0), 1e-9)
        assertEquals(10.0, GeoMath.normalizeDeg(730.0), 1e-9)
        assertEquals(270.0, GeoMath.normalizeDeg(-450.0), 1e-9)
        assertTrue(GeoMath.normalizeDeg(-1e-15) < 360.0)
    }

    @Test fun relativeBearing() {
        assertEquals(20.0, GeoMath.relativeBearingDeg(10.0, 350.0), 1e-9)
        assertEquals(-20.0, GeoMath.relativeBearingDeg(350.0, 10.0), 1e-9)
        assertEquals(180.0, GeoMath.relativeBearingDeg(180.0, 0.0), 1e-9)
        assertEquals(-90.0, GeoMath.relativeBearingDeg(0.0, 90.0), 1e-9)
        assertEquals(0.0, GeoMath.relativeBearingDeg(725.0, 5.0), 1e-9)
    }

    @Test fun formatDistance_metric() {
        assertEquals("0 m", GeoMath.formatDistance(0.0, true))
        assertEquals("45 m", GeoMath.formatDistance(45.4, true))
        assertEquals("999 m", GeoMath.formatDistance(999.0, true))
        assertEquals("1.0 km", GeoMath.formatDistance(999.6, true))
        assertEquals("1.2 km", GeoMath.formatDistance(1234.0, true))
        assertEquals("12 km", GeoMath.formatDistance(12_345.0, true))
        assertEquals("0 m", GeoMath.formatDistance(-5.0, true))
        assertEquals("0 m", GeoMath.formatDistance(Double.NaN, true))
    }

    @Test fun formatDistance_imperial() {
        assertEquals("150 ft", GeoMath.formatDistance(45.72, false))
        assertEquals("0.8 mi", GeoMath.formatDistance(1287.5, false))
        assertEquals("12 mi", GeoMath.formatDistance(19_312.0, false))
    }

    @Test fun walkingEta() {
        assertEquals(0, GeoMath.walkingEtaMinutes(0.0))
        assertEquals(1, GeoMath.walkingEtaMinutes(10.0))
        assertEquals(1, GeoMath.walkingEtaMinutes(81.0))
        assertEquals(2, GeoMath.walkingEtaMinutes(82.0))
        assertEquals(13, GeoMath.walkingEtaMinutes(1000.0))
        assertEquals(10, GeoMath.walkingEtaMinutes(600.0, 1.0))
    }

    @Test fun cardinal() {
        assertEquals("N", GeoMath.cardinal(0.0))
        assertEquals("N", GeoMath.cardinal(359.0))
        assertEquals("N", GeoMath.cardinal(22.0))
        assertEquals("NE", GeoMath.cardinal(23.0))
        assertEquals("E", GeoMath.cardinal(90.0))
        assertEquals("SE", GeoMath.cardinal(135.0))
        assertEquals("S", GeoMath.cardinal(180.0))
        assertEquals("SW", GeoMath.cardinal(225.0))
        assertEquals("W", GeoMath.cardinal(-90.0))
        assertEquals("NW", GeoMath.cardinal(315.0))
    }
}
