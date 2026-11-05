package com.realworldmagic.parkpin.domain

/**
 * A single position fix from any location source. [accuracyM] is the 68% horizontal radius.
 * [isMock] is true when the platform flagged the fix as coming from a mock location provider.
 */
data class LocationFix(
    val point: GeoPoint,
    val accuracyM: Float,
    val altitudeM: Double? = null,
    val timeMs: Long,
    val provider: String = "unknown",
    val isMock: Boolean = false,
)
