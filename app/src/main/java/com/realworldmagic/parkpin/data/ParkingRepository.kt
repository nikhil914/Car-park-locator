package com.realworldmagic.parkpin.data

import com.realworldmagic.parkpin.data.db.ParkingDao
import com.realworldmagic.parkpin.data.db.toDomain
import com.realworldmagic.parkpin.data.db.toEntity
import com.realworldmagic.parkpin.domain.ParkingSpot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface ParkingRepository {
    fun observeActiveSpot(): Flow<ParkingSpot?>
    fun observeHistory(): Flow<List<ParkingSpot>>
    fun observeSpot(id: Long): Flow<ParkingSpot?>
    suspend fun saveNewSpot(spot: ParkingSpot): Long   // deactivates previous active
    suspend fun update(spot: ParkingSpot)
    suspend fun clearActive()                          // "I found my car"
    suspend fun delete(id: Long)

    /** One-shot read (e.g. from a Worker). */
    suspend fun getSpot(id: Long): ParkingSpot?

    /** Privacy: wipe all history. */
    suspend fun deleteAll()
}

class ParkingRepositoryImpl(private val dao: ParkingDao) : ParkingRepository {
    override fun observeActiveSpot(): Flow<ParkingSpot?> =
        dao.observeActive().map { it?.toDomain() }.distinctUntilChanged()

    override fun observeHistory(): Flow<List<ParkingSpot>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }.distinctUntilChanged()

    override fun observeSpot(id: Long): Flow<ParkingSpot?> =
        dao.observeById(id).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun saveNewSpot(spot: ParkingSpot): Long = dao.insertAsActive(spot.toEntity())

    override suspend fun update(spot: ParkingSpot) = dao.updateKeepingSingleActive(spot.toEntity())

    override suspend fun clearActive() = dao.deactivateAll()

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun getSpot(id: Long): ParkingSpot? = dao.getById(id)?.toDomain()

    override suspend fun deleteAll() = dao.deleteAll()
}
