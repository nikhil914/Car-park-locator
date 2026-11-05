package com.realworldmagic.parkpin.domain

data class GuidanceState(
    val spot: ParkingSpot? = null,
    val fix: LocationFix? = null,
    val heading: Heading? = null,
    val distanceM: Double? = null,
    val bearingDeg: Double? = null,
    val relativeDeg: Double? = null,
    val arrived: Boolean = false,
    val gpsWeak: Boolean = false,
    val compassNeedsCalibration: Boolean = false,
)
