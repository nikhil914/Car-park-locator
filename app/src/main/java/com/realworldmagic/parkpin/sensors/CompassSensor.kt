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
import com.realworldmagic.parkpin.domain.OrientationMath
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * True-north compass.
 *
 * Uses TYPE_ROTATION_VECTOR when present, otherwise accelerometer + magnetometer.
 * Azimuth is corrected by [GeomagneticField] declination for the location set via [setLastKnownLocation].
 * Heading is smoothed (circular exponential, alpha 0.15). The magnetometer's accuracy is reported
 * in [Heading.accuracy] so the UI can show a figure-8 calibration prompt.
 *
 * Flat mode assumes the activity is in portrait (natural) orientation. Camera mode derives the
 * camera azimuth / elevation / roll from the full rotation matrix ([OrientationMath.cameraPose]),
 * so it also works rolled or in landscape.
 *
 * Fallback [HeadingProvider] when Google's FusedOrientationProvider is not available.
 */
class CompassSensor(context: Context) : HeadingProvider {
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    @Volatile
    private var declinationDeg: Float = 0f

    override val isAvailable: Boolean
        get() = rotationVector != null || (accelerometer != null && magnetometer != null)

    val usesRotationVector: Boolean get() = rotationVector != null

    /** Updates magnetic declination from a location (call whenever a fix arrives). */
    override fun setLastKnownLocation(point: GeoPoint, altitudeM: Double?, timeMs: Long) {
        if (!point.isValid) return
        declinationDeg = GeomagneticField(
            point.lat.toFloat(),
            point.lng.toFloat(),
            (altitudeM ?: 0.0).toFloat(),
            timeMs,
        ).declination
    }

    /**
     * @param cameraMode true when the phone is held up looking through the back camera:
     *   azimuth is the camera direction, [Heading.pitchDeg] the camera elevation above the
     *   horizon (positive = tilted up) and [Heading.rollDeg] the screen roll. When false, azimuth
     *   follows the top of the phone, switching automatically to the camera axis when the phone
     *   is tilted near-vertical.
     */
    override fun headingUpdates(cameraMode: Boolean): Flow<Heading> = callbackFlow {
        if (!isAvailable) {
            close()
            return@callbackFlow
        }
        val smoother = HeadingSmoother(alpha = 0.15)
        val rotation = FloatArray(9)
        val rotationD = DoubleArray(9)
        val orientation = FloatArray(3)
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var haveGravity = false
        var haveMagnetic = false
        var magAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        var accuracyDeg: Float? = null

        fun emitFromRotation(accuracy: Int) {
            val azimuthRad: Double
            val pitchDeg: Float
            val rollDeg: Float
            if (cameraMode) {
                for (i in 0 until 9) rotationD[i] = rotation[i].toDouble()
                val pose = OrientationMath.cameraPose(rotationD)
                azimuthRad = Math.toRadians(pose.azimuthDeg)
                pitchDeg = pose.elevationDeg.toFloat()
                rollDeg = pose.rollDeg.toFloat()
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
            trySend(Heading(smoothed.toFloat(), pitchDeg, rollDeg, minOf(accuracy, magAccuracy), accuracyDeg))
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotation, event.values)
                        // values[4]: estimated heading accuracy (rad), -1 / absent when unknown.
                        accuracyDeg = event.values.getOrNull(4)
                            ?.takeIf { it.isFinite() && it >= 0f }
                            ?.let { Math.toDegrees(it.toDouble()).toFloat() }
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
        val rv = rotationVector
        val acc = accelerometer
        val mag = magnetometer
        val registered = if (rv != null) {
            // Magnetometer only for its accuracy (calibration prompt).
            mag?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
            sensorManager.registerListener(listener, rv, rate)
        } else if (acc != null && mag != null) {
            sensorManager.registerListener(listener, acc, rate) and
                sensorManager.registerListener(listener, mag, rate)
        } else {
            false
        }
        if (!registered) {
            // No heading will ever arrive: complete instead of hanging silently (UI shows "no compass").
            sensorManager.unregisterListener(listener)
            close()
        }
        awaitClose { sensorManager.unregisterListener(listener) }
    }.conflate()

    private fun lowPass(input: FloatArray, output: FloatArray, alpha: Float) {
        for (i in 0 until 3) output[i] = output[i] + alpha * (input[i] - output[i])
    }
}
