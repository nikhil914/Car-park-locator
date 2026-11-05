package com.realworldmagic.parkpin.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Barometric pressure in hPa (for floor estimation in multi-level garages). */
class BarometerSensor(context: Context) {
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressure: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val isAvailable: Boolean get() = pressure != null

    /** Lightly smoothed pressure readings (hPa). Completes immediately when no barometer exists. */
    fun pressureUpdates(): Flow<Float> = callbackFlow {
        val sensor = pressure
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        var smoothed = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val v = event.values.firstOrNull() ?: return
                if (!(v > 0f)) return
                smoothed = if (smoothed.isNaN()) v else smoothed + 0.2f * (v - smoothed)
                trySend(smoothed)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensorManager.unregisterListener(listener) }
    }.conflate()

    /** One reading, or null when unavailable / timed out. */
    suspend fun currentPressureHpa(timeoutMs: Long = 2_000L): Float? {
        if (!isAvailable) return null
        return withTimeoutOrNull(timeoutMs) {
            try { pressureUpdates().first() } catch (e: NoSuchElementException) { null }
        }
    }
}
