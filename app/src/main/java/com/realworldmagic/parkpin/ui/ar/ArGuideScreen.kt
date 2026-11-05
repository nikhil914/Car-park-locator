package com.realworldmagic.parkpin.ui.ar

import android.Manifest
import android.annotation.SuppressLint
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.realworldmagic.parkpin.domain.ArProjection
import com.realworldmagic.parkpin.domain.ArProjectionResult
import com.realworldmagic.parkpin.domain.CenterDetector
import com.realworldmagic.parkpin.domain.GeoMath
import com.realworldmagic.parkpin.domain.SonarPattern
import com.realworldmagic.parkpin.domain.SpokenText
import com.realworldmagic.parkpin.ui.components.Haptics
import com.realworldmagic.parkpin.ui.components.LocationPermissions
import com.realworldmagic.parkpin.ui.components.PermissionGate
import com.realworldmagic.parkpin.ui.guide.GuideViewModel
import com.realworldmagic.parkpin.ui.components.rememberReduceMotion
import com.realworldmagic.parkpin.ui.components.rememberScreenReaderOn
import com.realworldmagic.parkpin.ui.components.rememberThrottledAnnouncement
import com.realworldmagic.parkpin.ui.util.KeepScreenOn
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ArGuideScreen(spotId: Long, onBack: () -> Unit) {
    // Sonar (vibration-only guidance) defaults to on when TalkBack is running.
    val screenReaderOn = rememberScreenReaderOn()
    var sonarOn by rememberSaveable { mutableStateOf(screenReaderOn) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PermissionGate(
            permissions = listOf(Manifest.permission.CAMERA),
            title = "Camera needed for AR",
            rationale = "The AR view shows a floating pin over the camera image. Frames are never stored or sent anywhere.",
            icon = Icons.Filled.CameraAlt,
        ) {
            PermissionGate(
                permissions = LocationPermissions,
                anyGranted = true,
                title = "Location needed",
                rationale = "GPS is used to place the pin in the direction of your car.",
            ) {
                ArContent(spotId, sonarOn)
            }
        }
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(12.dp).size(48.dp),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        FilledTonalIconToggleButton(
            checked = sonarOn,
            onCheckedChange = { sonarOn = it },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
                .size(48.dp)
                .semantics { stateDescription = if (sonarOn) "On" else "Off" },
        ) {
            Icon(
                Icons.Filled.Vibration,
                contentDescription = "Vibration sonar: pulses get faster as you point at your car",
            )
        }
    }
}

