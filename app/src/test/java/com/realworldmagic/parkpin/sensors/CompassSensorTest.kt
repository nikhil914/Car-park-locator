package com.realworldmagic.parkpin.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.realworldmagic.parkpin.domain.Heading
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import kotlin.math.cos
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class CompassSensorTest {
    private lateinit var context: Context
    private lateinit var sensorManager: SensorManager
    private lateinit var shadowSensors: ShadowSensorManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shadowSensors = shadowOf(sensorManager)
    }

    private fun addSensor(type: Int): Sensor = ShadowSensor.newInstance(type).also { shadowSensors.addSensor(it) }

    /** Rotation-vector event for the phone lying flat, rotated [deg] about the device Z axis. */
    private fun sendYaw(sensor: Sensor, deg: Double) {
        val half = Math.toRadians(deg) / 2.0
        val event = SensorEventBuilder.newBuilder()
            .setSensor(sensor)
            .setValues(floatArrayOf(0f, 0f, sin(half).toFloat(), cos(half).toFloat()))
            .setTimestamp(System.nanoTime())
            .build()
        shadowSensors.sendSensorEventToListeners(event)
    }

    @Test
    fun noSensors_isUnavailableAndFlowCompletesEmpty() = runTest {
        val compass = CompassSensor(context)
        assertFalse(compass.isAvailable)
        assertTrue(compass.headingUpdates().toList().isEmpty())
    }

    @Test
    fun accelerometerAndMagnetometer_areEnoughForAvailability() {
        addSensor(Sensor.TYPE_ACCELEROMETER)
        addSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val compass = CompassSensor(context)
        assertTrue(compass.isAvailable)
        assertFalse(compass.usesRotationVector)
    }

    @Test
    fun rotationVector_flatAndNorth_givesZeroHeading() = runTest(UnconfinedTestDispatcher()) {
        val rv = addSensor(Sensor.TYPE_ROTATION_VECTOR)
        val compass = CompassSensor(context)
        assertTrue(compass.usesRotationVector)

        val headings = mutableListOf<Heading>()
        val job = launch { compass.headingUpdates().collect { headings += it } }
        runCurrent()

        sendYaw(rv, 0.0)
        runCurrent()

        val h = headings.last()
        assertEquals(0.0, angularDistance(h.azimuthDeg.toDouble(), 0.0), 0.5)
        assertEquals(0f, h.pitchDeg, 0.5f)
        assertEquals(0f, h.rollDeg, 0.5f)
        job.cancel()
    }

    @Test
    fun rotationVector_yawIsReportedAsCompassHeading() = runTest(UnconfinedTestDispatcher()) {
        val rv = addSensor(Sensor.TYPE_ROTATION_VECTOR)
        val compass = CompassSensor(context)
        val headings = mutableListOf<Heading>()
        val job = launch { compass.headingUpdates().collect { headings += it } }
        runCurrent()

        // Counter-clockwise yaw of 90 deg (seen from above) points the top of the phone West.
        sendYaw(rv, 90.0)
        runCurrent()

        assertEquals(0.0, angularDistance(headings.last().azimuthDeg.toDouble(), 270.0), 0.5)
        job.cancel()
    }

    private fun sendQuaternion(sensor: Sensor, x: Float, y: Float, z: Float, w: Float) {
        val event = SensorEventBuilder.newBuilder()
            .setSensor(sensor)
            .setValues(floatArrayOf(x, y, z, w))
            .setTimestamp(System.nanoTime())
            .build()
        shadowSensors.sendSensorEventToListeners(event)
    }

    @Test
    fun cameraMode_uprightAndLandscape_reportCameraAzimuthAndRoll() = runTest(UnconfinedTestDispatcher()) {
        val rv = addSensor(Sensor.TYPE_ROTATION_VECTOR)
        val compass = CompassSensor(context)
        val headings = mutableListOf<Heading>()
        val job = launch { compass.headingUpdates(cameraMode = true).collect { headings += it } }
        runCurrent()

        // Upright portrait, camera facing North: +90 deg about East.
        val s = 0.70710677f
        sendQuaternion(rv, s, 0f, 0f, s)
        runCurrent()
        var h = headings.last()
        assertEquals(0.0, angularDistance(h.azimuthDeg.toDouble(), 0.0), 0.5)
        assertEquals(0f, h.pitchDeg, 0.5f)
        assertEquals(0f, h.rollDeg, 0.5f)

        // Same camera direction, phone turned 90 deg clockwise (landscape).
        sendQuaternion(rv, 0.5f, 0.5f, -0.5f, 0.5f)
        runCurrent()
        h = headings.last()
        assertEquals(0.0, angularDistance(h.azimuthDeg.toDouble(), 0.0), 0.5)
        assertEquals(0f, h.pitchDeg, 0.5f)
        assertEquals(90f, h.rollDeg, 0.5f)
        job.cancel()
    }

    @Test
    fun unregistersListenerWhenCollectionStops() = runTest(UnconfinedTestDispatcher()) {
        val rv = addSensor(Sensor.TYPE_ROTATION_VECTOR)
        val compass = CompassSensor(context)
        val headings = mutableListOf<Heading>()
        val job = launch { compass.headingUpdates().collect { headings += it } }
        runCurrent()
        job.cancel()
        runCurrent()

        sendYaw(rv, 45.0)
        runCurrent()
        assertTrue(headings.isEmpty())
    }

    private fun angularDistance(a: Double, b: Double): Double {
        val d = ((a - b) % 360.0 + 360.0) % 360.0
        return minOf(d, 360.0 - d)
    }
}
