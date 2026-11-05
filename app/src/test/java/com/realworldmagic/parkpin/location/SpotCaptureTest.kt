package com.realworldmagic.parkpin.location

import com.realworldmagic.parkpin.domain.FixFreshness
import com.realworldmagic.parkpin.domain.GeoPoint
import com.realworldmagic.parkpin.domain.LocationFix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotCaptureTest {
    private val now = 10_000_000_000L

    private class FakeProvider(
        private val live: Flow<LocationFix>,
        private val last: LocationFix? = null,
    ) : LocationProvider {
        override fun locationUpdates(intervalMs: Long): Flow<LocationFix> = live
        override suspend fun currentFix(timeoutMs: Long): LocationFix? = null
        override suspend fun lastKnownFix(): LocationFix? = last
    }

    private fun fix(acc: Float, lat: Double = 37.0, lng: Double = -122.0, t: Long = now, mock: Boolean = false) =
        LocationFix(GeoPoint(lat, lng), acc, timeMs = t, isMock = mock)

    @Test fun nullIslandFixCannotEndCaptureOrBeSaved() = runTest {
        val bogus = fix(1f, lat = 0.0, lng = 0.0)
        val real = fix(18f)
        val r = captureBestFix(FakeProvider(flowOf(bogus, real)), nowMs = { now })
        assertEquals(real, r.fix)
        assertEquals(1, r.samples)
        assertFalse(r.fromLastKnown)
    }

    @Test fun stopsEarlyOnTargetAccuracy() = runTest {
        val good = fix(6f)
        val later = fix(2f, t = now + 1)
        val r = captureBestFix(FakeProvider(flowOf(fix(25f), good, later)), nowMs = { now })
        assertEquals(good, r.fix)
        assertFalse(r.approximate)
    }

    @Test fun staleLastKnownIsNotUsed() = runTest {
        val old = fix(5f, t = now - FixFreshness.MAX_LAST_KNOWN_AGE_MS - 1)
        val r = captureBestFix(FakeProvider(emptyFlow(), last = old), nowMs = { now })
        assertNull(r.fix)
        assertFalse(r.fromLastKnown)
        assertTrue(r.approximate)
    }

    @Test fun recentLastKnownIsApproximateFallback() = runTest {
        val recent = fix(12f, t = now - 60_000)
        val r = captureBestFix(FakeProvider(emptyFlow(), last = recent), nowMs = { now })
        assertEquals(recent, r.fix)
        assertTrue(r.fromLastKnown)
        assertTrue(r.approximate)
    }

    @Test fun nullIslandLastKnownIsNotUsed() = runTest {
        val r = captureBestFix(FakeProvider(emptyFlow(), last = fix(5f, lat = 0.0, lng = 0.0)), nowMs = { now })
        assertNull(r.fix)
    }

    @Test fun mockFlagExposed() = runTest {
        val r = captureBestFix(FakeProvider(flowOf(fix(4f, mock = true))), nowMs = { now })
        assertTrue(r.isMock)
    }
}
