package com.realworldmagic.parkpin.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager fallback for meter reminders: used when the reminder is already due (lead time
 * passed) or no alarm could be set. Timed reminders normally go through [MeterAlarmReceiver].
 */
class MeterReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val spotId = inputData.getLong(KEY_SPOT_ID, -1L)
        val expiresAt = inputData.getLong(KEY_EXPIRES_AT_MS, -1L)
        MeterReminderNotifier.notifyIfStillValid(applicationContext, spotId, expiresAt)
        return Result.success()
    }

    companion object {
        const val KEY_SPOT_ID = "spotId"
        const val KEY_EXPIRES_AT_MS = "expiresAtMs"
        /** Intent extra read by MainActivity (Long). */
        const val EXTRA_SPOT_ID = MeterReminderNotifier.EXTRA_SPOT_ID
    }
}
