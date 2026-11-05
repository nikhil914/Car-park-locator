package com.realworldmagic.parkpin.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import com.realworldmagic.parkpin.data.AppSettings
import com.realworldmagic.parkpin.domain.GeoMath
import com.realworldmagic.parkpin.domain.ParkingSpot
import com.realworldmagic.parkpin.ui.components.AccuracyBadge
import com.realworldmagic.parkpin.ui.components.LocationPermissions
import com.realworldmagic.parkpin.ui.components.MeterCountdownChip
import com.realworldmagic.parkpin.ui.components.hasAnyPermission
import com.realworldmagic.parkpin.ui.util.formatAgo
import com.realworldmagic.parkpin.ui.util.formatTime
import com.realworldmagic.parkpin.ui.util.openAppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onGuide: (Long) -> Unit,
    onAr: (Long) -> Unit,
    onMap: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val spot by viewModel.activeSpot.collectAsStateWithLifecycle()
    val capture by viewModel.capture.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }

    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var confirmFound by rememberSaveable { mutableStateOf(false) }
    var confirmReplace by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            permissionDenied = false
            viewModel.saveSpotHere()
        } else {
            permissionDenied = true
        }
    }

    fun startSave() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (hasAnyPermission(context, LocationPermissions)) viewModel.saveSpotHere()
        else permissionLauncher.launch(LocationPermissions.toTypedArray())
    }

    LaunchedEffect(capture.savedEvent) {
        if (capture.savedEvent > 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(capture.message) {
        capture.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("ParkPin", fontWeight = FontWeight.Black)
                    Text(
                        "Real-World Magic",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val current = spot
            Spacer(Modifier.height(if (current == null) 48.dp else 8.dp))
            ParkButton(
                capturing = capture.capturing,
                liveAccuracyM = capture.liveAccuracyM,
                useMetric = settings.useMetric,
                size = if (current == null) 240.dp else 150.dp,
                onClick = {
                    when {
                        capture.capturing -> viewModel.cancelCapture()
                        current != null -> confirmReplace = true
                        else -> startSave()
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            if (current == null) {
                Text(
                    if (capture.capturing) "Getting the best GPS fix…" else "Tap when you leave your car",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Works offline: GPS and compass only. Nothing leaves your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            AnimatedVisibility(permissionDenied) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        "Location permission is needed to remember where you parked.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { context.openAppSettings() }) { Text("Open settings") }
                }
            }
            if (current != null) {
                ActiveSpotCard(
                    spot = current,
                    settings = settings,
                    onGuide = { onGuide(current.id) },
                    onAr = { onAr(current.id) },
                    onMap = { onMap(current.id) },
                    onEdit = { onEdit(current.id) },
                    onFound = { confirmFound = true },
                )
            }
        }
    }

    if (confirmFound) {
        AlertDialog(
            onDismissRequest = { confirmFound = false },
            icon = { Icon(Icons.Filled.CheckCircle, null) },
            title = { Text("Found your car?") },
            text = { Text("This clears the active spot. It stays in your history.") },
            confirmButton = {
                Button(onClick = {
                    confirmFound = false
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.foundMyCar()
                }) { Text("Found it") }
            },
            dismissButton = { TextButton(onClick = { confirmFound = false }) { Text("Cancel") } },
        )
    }
    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Save a new spot?") },
            text = { Text("Your current spot moves to history and this location becomes active.") },
            confirmButton = {
                Button(onClick = { confirmReplace = false; startSave() }) { Text("Save here") }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ParkButton(
    capturing: Boolean,
    liveAccuracyM: Float?,
    useMetric: Boolean,
    size: Dp,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (capturing) 1000 else 2200, easing = LinearEasing), RepeatMode.Restart),
        label = "ring",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val label = when {
        capturing -> "Saving parking location. Tap to cancel."
        else -> "I parked here. Save current location."
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size * 1.35f)) {
        Canvas(Modifier.matchParentSize()) {
            val maxR = this.size.minDimension / 2f
            val baseR = maxR / 1.35f
            for (k in 0 until 2) {
                val p = (pulse + k * 0.5f) % 1f
                drawCircle(
                    primary.copy(alpha = (1f - p) * 0.45f),
                    radius = baseR + (maxR - baseR) * p,
                    style = Stroke(width = 6f),
                )
            }
        }
        Surface(
            shape = CircleShape,
            shadowElevation = 12.dp,
            color = Color.Transparent,
            modifier = Modifier
                .size(size)
                .scale(if (capturing) 1f else breathe)
                .clip(CircleShape)
                .semantics {
                    role = Role.Button
                    contentDescription = label
                }
                .clickable(onClick = onClick),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(primary, secondary))),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (capturing) {
                        CircularProgressIndicator(color = onPrimary, modifier = Modifier.size(size / 4))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            liveAccuracyM?.let { "±" + GeoMath.formatDistance(it.toDouble(), useMetric) } ?: "Locating…",
                            color = onPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                        )
                        Text("Tap to cancel", color = onPrimary.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                    } else {
                        Icon(Icons.Filled.DirectionsCar, null, tint = onPrimary, modifier = Modifier.size(size / 4))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "I parked\nhere",
                            color = onPrimary,
                            textAlign = TextAlign.Center,
                            style = if (size > 200.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveSpotCard(
    spot: ParkingSpot,
    settings: AppSettings,
    onGuide: () -> Unit,
    onAr: () -> Unit,
    onMap: () -> Unit,
    onEdit: () -> Unit,
    onFound: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DirectionsCar, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Your car", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Saved ${formatTime(spot.savedAtMs)} · ${formatAgo(spot.savedAtMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (spot.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(spot.note, style = MaterialTheme.typography.bodyLarge)
            }
            spot.floorLabel?.takeIf { it.isNotBlank() }?.let {
                Text("Floor $it", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccuracyBadge(spot.accuracyM, settings.useMetric)
                spot.meterExpiresAtMs?.let { MeterCountdownChip(it, onClick = onEdit) }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onGuide, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Filled.Explore, null)
                Spacer(Modifier.width(8.dp))
                Text("Guide me back", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (settings.arEnabled) {
                    FilledTonalButton(onClick = onAr) {
                        Icon(Icons.Filled.ViewInAr, null); Spacer(Modifier.width(6.dp)); Text("AR")
                    }
                }
                FilledTonalButton(onClick = onMap) {
                    Icon(Icons.Filled.Map, null); Spacer(Modifier.width(6.dp)); Text("Map")
                }
                FilledTonalButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onFound, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text("Found my car")
            }
        }
    }
}
