package com.realworldmagic.parkpin.domain

/** A WGS84 coordinate in decimal degrees. */
data class GeoPoint(val lat: Double, val lng: Double) {
    val isValid: Boolean
        get() = lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
}
