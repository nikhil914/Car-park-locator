package com.realworldmagic.parkpin.quicksave

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.realworldmagic.parkpin.MainActivity
import com.realworldmagic.parkpin.R
import kotlin.math.roundToInt

/** Notification channel + builders for the quick-save feature. Channel is created lazily here. */
object QuickSaveNotifications {
    const val CHANNEL_ID = "quick_save"
    const val ID_PROGRESS = 4201
    const val ID_RESULT = 4202
    const val ID_PROMPT = 4203

    private const val RC_OPEN_APP = 4300
    private const val RC_GUIDE_BASE = 4400
    private const val RC_SAVE_FROM_PROMPT = 4301

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.quicksave_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.quicksave_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    fun progress(context: Context): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.quicksave_ic_car)
            .setContentTitle(context.getString(R.string.quicksave_progress_title))
            .setContentText(context.getString(R.string.quicksave_progress_text))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun openAppPendingIntent(context: Context, spotId: Long? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (spotId != null) putExtra(MainActivity.EXTRA_SPOT_ID, spotId)
        }
        val rc = if (spotId != null) RC_GUIDE_BASE + (spotId % 1000).toInt() else RC_OPEN_APP
        return PendingIntent.getActivity(
            context, rc, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun postSaved(context: Context, spotId: Long, accuracyM: Float, approximate: Boolean) {
        val acc = accuracyM.roundToInt().coerceAtLeast(1)
        val title = context.getString(
            if (approximate) R.string.quicksave_saved_title_approx else R.string.quicksave_saved_title, acc,
        )
        val guide = openAppPendingIntent(context, spotId)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.quicksave_ic_car)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.quicksave_saved_text))
            .setContentIntent(guide)
            .addAction(0, context.getString(R.string.quicksave_action_guide), guide)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        notify(context, ID_RESULT, n)
    }

    fun postFailed(context: Context) {
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.quicksave_ic_car)
            .setContentTitle(context.getString(R.string.quicksave_failed_title))
            .setContentText(context.getString(R.string.quicksave_failed_text))
            .setContentIntent(openAppPendingIntent(context))
            .addAction(0, context.getString(R.string.quicksave_action_save), saveFromNotificationPendingIntent(context))
            .setAutoCancel(true)
            .build()
        notify(context, ID_RESULT, n)
    }

    /** Location permission was never granted: the user must open the app to grant it. */
    fun postPermissionNeeded(context: Context) {
        val open = openAppPendingIntent(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.quicksave_ic_car)
            .setContentTitle(context.getString(R.string.quicksave_perm_title))
            .setContentText(context.getString(R.string.quicksave_perm_text))
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.quicksave_action_open), open)
            .setAutoCancel(true)
            .build()
        notify(context, ID_PROMPT, n)
    }

    /**
     * "Tap to save" prompt. Used when we may not access location from the background
     * (no ACCESS_BACKGROUND_LOCATION). Tapping it starts [QuickSaveService] via
     * PendingIntent.getForegroundService, and a notification interaction is exempt from both the
     * background-FGS-start restriction and the while-in-use location restriction.
     */
    fun postTapToSave(context: Context, carDisconnected: Boolean) {
        val save = saveFromNotificationPendingIntent(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.quicksave_ic_car)
            .setContentTitle(
                context.getString(if (carDisconnected) R.string.quicksave_car_disc_title else R.string.quicksave_tap_title),
            )
            .setContentText(
                context.getString(if (carDisconnected) R.string.quicksave_car_disc_text else R.string.quicksave_tap_text),
            )
            .setContentIntent(save)
            .addAction(0, context.getString(R.string.quicksave_action_save), save)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(30 * 60_000L)
            .build()
        notify(context, ID_PROMPT, n)
    }

    private fun saveFromNotificationPendingIntent(context: Context): PendingIntent =
        PendingIntent.getForegroundService(
            context,
            RC_SAVE_FROM_PROMPT,
            QuickSaveService.intent(context, QuickSaveService.SOURCE_NOTIFICATION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    private fun notify(context: Context, id: Int, n: Notification) {
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        // API 33+: POST_NOTIFICATIONS may be denied. Then the result is silently dropped (widget/tile still update).
        if (!nm.areNotificationsEnabled()) return
        try {
            nm.notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}
