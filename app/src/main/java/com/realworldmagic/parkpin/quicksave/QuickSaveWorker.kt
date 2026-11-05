package com.realworldmagic.parkpin.quicksave

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters

/**
 * Fallback for auto-save when a background FGS start is refused (API 31+). Enqueued expedited, so
 * on API 31+ it runs as an expedited job without a foreground service. Only enqueued when
 * ACCESS_BACKGROUND_LOCATION is held, which a background job needs to read location.
 */
class QuickSaveWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val outcome = try {
            QuickSaveCore.captureAndSave(applicationContext)
        } catch (e: SecurityException) {
            QuickSaveOutcome.NoPermission
        }
        if (outcome == QuickSaveOutcome.NoFix) {
            // Background fix failed: let the user retry with one tap rather than a dead-end error.
            QuickSaveNotifications.postTapToSave(applicationContext, carDisconnected = true)
        } else {
            QuickSaveCore.publish(applicationContext, outcome)
        }
        return Result.success()
    }

    /** Only used if WorkManager runs this expedited request as a foreground service (API < 31). */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val n = QuickSaveNotifications.progress(applicationContext)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(QuickSaveNotifications.ID_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            ForegroundInfo(QuickSaveNotifications.ID_PROGRESS, n)
        }
    }
}
