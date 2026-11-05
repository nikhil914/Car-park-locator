package com.realworldmagic.parkpin.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "parking_spots",
    indices = [Index(value = ["is_active"]), Index(value = ["saved_at_ms"])],
)
data class ParkingSpotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "lat") val lat: Double,
    @ColumnInfo(name = "lng") val lng: Double,
    @ColumnInfo(name = "accuracy_m") val accuracyM: Float,
    @ColumnInfo(name = "altitude_m") val altitudeM: Double?,
    @ColumnInfo(name = "saved_at_ms") val savedAtMs: Long,
    @ColumnInfo(name = "note") val note: String,
    @ColumnInfo(name = "floor_label") val floorLabel: String?,
    @ColumnInfo(name = "photo_uri") val photoUri: String?,
    @ColumnInfo(name = "meter_expires_at_ms") val meterExpiresAtMs: Long?,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "address") val address: String?,
    @ColumnInfo(name = "pressure_hpa") val pressureHpa: Float?,
)
