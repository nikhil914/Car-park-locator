package com.realworldmagic.parkpin.ui.map

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.realworldmagic.parkpin.domain.GeoMath
import com.realworldmagic.parkpin.domain.OfflineAreaPlan
import com.realworldmagic.parkpin.domain.OfflineAreaPlanner
import com.realworldmagic.parkpin.domain.ParkingSpot
import com.realworldmagic.parkpin.ui.components.LocationPermissions
import com.realworldmagic.parkpin.ui.components.hasAnyPermission
import com.realworldmagic.parkpin.ui.util.formatDateTime
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import com.realworldmagic.parkpin.domain.GeoPoint as PinGeoPoint

private fun PinGeoPoint.toLatLng() = LatLng(lat, lng)
private fun PinGeoPoint.toPoint(): Point = Point.fromLngLat(lng, lat)

private const val SRC_CAR = "parkpin-car"
private const val SRC_USER = "parkpin-user"
private const val SRC_LINE = "parkpin-line"
private const val IMG_CAR = "parkpin-car-pin"
private const val DEFAULT_ZOOM = 17.0

private val EMPTY: FeatureCollection = FeatureCollection.fromFeatures(emptyList<Feature>())

private fun isOnline(context: Context): Boolean = try {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
} catch (e: SecurityException) {
    true // ACCESS_NETWORK_STATE missing: let MapLibre try; it falls back to its offline database.
}

/** Live connectivity, updated from the default-network callback while in composition. */
@Composable
private fun rememberOnline(context: Context): Boolean {
    var online by remember { mutableStateOf(isOnline(context)) }
    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                online = isOnline(context)
            }

            override fun onLost(network: Network) {
                online = false
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                online = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        val registered = try {
            cm.registerDefaultNetworkCallback(callback)
            true
        } catch (e: Exception) {
            false // SecurityException without ACCESS_NETWORK_STATE, or too many callbacks.
        }
        onDispose { if (registered) runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return online
}

/** A map-pin bitmap in [color] with a white centre, anchored at its bottom tip. */
private fun carPinBitmap(color: Int, density: Float): Bitmap {
    val w = (36 * density).toInt().coerceAtLeast(1)
    val h = (48 * density).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = w / 2f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    val path = Path().apply {
        moveTo(r, h.toFloat() - stroke.strokeWidth)
        lineTo(r * 0.25f, r * 1.45f)
        lineTo(r * 1.75f, r * 1.45f)
        close()
        addCircle(r, r, r - stroke.strokeWidth, Path.Direction.CW)
    }
    c.drawPath(path, fill)
    c.drawCircle(r, r, r - stroke.strokeWidth, stroke)
    c.drawCircle(r, r, r * 0.38f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE })
    return bmp
}

/** Adds the ParkPin sources and layers on top of the base style. */
private fun addOverlays(style: Style, carColor: Int, userColor: Int, density: Float) {
    style.addImage(IMG_CAR, carPinBitmap(carColor, density))
    style.addSource(GeoJsonSource(SRC_LINE))
    style.addSource(GeoJsonSource(SRC_USER))
    style.addSource(GeoJsonSource(SRC_CAR))
    style.addLayer(
        LineLayer("$SRC_LINE-layer", SRC_LINE).withProperties(
            PropertyFactory.lineColor(carColor),
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
        ),
    )
    style.addLayer(
        CircleLayer("$SRC_USER-layer", SRC_USER).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(userColor),
            PropertyFactory.circleStrokeWidth(3f),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
        ),
    )
    style.addLayer(
        SymbolLayer("$SRC_CAR-layer", SRC_CAR).withProperties(
            PropertyFactory.iconImage(IMG_CAR),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ),
    )
}

private fun Style.setCollection(sourceId: String, fc: FeatureCollection) {
    getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(fc)
}

/** Auto-generated name for an offline area saved around [spot]. */
internal fun offlineAreaName(spot: ParkingSpot): String {
    val label = listOfNotNull(
        spot.note.takeIf { it.isNotBlank() },
        spot.floorLabel?.takeIf { it.isNotBlank() },
        spot.address?.takeIf { it.isNotBlank() },
    ).firstOrNull()
    val time = formatDateTime(spot.savedAtMs)
    return if (label != null) "$label · $time" else "Parked $time"
}

private data class DownloadProgress(val done: Long, val total: Long)

