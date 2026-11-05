package com.realworldmagic.parkpin.data.db

import com.realworldmagic.parkpin.domain.GeoPoint
import com.realworldmagic.parkpin.domain.ParkingSpot

fun ParkingSpotEntity.toDomain(): ParkingSpot = ParkingSpot(
    id = id,
    point = GeoPoint(lat, lng),
    accuracyM = accuracyM,
    altitudeM = altitudeM,
    savedAtMs = savedAtMs,
    note = note,
    floorLabel = floorLabel,
    photoUri = photoUri,
    meterExpiresAtMs = meterExpiresAtMs,
    isActive = isActive,
    address = address,
    pressureHpa = pressureHpa,
)

fun ParkingSpot.toEntity(): ParkingSpotEntity = ParkingSpotEntity(
    id = id,
    lat = point.lat,
    lng = point.lng,
    accuracyM = accuracyM,
    altitudeM = altitudeM,
    savedAtMs = savedAtMs,
    note = note,
    floorLabel = floorLabel,
    photoUri = photoUri,
    meterExpiresAtMs = meterExpiresAtMs,
    isActive = isActive,
    address = address,
    pressureHpa = pressureHpa,
)
