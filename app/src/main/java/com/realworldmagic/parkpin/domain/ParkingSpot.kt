package com.realworldmagic.parkpin.domain

data class ParkingSpot(
    val id: Long = 0,
    val point: GeoPoint,
    val accuracyM: Float,
    val altitudeM: Double? = null,
    val savedAtMs: Long,
    val note: String = "",          // e.g. "Level P2, row C"
    val floorLabel: String? = null,
    val photoUri: String? = null,   // content:// or file path in app storage
    val meterExpiresAtMs: Long? = null,
    val isActive: Boolean = true,   // only one active at a time
    val address: String? = null,    // optional reverse-geocode when online
    /** Barometric pressure (hPa) at save time, if a barometer exists. Used for floor estimate. */
    val pressureHpa: Float? = null,
)
