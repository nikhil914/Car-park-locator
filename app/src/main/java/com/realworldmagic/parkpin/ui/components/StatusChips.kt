package com.realworldmagic.parkpin.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.realworldmagic.parkpin.domain.GeoMath
import com.realworldmagic.parkpin.ui.theme.SuccessGreen
import com.realworldmagic.parkpin.ui.theme.WarningAmber
import com.realworldmagic.parkpin.ui.util.formatCountdown
import kotlinx.coroutines.delay

/** Formats a distance using the shared GeoMath formatter. */
@Composable
fun DistanceText(
    meters: Double?,
    useMetric: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val text = meters?.let { GeoMath.formatDistance(it, useMetric) } ?: "--"
    Text(text, modifier = modifier, style = style, color = color)
}

private fun accuracyColor(accuracyM: Float): Color = when {
    accuracyM <= 10f -> SuccessGreen
    accuracyM <= 30f -> WarningAmber
    else -> Color(0xFFE53935)
}

/** Small "±8 m" chip coloured by quality. */
@Composable
fun AccuracyBadge(accuracyM: Float?, useMetric: Boolean, modifier: Modifier = Modifier) {
    val label = if (accuracyM == null) "Accuracy: --"
    else "±" + GeoMath.formatDistance(accuracyM.toDouble(), useMetric)
    val tint = accuracyM?.let { accuracyColor(it) } ?: MaterialTheme.colorScheme.outline
    AssistChip(
        onClick = {},
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.GpsFixed, null, tint = tint, modifier = Modifier.size(18.dp)) },
        modifier = modifier.semantics { contentDescription = "GPS accuracy $label" },
    )
}

/** GPS status: searching / weak / good. */
@Composable
fun GpsStatusChip(accuracyM: Float?, modifier: Modifier = Modifier) {
    val (icon, label, tint) = when {
        accuracyM == null -> Triple(Icons.Filled.GpsOff, "Searching GPS…", MaterialTheme.colorScheme.outline)
        accuracyM > 30f -> Triple(Icons.Filled.GpsNotFixed, "GPS weak", accuracyColor(accuracyM))
        accuracyM > 10f -> Triple(Icons.Filled.GpsFixed, "GPS fair", accuracyColor(accuracyM))
        else -> Triple(Icons.Filled.GpsFixed, "GPS good", accuracyColor(accuracyM))
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) },
        colors = AssistChipDefaults.assistChipColors(),
        modifier = modifier,
    )
}

/** Parking-meter countdown that ticks every second. */
@Composable
fun MeterCountdownChip(expiresAtMs: Long, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
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
    val tint = when {
        expired -> MaterialTheme.colorScheme.error
        remaining < 10 * 60_000L -> WarningAmber
        else -> MaterialTheme.colorScheme.primary
    }
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(if (expired) Icons.Filled.TimerOff else Icons.Filled.Timer, null, tint = tint, modifier = Modifier.size(18.dp))
        },
        modifier = modifier.semantics { contentDescription = label },
    )
}
