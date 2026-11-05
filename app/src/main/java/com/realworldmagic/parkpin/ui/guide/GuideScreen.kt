package com.realworldmagic.parkpin.ui.guide

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.realworldmagic.parkpin.domain.GeoMath
import com.realworldmagic.parkpin.ui.components.AccuracyBadge
import com.realworldmagic.parkpin.ui.components.CompassCalibrationDialog
import com.realworldmagic.parkpin.ui.components.DirectionArrow
import com.realworldmagic.parkpin.ui.components.DistanceText
import com.realworldmagic.parkpin.ui.components.GpsStatusChip
import com.realworldmagic.parkpin.ui.components.LocationPermissions
import com.realworldmagic.parkpin.ui.components.MeterCountdownChip
import com.realworldmagic.parkpin.ui.components.PermissionGate
import com.realworldmagic.parkpin.ui.theme.NumericStyle
import com.realworldmagic.parkpin.ui.util.KeepScreenOn
import com.realworldmagic.parkpin.ui.util.vibrate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    spotId: Long,
    onBack: () -> Unit,
    onOpenAr: (Long) -> Unit,
    onOpenMap: (Long) -> Unit,
    onFoundCar: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guide to car") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            PermissionGate(
                permissions = LocationPermissions,
                anyGranted = true,
                title = "Location needed",
                rationale = "ParkPin uses GPS to point you back to your car. It works offline and your location never leaves the device.",
            ) {
                GuideContent(spotId, onOpenAr, onOpenMap, onFoundCar)
            }
        }
    }
}

/** Builds the TalkBack sentence, e.g. "Car at 2 o'clock, 40 m away". Distance rounded to limit chatter. */
internal fun guidanceAnnouncement(distanceM: Double?, clock: Int?, cardinal: String?, metric: Boolean, arrived: Boolean): String {
    if (arrived) return "You have arrived at your car."
    if (distanceM == null) return "Waiting for GPS."
    val step = when {
        distanceM < 50 -> 5.0
        distanceM < 500 -> 10.0
        else -> 50.0
    }
    val rounded = (distanceM / step).roundToInt() * step
    val dist = GeoMath.formatDistance(rounded, metric)
    return when {
        clock != null -> "Car at $clock o'clock, $dist away."
        cardinal != null -> "Car is $cardinal, $dist away."
        else -> "Car is $dist away."
    }
}

@Composable
private fun GuideContent(
    spotId: Long,
    onOpenAr: (Long) -> Unit,
    onOpenMap: (Long) -> Unit,
    onFoundCar: () -> Unit,
) {
    val vm: GuideViewModel = viewModel(key = "guide-$spotId", factory = GuideViewModel.factory(spotId))
    val ui by vm.state.collectAsStateWithLifecycle()
    val g = ui.guidance
    val context = LocalContext.current
    val metric = ui.settings.useMetric

    KeepScreenOn(ui.settings.keepScreenOn)

    var calibrationDismissed by rememberSaveable { mutableStateOf(false) }
    var showCalibration by remember { mutableStateOf(false) }
    LaunchedEffect(g.compassNeedsCalibration) {
        if (g.compassNeedsCalibration && !calibrationDismissed) showCalibration = true
    }
    if (showCalibration) {
        CompassCalibrationDialog(onDismiss = { showCalibration = false; calibrationDismissed = true })
    }

    LaunchedEffect(g.arrived) {
        if (g.arrived) context.vibrate()
    }

    if (ui.loaded && g.spot == null) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No parking spot to guide to.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        return
    }

    val announcement = guidanceAnnouncement(g.distanceM, ui.clock, ui.cardinal, metric, g.arrived)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GpsStatusChip(g.fix?.accuracyM)
            AccuracyBadge(g.fix?.accuracyM, metric)
        }
        g.spot?.meterExpiresAtMs?.let { MeterCountdownChip(it) }

        AnimatedVisibility(g.gpsWeak, enter = fadeIn(), exit = fadeOut()) {
            WarningCard("GPS signal is weak. Move to open sky for a better fix.")
        }
        AnimatedVisibility(!ui.compassAvailable) {
            WarningCard("No compass on this device. Arrow shows direction relative to north.")
        }
        AnimatedVisibility(g.compassNeedsCalibration && !showCalibration) {
            AssistChip(onClick = { showCalibration = true }, label = { Text("Compass inaccurate. Tap to calibrate") },
                leadingIcon = { Icon(Icons.Filled.Explore, null) })
        }

        Spacer(Modifier.height(16.dp))

        // Accessible summary: one node, polite live region, rounded values.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = announcement
                liveRegion = LiveRegionMode.Polite
            },
        ) {
            if (g.arrived) {
                ArrivedBanner()
            } else {
                // With a compass: relative bearing. Without: absolute bearing (north-up).
                val rotation = (g.relativeDeg ?: if (!ui.compassAvailable) g.bearingDeg else null)?.toFloat()
                DirectionArrow(rotationDeg = rotation, size = 260.dp)
                Spacer(Modifier.height(12.dp))
                DistanceText(g.distanceM, metric, style = NumericStyle, color = MaterialTheme.colorScheme.primary)
                val dirText = buildString {
                    ui.clock?.let { append("$it o'clock") }
                    ui.cardinal?.let {
                        if (isNotEmpty()) append(" · ")
                        append("heading ").append(it)
                    }
                }
                if (dirText.isNotEmpty()) Text(dirText, style = MaterialTheme.typography.titleLarge)
                ui.etaMinutes?.let {
                    Text(
                        if (it <= 1) "About 1 min walk" else "About $it min walk",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (g.fix == null) {
                    Text("Waiting for GPS…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        g.spot?.let { spot ->
            if (spot.note.isNotBlank() || !spot.floorLabel.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    listOfNotNull(spot.note.takeIf { it.isNotBlank() }, spot.floorLabel?.takeIf { it.isNotBlank() }?.let { "Floor $it" })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ui.settings.arEnabled) {
                    Button(onClick = { onOpenAr(spot.id) }) {
                        Icon(Icons.Filled.ViewInAr, null); Spacer(Modifier.width(6.dp)); Text("AR view")
                    }
                }
                Button(onClick = { onOpenMap(spot.id) }) {
                    Icon(Icons.Filled.Map, null); Spacer(Modifier.width(6.dp)); Text("Map")
                }
            }
            if (g.arrived && spot.isActive) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.foundMyCar(onFoundCar) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Found my car", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ArrivedBanner() {
    val t = rememberInfiniteTransition(label = "celebrate")
    val s by t.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "s")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 32.dp)) {
        Icon(
            Icons.Filled.Celebration,
            null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(140.dp).scale(s),
        )
        Spacer(Modifier.height(12.dp))
        Text("You're there!", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text("Your car should be right around you.", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}
