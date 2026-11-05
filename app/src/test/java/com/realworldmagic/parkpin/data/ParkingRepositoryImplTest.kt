package com.realworldmagic.parkpin.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.realworldmagic.parkpin.data.db.ParkPinDatabase
import com.realworldmagic.parkpin.domain.GeoPoint
import com.realworldmagic.parkpin.domain.ParkingSpot
import com.realworldmagic.parkpin.testutil.TestSpots.spot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParkingRepositoryImplTest {
    private lateinit var db: ParkPinDatabase
    private lateinit var repo: ParkingRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ParkPinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ParkingRepositoryImpl(db.parkingDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveNewSpot_leavesExactlyOneActive() = runTest {
        val a = repo.saveNewSpot(spot(savedAtMs = 1_000))
        val b = repo.saveNewSpot(spot(savedAtMs = 2_000))
        val c = repo.saveNewSpot(spot(savedAtMs = 3_000))

        val history = repo.observeHistory().first()
        assertEquals(1, history.count { it.isActive })
        assertEquals(c, history.single { it.isActive }.id)
        assertFalse(repo.getSpot(a)!!.isActive)
        assertFalse(repo.getSpot(b)!!.isActive)
    }

    @Test
    fun observeActiveSpot_emitsNullThenSpotThenNullAfterClear() = runTest {
        repo.observeActiveSpot().test {
            assertNull(awaitItem())

            val id = repo.saveNewSpot(spot(savedAtMs = 1_000, note = "Row C"))
            val active = awaitItem()
            assertEquals(id, active?.id)
            assertEquals("Row C", active?.note)

            repo.clearActive()
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeActiveSpot_switchesToNewestSave() = runTest {
        repo.observeActiveSpot().test {
            assertNull(awaitItem())
            val a = repo.saveNewSpot(spot(savedAtMs = 1_000))
            assertEquals(a, awaitItem()?.id)
            val b = repo.saveNewSpot(spot(savedAtMs = 2_000))
            assertEquals(b, awaitItem()?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeHistory_newestFirstAndIncludesInactive() = runTest {
        repo.observeHistory().test {
            assertTrue(awaitItem().isEmpty())
            repo.saveNewSpot(spot(savedAtMs = 1_000))
            assertEquals(listOf(1_000L), awaitItem().map { it.savedAtMs })
            repo.saveNewSpot(spot(savedAtMs = 2_000))
            val list = awaitItem()
            assertEquals(listOf(2_000L, 1_000L), list.map { it.savedAtMs })
            assertEquals(listOf(true, false), list.map { it.isActive })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun delete_activeSpot_makesObserveActiveEmitNull() = runTest {
        val id = repo.saveNewSpot(spot(savedAtMs = 1_000))
        repo.observeActiveSpot().test {
            assertEquals(id, awaitItem()?.id)
            repo.delete(id)
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(repo.getSpot(id))
        assertTrue(repo.observeHistory().first().isEmpty())
    }

    @Test
    fun observeSpot_followsUpdatesAndDeletion() = runTest {
        val id = repo.saveNewSpot(spot(savedAtMs = 1_000))
        repo.observeSpot(id).test {
            assertEquals("", awaitItem()?.note)
            repo.update(repo.getSpot(id)!!.copy(note = "P2"))
            assertEquals("P2", awaitItem()?.note)
            repo.delete(id)
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun update_keepsIdAndRoundTripsAllFields() = runTest {
        val id = repo.saveNewSpot(spot(savedAtMs = 1_000))
        val updated = ParkingSpot(
            id = id,
            point = GeoPoint(-33.8688, 151.2093),
            accuracyM = 12.5f,
            altitudeM = 42.0,
            savedAtMs = 1_000,
            note = "Level P2, row C",
            floorLabel = "P2",
            photoUri = "file:///data/photo.jpg",
            meterExpiresAtMs = 99_000,
            isActive = true,
            address = "1 George St",
            pressureHpa = 1003.2f,
        )
        repo.update(updated)
        assertEquals(updated, repo.getSpot(id))
    }

    @Test
    fun nullableFields_roundTripAsNull() = runTest {
        val id = repo.saveNewSpot(spot(savedAtMs = 1_000))
        val stored = repo.getSpot(id)!!
        assertNull(stored.altitudeM)
        assertNull(stored.floorLabel)
        assertNull(stored.address)
        assertNull(stored.photoUri)
        assertNull(stored.meterExpiresAtMs)
        assertNull(stored.pressureHpa)
    }

    @Test
    fun update_activatingOldSpotDeactivatesCurrent() = runTest {
        val a = repo.saveNewSpot(spot(savedAtMs = 1_000))
        val b = repo.saveNewSpot(spot(savedAtMs = 2_000))
        repo.update(repo.getSpot(a)!!.copy(isActive = true))

        assertEquals(a, repo.observeActiveSpot().first()?.id)
        assertFalse(repo.getSpot(b)!!.isActive)
    }

    @Test
    fun deleteAll_wipesHistory() = runTest {
        repo.saveNewSpot(spot(savedAtMs = 1_000))
        repo.saveNewSpot(spot(savedAtMs = 2_000))
        repo.deleteAll()
        assertTrue(repo.observeHistory().first().isEmpty())
        assertNull(repo.observeActiveSpot().first())
    }
}
