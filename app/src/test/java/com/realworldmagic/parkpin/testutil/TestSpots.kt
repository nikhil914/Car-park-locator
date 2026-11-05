package com.realworldmagic.parkpin.testutil

import com.realworldmagic.parkpin.data.db.ParkingSpotEntity
import com.realworldmagic.parkpin.domain.GeoPoint
import com.realworldmagic.parkpin.domain.ParkingSpot

/** Builders for test data. Distinct [savedAtMs] values keep history order deterministic. */
object TestSpots {
    fun spot(
        savedAtMs: Long,
        lat: Double = 51.5072,
        lng: Double = -0.1276,
        note: String = "",
        floorLabel: String? = null,
        meterExpiresAtMs: Long? = null,
        isActive: Boolean = true,
    ) = ParkingSpot(
        point = GeoPoint(lat, lng),
        accuracyM = 6f,
        savedAtMs = savedAtMs,
        note = note,
        floorLabel = floorLabel,
        meterExpiresAtMs = meterExpiresAtMs,
        isActive = isActive,
    )

    fun entity(savedAtMs: Long, isActive: Boolean = true, note: String = "") = ParkingSpotEntity(
        lat = 51.5072,
        lng = -0.1276,
        accuracyM = 6f,
        altitudeM = null,
        savedAtMs = savedAtMs,
        note = note,
        floorLabel = null,
        photoUri = null,
        meterExpiresAtMs = null,
        isActive = isActive,
        address = null,
        pressureHpa = null,
    )
}
