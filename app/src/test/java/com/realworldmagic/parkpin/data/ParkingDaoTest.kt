package com.realworldmagic.parkpin.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.realworldmagic.parkpin.data.db.ParkPinDatabase
import com.realworldmagic.parkpin.data.db.ParkingDao
import com.realworldmagic.parkpin.testutil.TestSpots.entity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParkingDaoTest {
    private lateinit var db: ParkPinDatabase
    private lateinit var dao: ParkingDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ParkPinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.parkingDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAsActive_deactivatesPreviousActive() = runTest {
        val first = dao.insertAsActive(entity(savedAtMs = 1_000))
        val second = dao.insertAsActive(entity(savedAtMs = 2_000))

        assertNotEquals(first, second)
        assertFalse(dao.getById(first)!!.isActive)
        assertTrue(dao.getById(second)!!.isActive)
        assertEquals(second, dao.getActive()!!.id)
    }

    @Test
    fun insertAsActive_ignoresCallerIdAndForcesActive() = runTest {
        val existing = dao.insertAsActive(entity(savedAtMs = 1_000))
        val id = dao.insertAsActive(entity(savedAtMs = 2_000, isActive = false).copy(id = existing))

        assertNotEquals(existing, id)
        assertTrue(dao.getById(id)!!.isActive)
    }

    @Test
    fun deactivateAll_leavesNoActiveSpot() = runTest {
        dao.insertAsActive(entity(savedAtMs = 1_000))
        dao.deactivateAll()
        assertNull(dao.getActive())
    }

    @Test
    fun updateKeepingSingleActive_reactivatesOldSpotAndDeactivatesOthers() = runTest {
        val a = dao.insertAsActive(entity(savedAtMs = 1_000))
        val b = dao.insertAsActive(entity(savedAtMs = 2_000))

        dao.updateKeepingSingleActive(dao.getById(a)!!.copy(isActive = true, note = "Level 2"))

        assertTrue(dao.getById(a)!!.isActive)
        assertEquals("Level 2", dao.getById(a)!!.note)
        assertFalse(dao.getById(b)!!.isActive)
    }

    @Test
    fun updateKeepingSingleActive_inactiveUpdateDoesNotTouchActiveSpot() = runTest {
        val a = dao.insertAsActive(entity(savedAtMs = 1_000))
        val b = dao.insertAsActive(entity(savedAtMs = 2_000))

        dao.updateKeepingSingleActive(dao.getById(a)!!.copy(note = "old"))

        assertTrue(dao.getById(b)!!.isActive)
        assertEquals("old", dao.getById(a)!!.note)
    }

    @Test
    fun observeAll_isNewestFirst() = runTest {
        dao.insertAsActive(entity(savedAtMs = 2_000))
        dao.insertAsActive(entity(savedAtMs = 3_000))
        dao.insert(entity(savedAtMs = 1_000, isActive = false))

        dao.observeAll().test {
            assertEquals(listOf(3_000L, 2_000L, 1_000L), awaitItem().map { it.savedAtMs })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteById_andDeleteAll() = runTest {
        val a = dao.insertAsActive(entity(savedAtMs = 1_000))
        dao.insertAsActive(entity(savedAtMs = 2_000))

        dao.deleteById(a)
        assertNull(dao.getById(a))

        dao.deleteAll()
        dao.observeAll().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