@Composable
private fun ArContent(spotId: Long, sonarOn: Boolean) {
    val vm: GuideViewModel = viewModel(key = "ar-$spotId", factory = GuideViewModel.factory(spotId, cameraMode = true))
    val ui by vm.state.collectAsStateWithLifecycle()
    val g = ui.guidance
    val metric = ui.settings.useMetric
    var tanHalf by remember { mutableStateOf(ArMath.DEFAULT_TAN_HALF_H to ArMath.DEFAULT_TAN_HALF_V) }

    KeepScreenOn(ui.settings.keepScreenOn || sonarOn)

    val context = LocalContext.current
    val haptics = remember(context) { Haptics(context) }
    val reduceMotion = rememberReduceMotion()
    // Relative bearing for aim feedback; null when unknown or already arrived.
    val aimDeg: Double? = if (g.arrived || g.heading == null || g.distanceM == null) null else g.relativeDeg
    val centerDetector = remember { CenterDetector(enterDeg = 5.0, exitDeg = 8.0) }
    var centered by remember { mutableStateOf(false) }
    LaunchedEffect(aimDeg) {
        // Haptic "tick" once each time the car enters the centre (±5°) of the view.
        if (centerDetector.update(aimDeg)) haptics.tick()
        centered = centerDetector.centered
    }
    val latestAim by rememberUpdatedState(aimDeg)
    LaunchedEffect(sonarOn) {
        if (!sonarOn) return@LaunchedEffect
        try {
            while (isActive) {
                val rel = latestAim
                if (rel == null) {
                    delay(500L)
                    continue
                }
                val ping = SonarPattern.ping(rel)
                haptics.pulse(ping.pulseMs, ping.amplitude)
                delay(ping.intervalMs)
            }
        } finally {
            haptics.cancel()
        }
    }
    val rawAnnouncement = arAnnouncement(
        distanceM = g.distanceM,
        clock = ui.clock,
        metric = metric,
        arrived = g.arrived,
        hasFix = g.fix != null,
        hasHeading = g.heading != null,
        centered = centered,
        gpsWeak = g.gpsWeak,
        compassNeedsCalibration = g.compassNeedsCalibration,
    )
    val announcement = rememberThrottledAnnouncement(rawAnnouncement, urgent = g.arrived)

    Box(Modifier.fillMaxSize()) {
        CameraPreview(onCameraBound = { camera -> readFov(camera)?.let { tanHalf = it } })

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            val marginPx = with(density) { 48.dp.toPx() }
            val fov = ArMath.displayedFov(tanHalf.first, tanHalf.second, wPx, hPx)
            val heading = g.heading
            val rel = g.relativeDeg
            val dist = g.distanceM

            if (heading != null && rel != null && dist != null && !g.arrived) {
                val altDelta = 0.0 // GPS altitude too noisy for elevation; assume same level
                val elevation = ArProjection.targetElevationDeg(dist, altDelta)
                val result = ArProjection.project(
                    relativeBearingDeg = rel,
                    elevationDeg = elevation,
                    devicePitchDeg = heading.pitchDeg.toDouble(),
                    horizontalFovDeg = fov.horizontalDeg,
                    verticalFovDeg = fov.verticalDeg,
                    screenWidthPx = wPx,
                    screenHeightPx = hPx,
                    marginPx = marginPx,
                )
                val label = GeoMath.formatDistance(dist, metric)
                when (result) {
                    is ArProjectionResult.OnScreen -> PinMarker(result.x, result.y, label, dist, reduceMotion)
                    is ArProjectionResult.OffScreen -> EdgeArrow(result.x, result.y, result.arrowAngleDeg, label, result.behind)
                }
            }
        }

        // Bottom info panel.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                .padding(16.dp)
                .clearAndSetSemantics {
                    // Throttled: at most one routine announcement every few seconds.
                    contentDescription = announcement
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            val pitch = g.heading?.pitchDeg
            when {
                g.arrived -> Text(
                    "You're there! Look around for your car.",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                g.fix == null -> Text("Waiting for GPS…", color = Color.White)
                g.heading == null -> Text("Waiting for compass…", color = Color.White)
                else -> {
                    Text(
                        g.distanceM?.let { GeoMath.formatDistance(it, metric) } ?: "--",
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    ui.clock?.let {
                        Text(
                            if (centered) "Straight ahead" else "$it o'clock",
                            color = if (centered) Color(0xFF69F0AE) else Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            if (pitch != null && abs(pitch) > 45f) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Hold your phone upright, camera facing forward",
                    color = Color(0xFFFFD54F),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Icon(Icons.Filled.ScreenRotation, null, tint = Color(0xFFFFD54F))
            }
            if (g.gpsWeak) Text("GPS weak: pin may be off", color = Color(0xFFFFCCBC), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            if (g.compassNeedsCalibration) Text("Compass inaccurate: wave phone in a figure-8", color = Color(0xFFFFCCBC), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PinMarker(x: Float, y: Float, label: String, distanceM: Double, reduceMotion: Boolean) {
    val spec: AnimationSpec<Float> = if (reduceMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow)
    val ax by animateFloatAsState(x, spec, label = "x")
    val ay by animateFloatAsState(y, spec, label = "y")
    // Closer = bigger pin (clamped).
    val sizeDp = (120 - (distanceM / 5).coerceAtMost(70.0)).toFloat().dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clearAndSetSemantics { } // decorative: the bottom panel announces guidance
            .layout { measurable, constraints ->
                // Wrap content (label grows with font scale) and put the pin tip on the projected point.
                val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(p.width, p.height) {
                    p.place((ax - p.width / 2f).roundToInt(), (ay - p.height).roundToInt())
                }
            },
    ) {
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(sizeDp))
    }
}

@Composable
private fun EdgeArrow(x: Float, y: Float, angleDeg: Float, label: String, behind: Boolean) {
    val density = LocalDensity.current
    val half = with(density) { 32.dp.toPx() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .offset { IntOffset((x - half).roundToInt(), (y - half).roundToInt()) }
            .clearAndSetSemantics { },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f), RoundedCornerShape(32.dp)),
        ) {
            Icon(
                Icons.Filled.ArrowUpward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp).rotate(angleDeg),
            )
        }
        Text(
            if (behind) "$label · behind you" else label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** TalkBack sentence for the AR view, e.g. "Car at 2 o'clock, 40 meters away." */
internal fun arAnnouncement(
    distanceM: Double?,
    clock: Int?,
    metric: Boolean,
    arrived: Boolean,
    hasFix: Boolean,
    hasHeading: Boolean,
    centered: Boolean,
    gpsWeak: Boolean,
    compassNeedsCalibration: Boolean,
): String {
    if (arrived) return "You have arrived. Look around for your car."
    if (!hasFix || distanceM == null) return "Waiting for GPS."
    if (!hasHeading) return "Waiting for compass."
    // Round so small GPS jitter does not create a "new" sentence every second.
    val rounded = when {
        distanceM < 20 -> distanceM
        distanceM < 100 -> (distanceM / 5.0).roundToInt() * 5.0
        distanceM < 1000 -> (distanceM / 10.0).roundToInt() * 10.0
        else -> (distanceM / 100.0).roundToInt() * 100.0
    }
    val dist = SpokenText.distance(rounded, metric)
    val base = when {
        centered -> "Car straight ahead, $dist away."
        clock != null -> "Car at $clock o'clock, $dist away."
        else -> "Car $dist away."
    }
    val warnings = buildString {
        if (gpsWeak) append(" GPS weak.")
        if (compassNeedsCalibration) append(" Compass needs calibration.")
    }
    return base + warnings
}

@Composable
private fun CameraPreview(onCameraBound: (Camera) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                p.unbindAll()
                val camera = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                onCameraBound(camera)
            } catch (e: Exception) {
                Log.w("ArGuide", "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            provider?.unbindAll()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** Reads physical sensor size + focal length to estimate FOV; null if unavailable. */
@SuppressLint("UnsafeOptInUsageError")
@OptIn(ExperimentalCamera2Interop::class)
private fun readFov(camera: Camera): Pair<Double, Double>? = try {
    val info = Camera2CameraInfo.from(camera.cameraInfo)
    val size = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    val focal = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
    if (size != null && focal != null && focal > 0f) {
        ArMath.portraitTanHalfFov(size.width, size.height, focal)
    } else null
} catch (e: Exception) {
    null
}
