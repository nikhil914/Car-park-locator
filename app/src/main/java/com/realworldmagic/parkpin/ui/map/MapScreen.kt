package com.realworldmagic.parkpin.ui.map

import android.content.Context
import android.net.ConnectivityManager
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
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.realworldmagic.parkpin.ui.components.LocationPermissions
import com.realworldmagic.parkpin.ui.components.hasAnyPermission
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
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
            setTileSource(TileSourceFactory.MAPNIK)
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

        // Info bubble.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(12.dp)
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
            "© OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}
