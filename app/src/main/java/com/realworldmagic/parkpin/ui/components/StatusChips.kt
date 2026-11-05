package com.realworldmagic.parkpin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.realworldmagic.parkpin.domain.GeoMath
import com.realworldmagic.parkpin.domain.SpokenText
import com.realworldmagic.parkpin.ui.util.formatCountdown
import kotlinx.coroutines.delay

/** Formats a distance using the shared GeoMath formatter; TalkBack hears units spelled out. */
@Composable
fun DistanceText(
    meters: Double?,
    useMetric: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val text = meters?.let { GeoMath.formatDistance(it, useMetric) } ?: "--"
    val spoken = meters?.let { SpokenText.distance(it, useMetric) } ?: "Distance unknown"
    Text(
        text,
        modifier = modifier.semantics { contentDescription = spoken },
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun accuracyColor(accuracyM: Float): Color = when {
    accuracyM <= 10f -> StatusColors.good()
    accuracyM <= 30f -> StatusColors.fair()
    else -> StatusColors.poor()
}

/**
 * Non-interactive status pill (looks like a chip but is not a fake button for TalkBack).
 * Text wraps instead of clipping at large font scales.
 */
@Composable
fun StatusPill(
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    spokenLabel: String = label,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.heightIn(min = 32.dp).clearAndSetSemantics { contentDescription = spokenLabel },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Small "±8 m" pill coloured by quality. */
@Composable
fun AccuracyBadge(accuracyM: Float?, useMetric: Boolean, modifier: Modifier = Modifier) {
    val label = if (accuracyM == null) "Accuracy: --"
    else "±" + GeoMath.formatDistance(accuracyM.toDouble(), useMetric)
    val spoken = if (accuracyM == null) "GPS accuracy unknown"
    else "GPS accuracy within " + SpokenText.distance(accuracyM.toDouble(), useMetric)
    val tint = accuracyM?.let { accuracyColor(it) } ?: MaterialTheme.colorScheme.outline
    StatusPill(label, Icons.Filled.GpsFixed, tint, modifier, spokenLabel = spoken)
}

/** GPS status: searching / weak / good. */
@Composable
fun GpsStatusChip(accuracyM: Float?, modifier: Modifier = Modifier) {
    val icon: ImageVector
    val label: String
    val tint: Color
    when {
        accuracyM == null -> { icon = Icons.Filled.GpsOff; label = "Searching GPS…"; tint = MaterialTheme.colorScheme.outline }
        accuracyM > 30f -> { icon = Icons.Filled.GpsNotFixed; label = "GPS weak"; tint = accuracyColor(accuracyM) }
        accuracyM > 10f -> { icon = Icons.Filled.GpsFixed; label = "GPS fair"; tint = accuracyColor(accuracyM) }
        else -> { icon = Icons.Filled.GpsFixed; label = "GPS good"; tint = accuracyColor(accuracyM) }
    }
    StatusPill(label, icon, tint, modifier, spokenLabel = label.removeSuffix("…"))
}

/**
 * Parking-meter countdown that ticks every second. With [onClick] it is a button (48 dp touch
 * target via Material chip); without, a plain status pill.
 */
@Composable
fun MeterCountdownChip(expiresAtMs: Long, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val remaining = expiresAtMs - now
    val expired = remaining <= 0
    val label = if (expired) "Meter expired" else "Meter " + formatCountdown(remaining)
    // Minute resolution for TalkBack so the description is not re-read every second.
    val spoken = if (expired) "Parking meter expired" else "Parking meter: " + spokenRemaining(remaining) + " left"
    val tint = when {
        expired -> MaterialTheme.colorScheme.error
        remaining < 10 * 60_000L -> StatusColors.fair()
        else -> MaterialTheme.colorScheme.primary
    }
    val icon = if (expired) Icons.Filled.TimerOff else Icons.Filled.Timer
    if (onClick == null) {
        StatusPill(label, icon, tint, modifier, spokenLabel = spoken)
    } else {
        AssistChip(
            onClick = onClick,
            label = { Text(label, maxLines = 1) },
            leadingIcon = { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) },
            modifier = modifier.clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                // clearAndSetSemantics drops the chip's own click action; re-add it.
                this.onClick(label = "edit meter") { onClick(); true }
            },
        )
    }
}

private fun spokenRemaining(ms: Long): String {
    val totalMin = ((ms + 59_999L) / 60_000L).coerceAtLeast(1)
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h == 0L -> "$m minute" + if (m == 1L) "" else "s"
        m == 0L -> "$h hour" + if (h == 1L) "" else "s"
        else -> "$h hour" + (if (h == 1L) "" else "s") + " $m minute" + if (m == 1L) "" else "s"
    }
}
