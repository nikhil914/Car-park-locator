package com.realworldmagic.parkpin.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Axis-aligned WGS84 box (degrees). Antimeridian crossing is not handled (edges are clamped). */
data class GeoBox(val north: Double, val south: Double, val east: Double, val west: Double)

/** A capped offline region: [box] at zoom [minZoom]..[maxZoom], about [tileCount] tiles per source. */
data class OfflineAreaPlan(val box: GeoBox, val minZoom: Int, val maxZoom: Int, val tileCount: Int)

/**
 * Plans a small offline map region around a point using Web Mercator (slippy map) tile math.
 * The map UI hands the box and zoom range to MapLibre's OfflineManager. Pure Kotlin so the
 * size cap can be unit tested.
 */
object OfflineAreaPlanner {
    /** Half the box side: 500 m each way gives a ~1 km x 1 km area. */
    const val DEFAULT_HALF_SIZE_M = 500.0
    const val DEFAULT_MIN_ZOOM = 14
    const val DEFAULT_MAX_ZOOM = 17

    /**
     * Politeness cap per tile source. OpenFreeMap allows offline use but asks heavy users to
     * self-host, so one region stays small (a 1 km box at z14-z17 is well under this).
     */
    const val MAX_TILES = 250

    private const val MAX_MERCATOR_LAT = 85.05112878
    private const val METERS_PER_DEG_LAT = 111_320.0

    fun boundingBoxAround(center: GeoPoint, halfSizeM: Double = DEFAULT_HALF_SIZE_M): GeoBox {
        require(center.isValid) { "invalid center" }
        require(halfSizeM > 0) { "halfSizeM must be positive" }
        val dLat = halfSizeM / METERS_PER_DEG_LAT
        val cosLat = cos(Math.toRadians(center.lat)).coerceAtLeast(0.01)
        val dLng = halfSizeM / (METERS_PER_DEG_LAT * cosLat)
        return GeoBox(
            north = (center.lat + dLat).coerceAtMost(MAX_MERCATOR_LAT),
            south = (center.lat - dLat).coerceAtLeast(-MAX_MERCATOR_LAT),
            east = (center.lng + dLng).coerceAtMost(180.0),
            west = (center.lng - dLng).coerceAtLeast(-180.0),
        )
    }

    fun tileX(lng: Double, zoom: Int): Int {
        val n = 1 shl zoom
        return floor((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    fun tileY(lat: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val latRad = Math.toRadians(lat.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT))
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        return floor(y).toInt().coerceIn(0, n - 1)
    }

    /** Tiles covering [box] at one [zoom]. */
    fun tileCount(box: GeoBox, zoom: Int): Int {
        require(zoom in 0..22) { "zoom out of range" }
        val xs = tileX(box.east, zoom) - tileX(box.west, zoom) + 1
        val ys = tileY(box.south, zoom) - tileY(box.north, zoom) + 1 // tile y grows southwards
        return xs * ys
    }

    fun tileCount(box: GeoBox, minZoom: Int, maxZoom: Int): Int =
        (minZoom..maxZoom).sumOf { tileCount(box, it) }

    /**
     * Box around [center] for [minZoom]..[maxZoom], lowering the max zoom until the total is
     * within [maxTiles]. Null when even [minZoom] alone exceeds the cap.
     */
    fun plan(
        center: GeoPoint,
        halfSizeM: Double = DEFAULT_HALF_SIZE_M,
        minZoom: Int = DEFAULT_MIN_ZOOM,
        maxZoom: Int = DEFAULT_MAX_ZOOM,
        maxTiles: Int = MAX_TILES,
    ): OfflineAreaPlan? {
        require(minZoom <= maxZoom) { "minZoom > maxZoom" }
        val box = boundingBoxAround(center, halfSizeM)
        for (z in maxZoom downTo minZoom) {
            val count = tileCount(box, minZoom, z)
            if (count <= maxTiles) return OfflineAreaPlan(box, minZoom, z, count)
        }
        return null
    }
}
