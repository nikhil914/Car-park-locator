package com.realworldmagic.parkpin.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.realworldmagic.parkpin.ParkPinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Alarms are cleared by a reboot and can drift on clock / time-zone changes; when the user grants
 * the exact-alarm permission a windowed alarm can be upgraded to an exact one. In each case the
 * active spot's meter reminder (read from Room) is scheduled again.
 */
class MeterReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val app = context.applicationContext as? ParkPinApp ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                rescheduleActive(app)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Best effort: a failed reschedule must not crash the boot broadcast.
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )

        /** Schedules the reminder for the active spot's meter, if it is still running. */
        suspend fun rescheduleActive(app: ParkPinApp, nowMs: Long = System.currentTimeMillis()) {
            val container = app.container
            val spot = container.parkingRepository.observeActiveSpot().first() ?: return
            val expires = spot.meterExpiresAtMs ?: return
            if (expires <= nowMs) return
            val lead = container.settingsRepository.settings.first().meterReminderLeadMinutes
            container.meterReminderScheduler.schedule(spot.id, expires, lead, nowMs)
        }
    }
}
