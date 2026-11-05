package com.realworldmagic.parkpin.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Schedules a "meter expires soon" notification [leadMinutes] before the meter runs out. */
class MeterReminderScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    /**
     * Replaces any reminder for [spotId]. If the lead time has already passed the reminder fires
     * immediately; if the meter has already expired nothing is scheduled.
     */
    fun schedule(
        spotId: Long,
        meterExpiresAtMs: Long,
        leadMinutes: Int,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (meterExpiresAtMs <= nowMs) {
            cancel(spotId)
            return
        }
        val fireAt = meterExpiresAtMs - TimeUnit.MINUTES.toMillis(leadMinutes.coerceAtLeast(0).toLong())
        val delayMs = (fireAt - nowMs).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<MeterReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    MeterReminderWorker.KEY_SPOT_ID to spotId,
                    MeterReminderWorker.KEY_EXPIRES_AT_MS to meterExpiresAtMs,
                ),
            )
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(uniqueName(spotId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(spotId: Long) {
        workManager.cancelUniqueWork(uniqueName(spotId))
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG)
    }

    companion object {
        const val TAG = "meter_reminder"
        fun uniqueName(spotId: Long) = "meter_reminder_$spotId"
    }
}
