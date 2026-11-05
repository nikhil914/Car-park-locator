package com.realworldmagic.parkpin.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.realworldmagic.parkpin.ParkPinApp
import com.realworldmagic.parkpin.R
import java.util.concurrent.TimeUnit

class MeterReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val spotId = inputData.getLong(KEY_SPOT_ID, -1L)
        val expiresAt = inputData.getLong(KEY_EXPIRES_AT_MS, -1L)
        if (spotId <= 0L || expiresAt <= 0L) return Result.success()

        val ctx = applicationContext
        // Skip if the spot was cleared, deleted or the meter changed since scheduling.
        val spot = (ctx as? ParkPinApp)?.container?.parkingRepository?.getSpot(spotId)
        if (spot != null && (!spot.isActive || spot.meterExpiresAtMs != expiresAt)) return Result.success()

        if (!canPostNotifications(ctx)) return Result.success()

        val minutesLeft = TimeUnit.MILLISECONDS.toMinutes(expiresAt - System.currentTimeMillis()).coerceAtLeast(0L).toInt()
        val text = if (minutesLeft > 0) {
            ctx.resources.getQuantityString(R.plurals.meter_reminder_text, minutesLeft, minutesLeft)
        } else {
            ctx.getString(R.string.meter_reminder_text_expired)
        }
        val detail = spot?.let { listOfNotNull(it.floorLabel, it.note.takeIf { n -> n.isNotBlank() }).joinToString(" · ") }
            ?.takeIf { it.isNotBlank() }

        val intent = Intent().apply {
            setClassName(ctx, MAIN_ACTIVITY)
            putExtra(EXTRA_SPOT_ID, spotId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            ctx,
            spotId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, NotificationChannels.METER_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_parkpin)
            .setContentTitle(ctx.getString(R.string.meter_reminder_title))
            .setContentText(if (detail != null) "$text — $detail" else text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(notificationId(spotId), notification)
        } catch (e: SecurityException) {
            // Permission revoked between check and post.
        }
        return Result.success()
    }

    private fun canPostNotifications(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    companion object {
        const val KEY_SPOT_ID = "spotId"
        const val KEY_EXPIRES_AT_MS = "expiresAtMs"
        /** Intent extra read by MainActivity (Long). */
        const val EXTRA_SPOT_ID = "spotId"
        private const val MAIN_ACTIVITY = "com.realworldmagic.parkpin.MainActivity"
        private fun notificationId(spotId: Long) = 10_000 + (spotId % 100_000).toInt()
    }
}
