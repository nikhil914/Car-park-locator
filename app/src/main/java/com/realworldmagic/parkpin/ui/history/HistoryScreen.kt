package com.realworldmagic.parkpin.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import com.realworldmagic.parkpin.domain.GeoMath
import com.realworldmagic.parkpin.domain.ParkingSpot
import com.realworldmagic.parkpin.ui.components.LocationPermissions
import com.realworldmagic.parkpin.ui.components.hasAnyPermission
import com.realworldmagic.parkpin.ui.util.formatDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onGuide: (Long) -> Unit) {
    val context = LocalContext.current
    val locationAllowed = remember { hasAnyPermission(context, LocationPermissions) }
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(locationAllowed))
    val ui by vm.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ParkingSpot?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("History") }) }) { padding ->
        if (ui.loaded && ui.spots.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.History, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(12.dp))
                Text("No parking history yet", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Spots you save appear here so you can find your way back again.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(ui.spots, key = { it.id }) { spot ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value != SwipeToDismissBoxValue.Settled) pendingDelete = spot
                            false // wait for confirmation dialog
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)
                                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.onErrorContainer) }
                        },
                    ) {
                        HistoryRow(
                            spot = spot,
                            distance = ui.fix?.let { GeoMath.distanceMeters(it.point, spot.point) },
                            metric = ui.settings.useMetric,
                            onClick = { onGuide(spot.id) },
                            onDelete = { pendingDelete = spot },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { spot ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this spot?") },
            text = { Text("Saved ${formatDateTime(spot.savedAtMs)}. This can't be undone.") },
            confirmButton = {
                Button(onClick = { vm.delete(spot); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryRow(
    spot: ParkingSpot,
    distance: Double?,
    metric: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Guide to this spot", onClick = onClick),
        leadingContent = {
            Icon(
                if (spot.isActive) Icons.Filled.DirectionsCar else Icons.Filled.Navigation,
                null,
                tint = if (spot.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text(formatDateTime(spot.savedAtMs)) },
        supportingContent = {
            val parts = listOfNotNull(
                spot.note.takeIf { it.isNotBlank() },
                spot.floorLabel?.takeIf { it.isNotBlank() }?.let { "Floor $it" },
                spot.address,
            )
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), maxLines = 2)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (spot.isActive) {
                    SuggestionChip(onClick = onClick, label = { Text("Active") })
                    Spacer(Modifier.width(4.dp))
                }
                distance?.let { Text(GeoMath.formatDistance(it, metric), style = MaterialTheme.typography.labelLarge) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete spot") }
            }
        },
    )
}
