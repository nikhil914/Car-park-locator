package com.realworldmagic.parkpin.domain

/** A single position fix from any location source. [accuracyM] is the 68% horizontal radius. */
data class LocationFix(
    val point: GeoPoint,
    val accuracyM: Float,
    val altitudeM: Double? = null,
    val timeMs: Long,
    val provider: String = "unknown",
)
