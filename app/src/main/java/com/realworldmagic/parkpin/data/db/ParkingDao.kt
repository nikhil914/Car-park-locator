package com.realworldmagic.parkpin.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ParkingDao {
    @Query("SELECT * FROM parking_spots WHERE is_active = 1 ORDER BY saved_at_ms DESC LIMIT 1")
    abstract fun observeActive(): Flow<ParkingSpotEntity?>

    @Query("SELECT * FROM parking_spots ORDER BY saved_at_ms DESC")
    abstract fun observeAll(): Flow<List<ParkingSpotEntity>>

    @Query("SELECT * FROM parking_spots WHERE id = :id")
    abstract fun observeById(id: Long): Flow<ParkingSpotEntity?>

    @Query("SELECT * FROM parking_spots WHERE id = :id")
    abstract suspend fun getById(id: Long): ParkingSpotEntity?

    @Query("SELECT * FROM parking_spots WHERE is_active = 1 ORDER BY saved_at_ms DESC LIMIT 1")
    abstract suspend fun getActive(): ParkingSpotEntity?

    @Insert
    abstract suspend fun insert(entity: ParkingSpotEntity): Long

    @Update
    abstract suspend fun update(entity: ParkingSpotEntity)

    @Query("UPDATE parking_spots SET is_active = 0 WHERE is_active = 1")
    abstract suspend fun deactivateAll()

    @Query("UPDATE parking_spots SET is_active = 0 WHERE is_active = 1 AND id != :keepId")
    abstract suspend fun deactivateAllExcept(keepId: Long)

    @Query("DELETE FROM parking_spots WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("DELETE FROM parking_spots")
    abstract suspend fun deleteAll()

    /** Inserts [entity] as the only active spot. */
    @Transaction
    open suspend fun insertAsActive(entity: ParkingSpotEntity): Long {
        deactivateAll()
        return insert(entity.copy(id = 0, isActive = true))
    }

    /** Updates [entity]; if it is active, every other spot is deactivated. */
    @Transaction
    open suspend fun updateKeepingSingleActive(entity: ParkingSpotEntity) {
        if (entity.isActive) deactivateAllExcept(entity.id)
        update(entity)
    }
}
