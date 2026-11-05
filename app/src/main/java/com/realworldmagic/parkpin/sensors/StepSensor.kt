package com.realworldmagic.parkpin.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.realworldmagic.parkpin.domain.StepEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * Hardware step detector (`Sensor.TYPE_STEP_DETECTOR`): one [StepEvent] per detected step,
 * for pedestrian dead reckoning (see domain/DeadReckoning.kt and docs/DEAD_RECKONING.md).
 *
 * On Android 10 (API 29)+ step sensors require the **runtime** permission
 * `android.permission.ACTIVITY_RECOGNITION` (declared in the manifest and requested at runtime);
 * without it the sensor delivers no events, so [stepEvents] completes immediately instead.
 * On API 26–28 no runtime permission is needed.
 *
 * Steps are not conflated: every step matters for distance, so events are buffered.
 */
class StepSensor(context: Context) {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val detector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    /** True when the device has a step-detector sensor. */
    val isAvailable: Boolean get() = detector != null

    /** True when no runtime permission is needed or it has been granted. */
    fun hasPermission(): Boolean = requiredPermission?.let {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    } ?: true

    /** Usable right now: sensor present and permission granted. */
    val canDetectSteps: Boolean get() = isAvailable && hasPermission()

    /**
     * Step events with wall-clock timestamps (sensor elapsed-realtime converted to epoch millis,
     * so they compare with [com.realworldmagic.parkpin.domain.LocationFix.timeMs]).
     * Completes immediately when the sensor is missing or permission is not granted.
     */
    fun stepEvents(): Flow<StepEvent> = callbackFlow {
        val sensor = detector
        if (sensor == null || !hasPermission()) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Each event's value is 1.0 for one step.
                val count = event.values.firstOrNull()?.toInt()?.coerceAtLeast(1) ?: 1
                trySend(StepEvent(timeMs = toWallClockMs(event.timestamp), count = count))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        val ok = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        if (!ok) {
            close()
            return@callbackFlow
        }
        awaitClose { sensorManager.unregisterListener(listener) }
    }.buffer(capacity = 64)

    companion object {
        /** Runtime permission needed for step sensors, or null below API 29. */
        val requiredPermission: String?
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Manifest.permission.ACTIVITY_RECOGNITION else null

        private fun toWallClockMs(sensorTimestampNs: Long): Long {
            val ageMs = (SystemClock.elapsedRealtimeNanos() - sensorTimestampNs) / 1_000_000L
            return System.currentTimeMillis() - ageMs.coerceAtLeast(0L)
        }
    }
}
