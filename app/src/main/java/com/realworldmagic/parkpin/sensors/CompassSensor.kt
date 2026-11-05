package com.realworldmagic.parkpin.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.realworldmagic.parkpin.domain.GeoMath
import com.realworldmagic.parkpin.domain.GeoPoint
import com.realworldmagic.parkpin.domain.Heading
import com.realworldmagic.parkpin.domain.HeadingSmoother
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.asin

/**
 * True-north compass.
 *
 * Uses TYPE_ROTATION_VECTOR when present, otherwise accelerometer + magnetometer.
 * Azimuth is corrected by [GeomagneticField] declination for the location set via [setLastKnownLocation].
 * Heading is smoothed (circular exponential, alpha 0.15). The magnetometer's accuracy is reported
 * in [Heading.accuracy] so the UI can show a figure-8 calibration prompt.
 *
 * Assumes the activity is in portrait (natural) orientation.
 */
class CompassSensor(context: Context) {
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    @Volatile
    private var declinationDeg: Float = 0f

    val isAvailable: Boolean
        get() = rotationVector != null || (accelerometer != null && magnetometer != null)

    val usesRotationVector: Boolean get() = rotationVector != null

    /** Updates magnetic declination from a location (call whenever a fix arrives). */
    fun setLastKnownLocation(point: GeoPoint, altitudeM: Double? = null, timeMs: Long = System.currentTimeMillis()) {
        if (!point.isValid) return
        declinationDeg = GeomagneticField(
            point.lat.toFloat(),
            point.lng.toFloat(),
            (altitudeM ?: 0.0).toFloat(),
            timeMs,
        ).declination
    }

    /** Heading for a phone held flat-ish in front of the user (map/compass screen). */
    fun headingUpdates(): Flow<Heading> = headingUpdates(cameraMode = false)

    /**
     * @param cameraMode true when the phone is held upright looking through the back camera:
     *   azimuth is the camera direction and [Heading.pitchDeg] is the camera elevation above the
     *   horizon (positive = tilted up). When false, azimuth follows the top of the phone, switching
     *   automatically to the camera axis when the phone is tilted near-vertical.
     */
    fun headingUpdates(cameraMode: Boolean): Flow<Heading> = callbackFlow {
        if (!isAvailable) {
            close()
            return@callbackFlow
        }
        val smoother = HeadingSmoother(alpha = 0.15)
        val rotation = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var haveGravity = false
        var haveMagnetic = false
        var magAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM

        fun emitFromRotation(accuracy: Int) {
            val azimuthRad: Double
            val pitchDeg: Float
            val rollDeg: Float
            if (cameraMode) {
                SensorManager.remapCoordinateSystem(rotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                SensorManager.getOrientation(remapped, orientation)
                azimuthRad = orientation[0].toDouble()
                // Camera (-Z) elevation: asin of its world-up component (rotation[8] is up-component of +Z).
                pitchDeg = Math.toDegrees(asin((-rotation[8]).toDouble().coerceIn(-1.0, 1.0))).toFloat()
                rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
            } else {
                SensorManager.getOrientation(rotation, orientation)
                pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
                rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
                // Horizontal components of device +Y (top) and -Z (camera), world East/North.
                val yHoriz = hypot(rotation[1].toDouble(), rotation[4].toDouble())
                val camE = -rotation[2].toDouble()
                val camN = -rotation[5].toDouble()
                val camHoriz = hypot(camE, camN)
                azimuthRad = if (yHoriz >= camHoriz || abs(pitchDeg) < 45f) {
                    orientation[0].toDouble()
                } else {
                    atan2(camE, camN)
                }
            }
            val magneticDeg = Math.toDegrees(azimuthRad)
            val trueDeg = GeoMath.normalizeDeg(magneticDeg + declinationDeg)
            val smoothed = smoother.update(trueDeg)
            trySend(Heading(smoothed.toFloat(), pitchDeg, rollDeg, minOf(accuracy, magAccuracy)))
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotation, event.values)
                        // Rotation vector accuracy is unreliable on many devices; rely on the magnetometer's.
                        emitFromRotation(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        lowPass(event.values, gravity, if (haveGravity) 0.2f else 1f)
                        haveGravity = true
                        if (rotationVector == null && haveMagnetic &&
                            SensorManager.getRotationMatrix(rotation, null, gravity, geomagnetic)
                        ) {
                            emitFromRotation(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        lowPass(event.values, geomagnetic, if (haveMagnetic) 0.2f else 1f)
                        haveMagnetic = true
                        magAccuracy = event.accuracy
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) magAccuracy = accuracy
            }
        }

        val rate = SensorManager.SENSOR_DELAY_UI
        if (rotationVector != null) {
            sensorManager.registerListener(listener, rotationVector, rate)
            // Magnetometer only for its accuracy (calibration prompt).
            magnetometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        } else {
            sensorManager.registerListener(listener, accelerometer, rate)
            sensorManager.registerListener(listener, magnetometer, rate)
        }
        awaitClose { sensorManager.unregisterListener(listener) }
    }.conflate()

    private fun lowPass(input: FloatArray, output: FloatArray, alpha: Float) {
        for (i in 0 until 3) output[i] = output[i] + alpha * (input[i] - output[i])
    }
}
