package com.realworldmagic.parkpin.domain

/** A WGS84 coordinate in decimal degrees. */
data class GeoPoint(val lat: Double, val lng: Double) {
    /**
     * True for a finite, in-range coordinate that is not exactly (0, 0). "Null island" is what
     * buggy drivers / uninitialised Location objects report, and nobody parks in the Gulf of Guinea.
     */
    val isValid: Boolean
        get() = lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0 && !isNullIsland

    /** Exactly (0, 0): almost always a bogus fix rather than a real position. */
    val isNullIsland: Boolean get() = lat == 0.0 && lng == 0.0
}
