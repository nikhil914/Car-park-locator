package com.realworldmagic.parkpin.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.realworldmagic.parkpin.R

object NotificationChannels {
    const val METER_REMINDERS = "meter_reminders"

    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            METER_REMINDERS,
            context.getString(R.string.channel_meter_reminders_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_meter_reminders_description)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}
