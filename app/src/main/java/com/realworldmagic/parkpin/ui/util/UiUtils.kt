package com.realworldmagic.parkpin.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider
import com.realworldmagic.parkpin.ParkPinApp
import com.realworldmagic.parkpin.di.AppContainer
import com.realworldmagic.parkpin.domain.ParkingSpot
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Resolve the manual-DI container from ViewModel CreationExtras. */
fun CreationExtras.parkPinContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ParkPinApp).container

fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

fun Context.vibrate(pattern: LongArray = longArrayOf(0, 120, 80, 120, 80, 300)) {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    if (vibrator?.hasVibrator() != true) return
    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
}

/** Keeps the screen on while this composable is in composition and [enabled] is true. */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = context.findActivity()?.window
        if (enabled) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (enabled) window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

fun formatTime(ms: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

fun formatDateTime(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

/** "3 min ago", "2 h 5 min ago", or a date if older than a day. */
fun formatAgo(ms: Long, now: Long = System.currentTimeMillis()): String {
    val diffMin = ((now - ms) / 60_000L).coerceAtLeast(0)
    return when {
        diffMin < 1 -> "just now"
        diffMin < 60 -> "$diffMin min ago"
        diffMin < 24 * 60 -> "${diffMin / 60} h ${diffMin % 60} min ago"
        else -> formatDateTime(ms)
    }
}

/** "1:05:09" / "12:03" countdown text. */
fun formatCountdown(remainingMs: Long): String {
    val totalSec = (remainingMs / 1000L).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun clockPhrase(clock: Int): String = "$clock o'clock"

/** Builds the share text for a spot: geo: URI + OSM link. */
fun buildShareText(spot: ParkingSpot): String {
    val lat = String.format(Locale.US, "%.6f", spot.point.lat)
    val lng = String.format(Locale.US, "%.6f", spot.point.lng)
    return buildString {
        append("My car is parked here")
        if (spot.note.isNotBlank()) append(" (").append(spot.note).append(")")
        spot.floorLabel?.takeIf { it.isNotBlank() }?.let { append(", floor ").append(it) }
        append(":\n")
        append("geo:$lat,$lng?q=$lat,$lng(My car)\n")
        append("https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=19/$lat/$lng")
    }
}

fun shareSpot(context: Context, spot: ParkingSpot) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Where I parked")
        putExtra(Intent.EXTRA_TEXT, buildShareText(spot))
    }
    context.startActivity(Intent.createChooser(send, "Share parking spot"))
}
