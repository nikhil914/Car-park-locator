package com.realworldmagic.parkpin.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.realworldmagic.parkpin.domain.GeoPoint
import com.realworldmagic.parkpin.domain.Heading
import com.realworldmagic.parkpin.location.LocationProviderFactory
import kotlinx.coroutines.flow.Flow

/** Source of true-north [Heading] updates. */
interface HeadingProvider {
    /** False when the device cannot produce a heading at all (the flow then completes empty). */
    val isAvailable: Boolean

    /**
     * @param cameraMode true when the phone is held up looking through the back camera:
     *   azimuth is the camera direction, [Heading.pitchDeg] its elevation above the horizon and
     *   [Heading.rollDeg] the screen roll about the optical axis (any hold, including landscape).
     *   When false, azimuth follows the top of the phone (switching to the camera axis when the
     *   phone is near-vertical).
     */
    fun headingUpdates(cameraMode: Boolean = false): Flow<Heading>

    /** Updates magnetic declination from a location (call whenever a fix arrives). */
    fun setLastKnownLocation(point: GeoPoint, altitudeM: Double? = null, timeMs: Long = System.currentTimeMillis())
}

object HeadingProviderFactory {
    /**
     * Google's FusedOrientationProvider when Play Services and the needed sensors are present
     * (it falls back to the rotation-vector [CompassSensor] at runtime if it fails); otherwise
     * the [CompassSensor] alone.
     */
    fun create(context: Context): HeadingProvider {
        val compass = CompassSensor(context)
        return if (LocationProviderFactory.isPlayServicesAvailable(context) && hasFusedOrientationSensors(context)) {
            try {
                FusedHeadingProvider(context, fallback = compass)
            } catch (e: Throwable) {
                compass
            }
        } else {
            compass
        }
    }

    /** FOP needs accelerometer, gyroscope and magnetometer. */
    private fun hasFusedOrientationSensors(context: Context): Boolean {
        val sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        return sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
            sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null &&
            sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
    }
}
