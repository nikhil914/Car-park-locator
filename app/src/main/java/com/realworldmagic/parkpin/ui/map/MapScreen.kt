package com.realworldmagic.parkpin.ui.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.realworldmagic.parkpin.domain.GeoMath
import com.realworldmagic.parkpin.domain.OfflineAreaPlan
import com.realworldmagic.parkpin.domain.OfflineAreaPlanner
import com.realworldmagic.parkpin.ui.components.LocationPermissions
import com.realworldmagic.parkpin.ui.components.hasAnyPermission
import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import com.realworldmagic.parkpin.domain.GeoPoint as PinGeoPoint

private fun PinGeoPoint.toOsm() = OsmGeoPoint(lat, lng)

private fun isOnline(context: Context): Boolean = try {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
} catch (e: SecurityException) {
    true // ACCESS_NETWORK_STATE missing: let osmdroid try; it falls back to cache.
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

private data class DownloadProgress(val done: Int, val total: Int)

@Composable
fun MapScreen(spotId: Long, onGuide: (Long) -> Unit) {
    val context = LocalContext.current
    val locationAllowed = remember { hasAnyPermission(context, LocationPermissions) }
    val vm: MapViewModel = viewModel(key = "map-$spotId", factory = MapViewModel.factory(spotId, locationAllowed))
    val ui by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val carColor = MaterialTheme.colorScheme.primary.toArgb()

    val mapView = remember {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = context.packageName
        }
        MapView(context).apply {
            setTileSource(MapTiles.SOURCE)
            setMultiTouchControls(true)
            setUseDataConnection(isOnline(context))
            controller.setZoom(18.0)
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
        }
    }
    val carMarker = remember {
        Marker(mapView).apply {
            title = "My car"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
    }
    val line = remember {
        Polyline(mapView).apply {
            outlinePaint.color = carColor
            outlinePaint.strokeWidth = 10f
            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(30f, 20f), 0f)
        }
    }
    val myLocation = remember {
        if (locationAllowed) MyLocationNewOverlay(GpsMyLocationProvider(context), mapView) else null
    }

    DisposableEffect(lifecycleOwner) {
        mapView.overlays.add(line)
        mapView.overlays.add(carMarker)
        myLocation?.let { mapView.overlays.add(it) }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.setUseDataConnection(isOnline(context))
                    mapView.onResume()
                    myLocation?.enableMyLocation()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    myLocation?.disableMyLocation()
                    mapView.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume(); myLocation?.enableMyLocation()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            myLocation?.disableMyLocation()
            mapView.onPause()
            mapView.overlays.clear()
            mapView.onDetach()
        }
    }

    var centeredOnce by rememberSaveable { mutableStateOf(false) }

    // Offline: never hit the network; osmdroid serves whatever is in the tile cache.
    val online = rememberOnline(context)
    LaunchedEffect(online) {
        mapView.setUseDataConnection(online)
        mapView.invalidate()
    }

    val scope = rememberCoroutineScope()
    val downloader = remember { OfflineAreaDownloader(mapView) }
    DisposableEffect(downloader) { onDispose { downloader.cancel() } }
    var menuOpen by remember { mutableStateOf(false) }
    var cacheInfo by remember { mutableStateOf<TileCacheInfo?>(null) }
    var confirmPlan by remember { mutableStateOf<OfflineAreaPlan?>(null) }
    var progress by remember { mutableStateOf<DownloadProgress?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(menuOpen) {
        if (menuOpen) cacheInfo = withContext(Dispatchers.IO) { readTileCacheInfo(mapView) }
    }

    // Sync overlays with state.
    LaunchedEffect(ui.spot, ui.fix) {
        val spot = ui.spot
        val fix = ui.fix
        carMarker.isEnabled = spot != null
        if (spot != null) {
            carMarker.position = spot.point.toOsm()
            carMarker.snippet = spot.note.ifBlank { null }
        }
        line.isEnabled = spot != null && fix != null
        if (spot != null && fix != null) line.setPoints(listOf(fix.point.toOsm(), spot.point.toOsm()))
        if (!centeredOnce) {
            val target = spot?.point ?: fix?.point
            if (target != null) {
                mapView.controller.setCenter(target.toOsm())
                centeredOnce = true
            }
        }
        mapView.invalidate()
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
                    enabled = spot != null && online,
                    onClick = {
                        menuOpen = false
                        val plan = spot?.let { OfflineAreaPlanner.plan(it.point) }
                        if (plan == null) notice = "Can't plan an offline area here." else confirmPlan = plan
                    },
                )
                DropdownMenuItem(
                    text = { Text("Clear map cache") },
                    onClick = {
                        menuOpen = false
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { purgeTileCache(mapView) }
                            mapView.tileProvider.clearTileCache()
                            mapView.invalidate()
                            notice = if (ok) "Map cache cleared." else "Couldn't clear the map cache."
                        }
                    },
                )
                DropdownMenuItem(
                    text = { Text(cacheInfo?.label ?: "Cached map: …", style = MaterialTheme.typography.bodySmall) },
                    enabled = false,
                    onClick = {},
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
                    ui.fix?.let { mapView.controller.animateTo(it.point.toOsm()) }
                }) { Icon(Icons.Filled.MyLocation, "Center on me") }
            }
            ui.spot?.let { spot ->
                FloatingActionButton(onClick = { mapView.controller.animateTo(spot.point.toOsm()) }) {
                    Icon(Icons.Filled.DirectionsCar, "Center on car")
                }
                ExtendedFloatingActionButton(
                    onClick = { onGuide(spot.id) },
                    icon = { Icon(Icons.Filled.Explore, null) },
                    text = { Text("Guide") },
                )
            }
        }

        Text(
            MapTiles.ATTRIBUTION,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )

        confirmPlan?.let { plan ->
            AlertDialog(
                onDismissRequest = { confirmPlan = null },
                title = { Text("Save area for offline?") },
                text = {
                    Text(
                        "Downloads ${plan.tileCount} map tiles (zoom ${plan.minZoom}–${plan.maxZoom}) covering about " +
                            "1 km around your car, so the map works without signal.\n\n" +
                            "Map data ${MapTiles.ATTRIBUTION}. Saves are limited to ${OfflineAreaPlanner.MAX_TILES} " +
                            "tiles to respect the OpenStreetMap tile usage policy."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmPlan = null
                        progress = DownloadProgress(0, plan.tileCount)
                        val result = downloader.start(
                            context, plan,
                            onProgress = { done, total -> progress = DownloadProgress(done, total) },
                            onFinished = { failed ->
                                progress = null
                                notice = if (failed == 0) {
                                    "Area saved for offline use."
                                } else {
                                    "Saved, but $failed tiles failed to download."
                                }
                            },
                        )
                        if (result is OfflineAreaDownloader.StartResult.Refused) {
                            progress = null
                            notice = result.reason
                        }
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
                        val fraction = if (p.total > 0) (p.done.toFloat() / p.total).coerceIn(0f, 1f) else 0f
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("${p.done} / ${p.total} tiles")
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        downloader.cancel()
                        progress = null
                        notice = "Download cancelled. Tiles already saved stay cached."
                    }) { Text("Cancel") }
                },
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
            "Offline — showing cached map",
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
