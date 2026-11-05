package com.realworldmagic.parkpin.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Schedules a "meter expires soon" notification [leadMinutes] before the meter runs out.
 *
 * Delayed WorkManager work can be deferred by Doze for a long time (docs/RESEARCH.md §9.2), so:
 * - **Exact alarms allowed** (API < 31, or `SCHEDULE_EXACT_ALARM` granted): `setAlarmClock`.
 *   It fires on time even in Doze and shows the alarm icon; tapping it opens the spot.
 * - **Exact alarms denied**: `setWindow` with a 10-minute window ending at the reminder time.
 * - **No alarm possible** (no AlarmManager / SecurityException) or **already due**: WorkManager.
 *
 * [MeterAlarmReceiver] posts the notification when the alarm fires. Alarms do not survive a
 * reboot; [MeterReminderRescheduleReceiver] reschedules the active spot's reminder after boot,
 * clock / time-zone changes and when the exact-alarm permission is granted.
 */
class MeterReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val workManager by lazy { WorkManager.getInstance(appContext) }
    private val alarmManager: AlarmManager? = appContext.getSystemService(AlarmManager::class.java)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** How a reminder was scheduled. */
    enum class Mode { NONE, EXACT_ALARM, WINDOWED_ALARM, WORK_MANAGER }

    /** True when `setAlarmClock` may be used (always below API 31). */
    fun canScheduleExactAlarms(): Boolean {
        val am = alarmManager ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    /**
     * Replaces any reminder for [spotId]. If the lead time has already passed the reminder fires
     * immediately; if the meter has already expired nothing is scheduled.
     */
    fun schedule(
        spotId: Long,
        meterExpiresAtMs: Long,
        leadMinutes: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): Mode {
        cancel(spotId)
        if (meterExpiresAtMs <= nowMs) return Mode.NONE
        val fireAt = meterExpiresAtMs - TimeUnit.MINUTES.toMillis(leadMinutes.coerceAtLeast(0).toLong())
        if (fireAt <= nowMs) {
            enqueueWork(spotId, meterExpiresAtMs, delayMs = 0L)
            return Mode.WORK_MANAGER
        }
        val am = alarmManager
        if (am != null) {
            val operation = alarmPendingIntent(spotId, meterExpiresAtMs)
            try {
                val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
                val mode = if (exactAllowed) {
                    val show = MeterReminderNotifier.openSpotPendingIntent(appContext, spotId)
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(fireAt, show), operation)
                    Mode.EXACT_ALARM
                } else {
                    // Inexact but alarm-based: ends at the reminder time, so it is never late by more
                    // than what the system adds to the window.
                    val start = maxOf(nowMs, fireAt - WINDOW_MS)
                    am.setWindow(AlarmManager.RTC_WAKEUP, start, (fireAt - start).coerceAtLeast(1L), operation)
                    Mode.WINDOWED_ALARM
                }
                rememberAlarm(spotId)
                return mode
            } catch (e: SecurityException) {
                // Exact-alarm permission revoked between the check and the call: fall through.
                operation.cancel()
            }
        }
        enqueueWork(spotId, meterExpiresAtMs, delayMs = fireAt - nowMs)
        return Mode.WORK_MANAGER
    }

    fun cancel(spotId: Long) {
        cancelAlarm(spotId)
        forgetAlarm(spotId)
        workManager.cancelUniqueWork(uniqueName(spotId))
    }

    fun cancelAll() {
        alarmIds().forEach { cancelAlarm(it) }
        prefs.edit().remove(KEY_ALARM_IDS).apply()
        workManager.cancelAllWorkByTag(TAG)
    }

    /** Called by [MeterAlarmReceiver] once an alarm has fired. */
    internal fun onAlarmFired(spotId: Long) = forgetAlarm(spotId)

    private fun enqueueWork(spotId: Long, meterExpiresAtMs: Long, delayMs: Long) {
        val request = OneTimeWorkRequestBuilder<MeterReminderWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
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

    private fun alarmIntent(spotId: Long): Intent =
        Intent(appContext, MeterAlarmReceiver::class.java)
            .setAction(ACTION_METER_ALARM)
            // Distinct data per spot so PendingIntents never collide, whatever the request code.
            .setData(Uri.parse("parkpin://meter-reminder/$spotId"))

    private fun alarmPendingIntent(spotId: Long, meterExpiresAtMs: Long): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            spotId.toInt(),
            alarmIntent(spotId)
                .putExtra(EXTRA_SPOT_ID, spotId)
                .putExtra(EXTRA_EXPIRES_AT_MS, meterExpiresAtMs),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun cancelAlarm(spotId: Long) {
        val existing = PendingIntent.getBroadcast(
            appContext,
            spotId.toInt(),
            alarmIntent(spotId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager?.cancel(existing)
        existing.cancel()
    }

    private fun alarmIds(): Set<Long> =
        prefs.getStringSet(KEY_ALARM_IDS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    private fun rememberAlarm(spotId: Long) {
        val ids = alarmIds() + spotId
        prefs.edit().putStringSet(KEY_ALARM_IDS, ids.map { it.toString() }.toSet()).apply()
    }

    private fun forgetAlarm(spotId: Long) {
        val ids = alarmIds()
        if (spotId !in ids) return
        prefs.edit().putStringSet(KEY_ALARM_IDS, (ids - spotId).map { it.toString() }.toSet()).apply()
    }

    companion object {
        const val TAG = "meter_reminder"
        const val ACTION_METER_ALARM = "com.realworldmagic.parkpin.action.METER_REMINDER"
        const val EXTRA_SPOT_ID = "spotId"
        const val EXTRA_EXPIRES_AT_MS = "expiresAtMs"
        /** Window used when exact alarms are denied (the platform minimum on API 31+). */
        val WINDOW_MS = TimeUnit.MINUTES.toMillis(10)
        private const val PREFS_NAME = "meter_reminders"
        private const val KEY_ALARM_IDS = "alarm_spot_ids"
        fun uniqueName(spotId: Long) = "meter_reminder_$spotId"
    }
}
