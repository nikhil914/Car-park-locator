package com.realworldmagic.parkpin.sensors

import android.content.Context
import android.util.Log
import com.google.android.gms.location.DeviceOrientation
import com.google.android.gms.location.DeviceOrientationListener
import com.google.android.gms.location.DeviceOrientationRequest
import com.google.android.gms.location.FusedOrientationProviderClient
import com.google.android.gms.location.LocationServices
import com.realworldmagic.parkpin.domain.GeoMath
import com.realworldmagic.parkpin.domain.GeoPoint
import com.realworldmagic.parkpin.domain.Heading
import com.realworldmagic.parkpin.domain.HeadingSmoother
import com.realworldmagic.parkpin.domain.OrientationMath
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Heading from Google's FusedOrientationProvider (Play Services, no permissions): fused
 * accel/gyro/mag attitude plus a true-north heading and its error estimate in degrees.
 *
 * - Flat mode: azimuth = `headingDegrees`; pitch/roll as `SensorManager.getOrientation` would give.
 * - Camera mode: camera azimuth / elevation / roll come from the attitude quaternion via
 *   [OrientationMath.cameraPose], so any hold (upright, landscape, rolled) works. The attitude's
 *   north reference is aligned to `headingDegrees` with an offset learnt while the top of the
 *   phone points horizontally enough for both to be comparable.
 * - `headingErrorDegrees` maps to [Heading.accuracy] / [Heading.accuracyDeg].
 *
 * If the provider fails or sends nothing within [FIRST_UPDATE_TIMEOUT_MS], the flow switches to
 * [fallback] (the rotation-vector [CompassSensor]).
 */
class FusedHeadingProvider(
    context: Context,
    private val fallback: HeadingProvider,
) : HeadingProvider {
    private val client: FusedOrientationProviderClient =
        LocationServices.getFusedOrientationProviderClient(context.applicationContext)

    override val isAvailable: Boolean get() = fallback.isAvailable

    override fun setLastKnownLocation(point: GeoPoint, altitudeM: Double?, timeMs: Long) {
        // FOP handles declination itself; the fallback still needs it.
        fallback.setLastKnownLocation(point, altitudeM, timeMs)
    }

    override fun headingUpdates(cameraMode: Boolean): Flow<Heading> =
        fusedUpdates(cameraMode).catch { e ->
            Log.w(TAG, "Fused orientation unavailable, using sensor compass", e)
            emitAll(fallback.headingUpdates(cameraMode))
        }

    private fun fusedUpdates(cameraMode: Boolean): Flow<Heading> = callbackFlow {
        val executor = Executors.newSingleThreadExecutor()
        val smoother = HeadingSmoother(alpha = 0.25)
        val received = AtomicBoolean(false)
        var northOffsetDeg: Double? = null

        val listener = DeviceOrientationListener { o: DeviceOrientation ->
            received.set(true)
            val r = OrientationMath.rotationMatrixFromQuaternion(o.attitude)
            val heading = o.headingDegrees.toDouble()
            val errorDeg = o.headingErrorDegrees
            val azimuth: Double
            val pitch: Double
            val roll: Double
            if (cameraMode) {
                // Learn attitude-north -> true-north offset while the phone top is horizontal enough.
                if (hypot(r[1], r[4]) > 0.5) {
                    OrientationMath.topAzimuthDeg(r)?.let { top ->
                        val measured = GeoMath.relativeBearingDeg(heading, top)
                        val prev = northOffsetDeg
                        northOffsetDeg = if (prev == null) measured
                        else prev + 0.1 * GeoMath.relativeBearingDeg(measured, prev)
                    }
                }
                val pose = OrientationMath.cameraPose(r)
                azimuth = pose.azimuthDeg + (northOffsetDeg ?: 0.0)
                pitch = pose.elevationDeg
                roll = pose.rollDeg
            } else {
                azimuth = heading
                pitch = Math.toDegrees(asin((-r[7]).coerceIn(-1.0, 1.0)))
                roll = Math.toDegrees(atan2(-r[6], r[8]))
            }
            if (!azimuth.isFinite()) return@DeviceOrientationListener
            val smoothed = smoother.update(GeoMath.normalizeDeg(azimuth))
            trySend(
                Heading(
                    azimuthDeg = smoothed.toFloat(),
                    pitchDeg = pitch.toFloat(),
                    rollDeg = roll.toFloat(),
                    accuracy = Heading.accuracyFromErrorDeg(errorDeg),
                    accuracyDeg = errorDeg.takeIf { it.isFinite() && it >= 0f },
                ),
            )
        }

        val request = DeviceOrientationRequest.Builder(DeviceOrientationRequest.OUTPUT_PERIOD_DEFAULT).build()
        try {
            client.requestOrientationUpdates(request, executor, listener)
                .addOnFailureListener { close(it) }
        } catch (e: Exception) {
            executor.shutdown()
            close(e)
            return@callbackFlow
        }
        launch {
            delay(FIRST_UPDATE_TIMEOUT_MS)
            if (!received.get()) close(TimeoutException("No fused orientation within ${FIRST_UPDATE_TIMEOUT_MS} ms"))
        }
        awaitClose {
            try {
                client.removeOrientationUpdates(listener)
            } catch (e: Exception) {
                Log.w(TAG, "removeOrientationUpdates failed", e)
            }
            executor.shutdown()
        }
    }.conflate()

    private companion object {
        const val TAG = "FusedHeading"
        const val FIRST_UPDATE_TIMEOUT_MS = 3_000L
    }
}
