package com.realworldmagic.parkpin.quicksave

import android.content.Context
import com.realworldmagic.parkpin.ParkPinApp
import com.realworldmagic.parkpin.domain.ParkingSpot
import com.realworldmagic.parkpin.location.captureBestFix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Outcome of one quick-save attempt. */
sealed interface QuickSaveOutcome {
    data class Saved(
        val spotId: Long,
        val accuracyM: Float,
        val approximate: Boolean,
        /** The saved fix came from a mock location provider. */
        val isMock: Boolean = false,
    ) : QuickSaveOutcome
    data object NoFix : QuickSaveOutcome
    data object NoPermission : QuickSaveOutcome
}

/** Shared capture-and-persist logic used by [QuickSaveService] and [QuickSaveWorker]. */
internal object QuickSaveCore {
    suspend fun captureAndSave(context: Context): QuickSaveOutcome = coroutineScope {
        val app = context.applicationContext
        if (!QuickSavePermissions.hasLocation(app)) return@coroutineScope QuickSaveOutcome.NoPermission
        val container = (app as ParkPinApp).container
        // Barometer reading in parallel with the GPS fix (null without a barometer), like the
        // in-app save, so quick-saved spots also get the "car is ~N floors below you" hint.
        val pressure = async {
            try {
                container.barometerSensor.currentPressureHpa()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        val result = captureBestFix(container.locationProvider)
        val fix = result.fix
        if (fix == null) {
            pressure.cancel()
            return@coroutineScope QuickSaveOutcome.NoFix
        }
        val spot = ParkingSpot(
            point = fix.point,
            accuracyM = fix.accuracyM,
            altitudeM = fix.altitudeM,
            savedAtMs = System.currentTimeMillis(),
            isActive = true,
            pressureHpa = pressure.await(),
        )
        val id = container.parkingRepository.saveNewSpot(spot)
        QuickSaveOutcome.Saved(id, fix.accuracyM, result.approximate, result.isMock)
    }

    /** Posts the matching result notification and refreshes widgets + tile. */
    suspend fun publish(context: Context, outcome: QuickSaveOutcome) {
        when (outcome) {
            is QuickSaveOutcome.Saved ->
                QuickSaveNotifications.postSaved(
                    context, outcome.spotId, outcome.accuracyM, outcome.approximate, outcome.isMock,
                )
            QuickSaveOutcome.NoFix -> QuickSaveNotifications.postFailed(context)
            QuickSaveOutcome.NoPermission -> QuickSaveNotifications.postPermissionNeeded(context)
        }
        if (outcome is QuickSaveOutcome.Saved) {
            QuickSaveNotifications.cancel(context, QuickSaveNotifications.ID_PROMPT)
            WidgetUpdater.refreshAllNow(context)
        }
    }
}
