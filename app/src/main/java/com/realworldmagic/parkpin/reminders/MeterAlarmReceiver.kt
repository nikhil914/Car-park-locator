package com.realworldmagic.parkpin.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.realworldmagic.parkpin.ParkPinApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives the meter-reminder alarm set by [MeterReminderScheduler] and posts the notification. */
class MeterAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MeterReminderScheduler.ACTION_METER_ALARM) return
        val spotId = intent.getLongExtra(MeterReminderScheduler.EXTRA_SPOT_ID, -1L)
        val expiresAt = intent.getLongExtra(MeterReminderScheduler.EXTRA_EXPIRES_AT_MS, -1L)
        val app = context.applicationContext
        (app as? ParkPinApp)?.container?.meterReminderScheduler?.onAlarmFired(spotId)
        // Room lookup is off the main thread; goAsync keeps the process alive until it is done.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                MeterReminderNotifier.notifyIfStillValid(app, spotId, expiresAt)
            } finally {
                pending?.finish()
            }
        }
    }
}
