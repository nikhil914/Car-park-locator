package com.realworldmagic.parkpin.quicksave

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

/**
 * Decides how to auto-save when the chosen car Bluetooth device disconnects. Shared by
 * [BluetoothCarDisconnectReceiver] (ACL broadcast, all API levels) and [CarCompanionService]
 * (CompanionDeviceManager presence, API 33+). Both may fire for one event; [QuickSavePrefs] debounces.
 *
 * Strategy (most to least automatic):
 *  1. Background location ("Allow all the time") granted, or API < 30 (no while-in-use restriction):
 *     start [QuickSaveService]. Allowed from the background on API < 31, or on 31+ when the app is
 *     exempt (CDM association + REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND,
 *     battery-optimisation exemption, ...).
 *  2. Same, but the FGS start was refused (ForegroundServiceStartNotAllowedException): enqueue an
 *     expedited [QuickSaveWorker], which runs as a JobScheduler expedited job on 31+ and can read
 *     location in the background because ACCESS_BACKGROUND_LOCATION is held.
 *  3. Only while-in-use location: post a "Car disconnected · tap to save" notification. Tapping it
 *     starts the FGS via a notification PendingIntent, which is WIU-exempt.
 *  4. No location permission at all: post "open the app" notification.
 */
object CarDisconnectHandler {
    private const val TAG = "CarDisconnect"
    private const val WORK_NAME = "quicksave_auto"

    fun onCarDisconnected(context: Context, trigger: String) {
        val app = context.applicationContext
        if (!QuickSavePrefs.isAutoSaveEnabled(app)) return
        if (!QuickSavePrefs.tryClaimAutoTrigger(app)) {
            Log.d(TAG, "Debounced duplicate trigger from $trigger")
            return
        }
        QuickSaveNotifications.ensureChannel(app)
        if (!QuickSavePermissions.hasLocation(app)) {
            QuickSaveNotifications.postPermissionNeeded(app)
            return
        }
        val canUseLocationInBackground =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R || QuickSavePermissions.hasBackgroundLocation(app)
        if (!canUseLocationInBackground) {
            QuickSaveNotifications.postTapToSave(app, carDisconnected = true)
            return
        }
        if (QuickSaveService.start(app, QuickSaveService.SOURCE_CAR_BLUETOOTH)) return
        enqueueWorker(app)
    }

    private fun enqueueWorker(context: Context) {
        try {
            val request = OneTimeWorkRequestBuilder<QuickSaveWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        } catch (e: Exception) {
            Log.w(TAG, "Could not enqueue worker; asking user instead", e)
            QuickSaveNotifications.postTapToSave(context, carDisconnected = true)
        }
    }
}
