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
import com.realworldmagic.parkpin.ParkPinApp
import com.realworldmagic.parkpin.R
import java.util.concurrent.TimeUnit

/**
 * Posts the "meter expires soon" notification. Shared by the alarm path
 * ([MeterAlarmReceiver]) and the WorkManager fallback ([MeterReminderWorker]).
 */
object MeterReminderNotifier {
    /** Intent extra read by MainActivity (Long). */
    const val EXTRA_SPOT_ID = "spotId"
    private const val MAIN_ACTIVITY = "com.realworldmagic.parkpin.MainActivity"
    private val MINUTE_MS = TimeUnit.MINUTES.toMillis(1)

    /**
     * Posts the reminder unless the spot was cleared, deleted or its meter changed since
     * scheduling, or notifications are not allowed. Returns true when a notification was posted.
     */
    suspend fun notifyIfStillValid(ctx: Context, spotId: Long, expiresAt: Long): Boolean {
        if (spotId <= 0L || expiresAt <= 0L) return false
        val repository = (ctx.applicationContext as? ParkPinApp)?.container?.parkingRepository
        val spot = repository?.getSpot(spotId)
        // A null spot with a repository available means it was deleted: never remind about it.
        if (repository != null && spot == null) return false
        if (spot != null && (!spot.isActive || spot.meterExpiresAtMs != expiresAt)) return false

        if (!canPostNotifications(ctx)) return false

        // Round up so "9 min 59 s left" reads as 10 minutes, matching the configured lead time.
        val remainingMs = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val minutesLeft = ((remainingMs + MINUTE_MS - 1) / MINUTE_MS).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val text = if (minutesLeft > 0) {
            ctx.resources.getQuantityString(R.plurals.meter_reminder_text, minutesLeft, minutesLeft)
        } else {
            ctx.getString(R.string.meter_reminder_text_expired)
        }
        val detail = spot?.let { listOfNotNull(it.floorLabel, it.note.takeIf { n -> n.isNotBlank() }).joinToString(" · ") }
            ?.takeIf { it.isNotBlank() }

        val notification = NotificationCompat.Builder(ctx, NotificationChannels.METER_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_parkpin)
            .setContentTitle(ctx.getString(R.string.meter_reminder_title))
            .setContentText(if (detail != null) "$text — $detail" else text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openSpotPendingIntent(ctx, spotId))
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(ctx).notify(notificationId(spotId), notification)
            true
        } catch (e: SecurityException) {
            // Permission revoked between check and post.
            false
        }
    }

    /** Opens the app on [spotId]; also the alarm clock's "show" intent (tap on the status-bar alarm). */
    fun openSpotPendingIntent(ctx: Context, spotId: Long): PendingIntent {
        val intent = Intent().apply {
            setClassName(ctx, MAIN_ACTIVITY)
            putExtra(EXTRA_SPOT_ID, spotId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            ctx,
            spotId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canPostNotifications(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    private fun notificationId(spotId: Long) = 10_000 + (spotId % 100_000).toInt()
}
