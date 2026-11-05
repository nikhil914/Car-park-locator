package com.realworldmagic.parkpin.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import com.realworldmagic.parkpin.domain.AnnouncementThrottle
import kotlinx.coroutines.delay

/**
 * True when the user turned animations off (Developer options / Accessibility "Remove animations",
 * i.e. animator duration scale = 0). Pulsing / looping animations should then show a static frame.
 */
fun Context.isReduceMotionEnabled(): Boolean = try {
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (e: Exception) {
    false
}

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isReduceMotionEnabled() }
}

/** True when a touch-exploration screen reader (TalkBack) is on. */
fun Context.isScreenReaderOn(): Boolean {
    val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    return am.isEnabled && am.isTouchExplorationEnabled
}

@Composable
fun rememberScreenReaderOn(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isScreenReaderOn() }
}

/**
 * Returns [text] throttled for TalkBack live regions: changes are published at most once per
 * [minIntervalMs] (latest text wins), [urgent] changes immediately. Use the result as the
 * `contentDescription` of a node with `liveRegion = LiveRegionMode.Polite`.
 */
@Composable
fun rememberThrottledAnnouncement(text: String, urgent: Boolean = false, minIntervalMs: Long = 8_000L): String {
    val throttle = remember(minIntervalMs) { AnnouncementThrottle(minIntervalMs = minIntervalMs) }
    var published by remember { mutableStateOf(text) }
    LaunchedEffect(text, urgent) {
        val wait = throttle.delayBeforeAnnouncing(text, System.currentTimeMillis(), urgent) ?: return@LaunchedEffect
        if (wait > 0) delay(wait)
        throttle.markAnnounced(text, System.currentTimeMillis())
        published = text
    }
    return published
}

/** Status colours with enough contrast (≥ 3:1 for icons) on the current surface. */
object StatusColors {
    @Composable
    fun good(): Color = if (isLightSurface()) Color(0xFF1B5E20) else Color(0xFF81C784)

    @Composable
    fun fair(): Color = if (isLightSurface()) Color(0xFF8A5300) else Color(0xFFFFCA28)

    @Composable
    fun poor(): Color = if (isLightSurface()) Color(0xFFB71C1C) else Color(0xFFFF8A80)

    @Composable
    private fun isLightSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() > 0.5f
}

/** Vibration helpers for guidance haptics. Silently no-ops on devices without a vibrator. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.applicationContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val usable: Boolean get() = vibrator?.hasVibrator() == true

    /** Short crisp "tick" (e.g. the car is centred in the AR view). */
    fun tick() {
        val v = vibrator ?: return
        if (!usable) return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        } else {
            VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        runCatching { v.vibrate(effect) }
    }

    /** One sonar ping of [durationMs] at [amplitude] 1..255 (ignored when amplitude control is missing). */
    fun pulse(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!usable || durationMs <= 0) return
        val amp = if (v.hasAmplitudeControl()) amplitude.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
        runCatching { v.vibrate(VibrationEffect.createOneShot(durationMs, amp)) }
    }

    fun cancel() {
        runCatching { vibrator?.cancel() }
    }
}
