package com.realworldmagic.parkpin.quicksave

import android.content.Context
import com.realworldmagic.parkpin.ParkPinApp
import com.realworldmagic.parkpin.domain.ParkingSpot
import com.realworldmagic.parkpin.location.captureBestFix

/** Outcome of one quick-save attempt. */
sealed interface QuickSaveOutcome {
    data class Saved(val spotId: Long, val accuracyM: Float, val approximate: Boolean) : QuickSaveOutcome
    data object NoFix : QuickSaveOutcome
    data object NoPermission : QuickSaveOutcome
}

/** Shared capture-and-persist logic used by [QuickSaveService] and [QuickSaveWorker]. */
internal object QuickSaveCore {
    suspend fun captureAndSave(context: Context): QuickSaveOutcome {
        val app = context.applicationContext
        if (!QuickSavePermissions.hasLocation(app)) return QuickSaveOutcome.NoPermission
        val container = (app as ParkPinApp).container
        val result = captureBestFix(container.locationProvider)
        val fix = result.fix ?: return QuickSaveOutcome.NoFix
        val spot = ParkingSpot(
            point = fix.point,
            accuracyM = fix.accuracyM,
            altitudeM = fix.altitudeM,
            savedAtMs = System.currentTimeMillis(),
            isActive = true,
        )
        val id = container.parkingRepository.saveNewSpot(spot)
        return QuickSaveOutcome.Saved(id, fix.accuracyM, result.approximate)
    }

    /** Posts the matching result notification and refreshes widgets + tile. */
    suspend fun publish(context: Context, outcome: QuickSaveOutcome) {
        when (outcome) {
            is QuickSaveOutcome.Saved ->
                QuickSaveNotifications.postSaved(context, outcome.spotId, outcome.accuracyM, outcome.approximate)
            QuickSaveOutcome.NoFix -> QuickSaveNotifications.postFailed(context)
            QuickSaveOutcome.NoPermission -> QuickSaveNotifications.postPermissionNeeded(context)
        }
        if (outcome is QuickSaveOutcome.Saved) {
            QuickSaveNotifications.cancel(context, QuickSaveNotifications.ID_PROMPT)
            WidgetUpdater.refreshAllNow(context)
        }
    }
}