/**
 * Drives [mapView] through the Android lifecycle of [lifecycleOwner]: onStart/onResume/onPause/
 * onStop/onDestroy plus onLowMemory. MapView.onCreate is called once when the view is built.
 */
@Composable
private fun MapViewLifecycle(mapView: MapView) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        var started = false
        var resumed = false
        fun start() { if (!started) { mapView.onStart(); started = true } }
        fun resume() { start(); if (!resumed) { mapView.onResume(); resumed = true } }
        fun pause() { if (resumed) { mapView.onPause(); resumed = false } }
        fun stop() { pause(); if (started) { mapView.onStop(); started = false } }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        val memory = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = mapView.onLowMemory()

            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) mapView.onLowMemory()
            }
        }
        // addObserver replays ON_START/ON_RESUME up to the current state.
        lifecycleOwner.lifecycle.addObserver(observer)
        context.applicationContext.registerComponentCallbacks(memory)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(memory)
            stop()
            mapView.onDestroy()
        }
    }
}

@Composable
fun MapScreen(spotId: Long, onGuide: (Long) -> Unit) {
    val context = LocalContext.current
    val locationAllowed = remember { hasAnyPermission(context, LocationPermissions) }
    val vm: MapViewModel = viewModel(key = "map-$spotId", factory = MapViewModel.factory(spotId, locationAllowed))
    val ui by vm.state.collectAsStateWithLifecycle()
    val carColor = MaterialTheme.colorScheme.primary.toArgb()
    val userColor = Color(0xFF1A73E8).toArgb()
    val density = context.resources.displayMetrics.density

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { m ->
                m.uiSettings.isAttributionEnabled = true
                m.uiSettings.isLogoEnabled = true
                m.uiSettings.isCompassEnabled = true
                m.setMinZoomPreference(3.0)
                m.setMaxZoomPreference(20.0)
                m.cameraPosition = CameraPosition.Builder().zoom(DEFAULT_ZOOM).build()
                map = m
                m.setStyle(Style.Builder().fromUri(MapStyle.STYLE_URL)) { s ->
                    addOverlays(s, carColor, userColor, density)
                    style = s
                }
            }
        }
    }
    MapViewLifecycle(mapView)

    var centeredOnce by rememberSaveable { mutableStateOf(false) }

    // Offline: tell MapLibre so it serves the ambient cache and offline regions without retrying.
    val online = rememberOnline(context)
    LaunchedEffect(online) { MapLibre.setConnected(online) }

    val scope = rememberCoroutineScope()
    val regions = remember { OfflineRegions(context) }
    DisposableEffect(regions) { onDispose { regions.cancel() } }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmPlan by remember { mutableStateOf<OfflineAreaPlan?>(null) }
    var progress by remember { mutableStateOf<DownloadProgress?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var savedList by remember { mutableStateOf<List<SavedRegion>?>(null) }

    // Sync overlays with state.
    LaunchedEffect(style, ui.spot, ui.fix) {
        val s = style ?: return@LaunchedEffect
        val spot = ui.spot
        val fix = ui.fix
        s.setCollection(
            SRC_CAR,
            spot?.let { FeatureCollection.fromFeature(Feature.fromGeometry(it.point.toPoint())) } ?: EMPTY,
        )
        s.setCollection(
            SRC_USER,
            fix?.let { FeatureCollection.fromFeature(Feature.fromGeometry(it.point.toPoint())) } ?: EMPTY,
        )
        s.setCollection(
            SRC_LINE,
            if (spot != null && fix != null) {
                FeatureCollection.fromFeature(
                    Feature.fromGeometry(LineString.fromLngLats(listOf(fix.point.toPoint(), spot.point.toPoint()))),
                )
            } else {
                EMPTY
            },
        )
        if (!centeredOnce) {
            val target = spot?.point ?: fix?.point
            if (target != null) {
                map?.moveCamera(CameraUpdateFactory.newLatLngZoom(target.toLatLng(), DEFAULT_ZOOM))
                centeredOnce = true
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!online) OfflineBanner()
            InfoBubble(ui)
        }

        // Offline-area menu.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 8.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(50)),
        ) {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Map options") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val spot = ui.spot
                DropdownMenuItem(
                    text = { Text(if (online) "Save area for offline" else "Save area for offline (needs internet)") },
                    enabled = spot != null && online && progress == null,
                    onClick = {
                        menuOpen = false
                        val plan = spot?.let { OfflineAreaPlanner.plan(it.point) }
                        if (plan == null) notice = "Can't plan an offline area here." else confirmPlan = plan
                    },
                )
                DropdownMenuItem(
                    text = { Text("Saved offline areas") },
                    onClick = {
                        menuOpen = false
                        scope.launch { savedList = regions.list() }
                    },
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (ui.fix != null) {
                SmallFloatingActionButton(onClick = {
                    ui.fix?.let { map?.animateCamera(CameraUpdateFactory.newLatLng(it.point.toLatLng())) }
                }) { Icon(Icons.Filled.MyLocation, "Center on me") }
            }
            ui.spot?.let { spot ->
                FloatingActionButton(onClick = {
                    map?.animateCamera(CameraUpdateFactory.newLatLng(spot.point.toLatLng()))
                }) {
                    Icon(Icons.Filled.DirectionsCar, "Center on car")
                }
                ExtendedFloatingActionButton(
                    onClick = { onGuide(spot.id) },
                    icon = { Icon(Icons.Filled.Explore, null) },
                    text = { Text("Guide") },
                )
            }
        }

        // MapLibre's own (i) attribution button lists the style's sources; this keeps the
        // required credit visible at all times as well.
        Text(
            MapStyle.ATTRIBUTION,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 34.dp)
                .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )

        confirmPlan?.let { plan ->
            AlertDialog(
                onDismissRequest = { confirmPlan = null },
                title = { Text("Save area for offline?") },
                text = {
                    Text(
                        "Downloads the map (zoom ${plan.minZoom}–${plan.maxZoom}) for about 1 km around your " +
                            "car, so it works without signal. Usually a few MB.\n\n" +
                            "Map data ${MapStyle.ATTRIBUTION}."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmPlan = null
                        val spot = ui.spot ?: return@TextButton
                        progress = DownloadProgress(0, 0)
                        regions.start(
                            name = offlineAreaName(spot),
                            plan = plan,
                            pixelRatio = density,
                            onProgress = { done, total -> progress = DownloadProgress(done, total) },
                            onFinished = { error ->
                                progress = null
                                notice = if (error == null) {
                                    "Area saved for offline use."
                                } else {
                                    "Couldn't save the area: $error"
                                }
                            },
                        )
                    }) { Text("Download") }
                },
                dismissButton = { TextButton(onClick = { confirmPlan = null }) { Text("Cancel") } },
            )
        }

        progress?.let { p ->
            AlertDialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                title = { Text("Saving map area") },
                text = {
                    Column {
                        if (p.total > 0) {
                            val fraction = (p.done.toFloat() / p.total).coerceIn(0f, 1f)
                            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(if (p.total > 0) "${p.done} / ${p.total} resources" else "Starting…")
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        regions.cancel()
                        progress = null
                        notice = "Download cancelled."
                    }) { Text("Cancel") }
                },
            )
        }

        savedList?.let { list ->
            AlertDialog(
                onDismissRequest = { savedList = null },
                title = { Text("Saved offline areas") },
                text = {
                    if (list.isEmpty()) {
                        Text("No areas saved yet. Use \"Save area for offline\" while online.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            list.forEach { r ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(r.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            formatBytes(r.sizeBytes) + if (r.complete) "" else " · incomplete",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            val ok = regions.delete(r)
                                            if (!ok) notice = "Couldn't delete that area."
                                            savedList = regions.list()
                                        }
                                    }) { Icon(Icons.Filled.Delete, "Delete ${r.name}") }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { savedList = null }) { Text("Close") } },
            )
        }

        notice?.let { msg ->
            AlertDialog(
                onDismissRequest = { notice = null },
                text = { Text(msg) },
                confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } },
            )
        }
    }
}

@Composable
private fun InfoBubble(ui: MapUiState) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val spot = ui.spot
        when {
            !ui.loaded -> Text("Loading…")
            spot == null -> Text("No active parking spot")
            else -> {
                Text(
                    ui.distanceM?.let { "Car is " + GeoMath.formatDistance(it, ui.settings.useMetric) + " away" } ?: "Your car",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (spot.note.isNotBlank()) Text(spot.note, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.width(6.dp))
        Text(
            "Offline — showing saved map areas",
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
