package com.realworldmagic.parkpin.location

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.realworldmagic.parkpin.domain.LocationFix
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLocationManager

@RunWith(AndroidJUnit4::class)
class PlatformLocationProviderTest {
    private lateinit var app: Application
    private lateinit var manager: LocationManager
    private lateinit var shadowManager: ShadowLocationManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        manager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowManager = shadowOf(manager)
        shadowManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    }

    private fun grantLocation() = shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun location(provider: String, lat: Double, lng: Double, timeMs: Long, accuracy: Float? = 5f) =
        Location(provider).apply {
            latitude = lat
            longitude = lng
            time = timeMs
            accuracy?.let { this.accuracy = it }
        }

    private fun deliver(location: Location) {
        shadowManager.simulateLocation(location)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun withoutPermission_updatesCompleteEmptyAndLastKnownIsNull() = runTest {
        val provider = PlatformLocationProvider(app)
        assertTrue(provider.locationUpdates(1_000L).toList().isEmpty())
        assertNull(provider.lastKnownFix())
    }

    @Test
    fun gpsFixIsMappedToLocationFix() = runTest(UnconfinedTestDispatcher()) {
        grantLocation()
        val provider = PlatformLocationProvider(app)
        val fixes = mutableListOf<LocationFix>()
        val job = launch { provider.locationUpdates(1_000L).collect { fixes += it } }
        runCurrent()

        deliver(location(LocationManager.GPS_PROVIDER, 51.5, -0.12, timeMs = 10_000, accuracy = 4f))
        runCurrent()

        val fix = fixes.single()
        assertEquals(51.5, fix.point.lat, 1e-9)
        assertEquals(-0.12, fix.point.lng, 1e-9)
        assertEquals(4f, fix.accuracyM, 0f)
        assertEquals(10_000L, fix.timeMs)
        assertEquals(LocationManager.GPS_PROVIDER, fix.provider)
        assertNull(fix.altitudeM)
        job.cancel()
    }

    @Test
    fun networkFixIsDroppedWhileGpsIsFresh() = runTest(UnconfinedTestDispatcher()) {
        grantLocation()
        val provider = PlatformLocationProvider(app)
        val fixes = mutableListOf<LocationFix>()
        val job = launch { provider.locationUpdates(1_000L).collect { fixes += it } }
        runCurrent()

        deliver(location(LocationManager.GPS_PROVIDER, 51.5, -0.12, timeMs = 10_000))
        // 1 s after the GPS fix: within 3 x interval, dropped.
        deliver(location(LocationManager.NETWORK_PROVIDER, 51.6, -0.13, timeMs = 11_000))
        // 5 s after: GPS is stale, accepted.
        deliver(location(LocationManager.NETWORK_PROVIDER, 51.7, -0.14, timeMs = 15_000))
        runCurrent()

        assertEquals(listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER), fixes.map { it.provider })
        assertEquals(51.7, fixes.last().point.lat, 1e-9)
        job.cancel()
    }

    @Test
    fun missingAccuracyMapsToUnknownAccuracy() {
        val loc = location(LocationManager.GPS_PROVIDER, 1.0, 2.0, timeMs = 1, accuracy = null)
        loc.altitude = 35.0
        val fix = loc.toFix()
        assertEquals(UNKNOWN_ACCURACY_M, fix.accuracyM, 0f)
        assertEquals(35.0, fix.altitudeM!!, 1e-9)
    }

    @Test
    fun lastKnownFix_picksNewestAcrossProviders() = runTest {
        grantLocation()
        shadowManager.setLastKnownLocation(
            LocationManager.GPS_PROVIDER,
            location(LocationManager.GPS_PROVIDER, 10.0, 10.0, timeMs = 1_000),
        )
        shadowManager.setLastKnownLocation(
            LocationManager.NETWORK_PROVIDER,
            location(LocationManager.NETWORK_PROVIDER, 20.0, 20.0, timeMs = 2_000),
        )

        val fix = PlatformLocationProvider(app).lastKnownFix()
        assertEquals(20.0, fix!!.point.lat, 1e-9)
        assertEquals(LocationManager.NETWORK_PROVIDER, fix.provider)
    }
}
