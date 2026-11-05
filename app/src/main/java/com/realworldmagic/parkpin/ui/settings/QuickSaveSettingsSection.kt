package com.realworldmagic.parkpin.ui.settings

import android.Manifest
import android.app.StatusBarManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.realworldmagic.parkpin.R
import com.realworldmagic.parkpin.quicksave.CarAssociation
import com.realworldmagic.parkpin.quicksave.CarCandidate
import com.realworldmagic.parkpin.quicksave.ParkTileService
import com.realworldmagic.parkpin.quicksave.ParkWidgetProvider
import com.realworldmagic.parkpin.quicksave.QuickSavePrefs

private data class ChosenCar(val name: String, val address: String)

private fun readChosenCar(context: Context): ChosenCar? {
    val address = QuickSavePrefs.carBtAddress(context) ?: return null
    return ChosenCar(QuickSavePrefs.carName(context) ?: address, address)
}

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun hasBluetoothConnect(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(context, Manifest.permission.BLUETOOTH_CONNECT)

private fun hasNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(context, Manifest.permission.POST_NOTIFICATIONS)

/**
 * Settings section for quick save: Quick Settings tile, home-screen widget and the opt-in car
 * Bluetooth auto-save. Background location is intentionally never requested, so a car disconnect
 * posts a one-tap "Save parking" notification instead of saving silently.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickSaveSettingsSection(showMessage: (String) -> Unit) {
    val context = LocalContext.current
    var car by remember { mutableStateOf(readChosenCar(context)) }
    var btGranted by remember { mutableStateOf(hasBluetoothConnect(context)) }
    var notifGranted by remember { mutableStateOf(hasNotifications(context)) }
    var candidates by remember { mutableStateOf<List<CarCandidate>?>(null) }

    // Permissions can change in system settings while we are in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                car = readChosenCar(context)
                btGranted = hasBluetoothConnect(context)
                notifGranted = hasNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openPicker() {
        val devices = CarAssociation.bondedDevices(context)
        if (devices.isEmpty()) {
            showMessage("No paired Bluetooth devices found. Pair your car in Android Bluetooth settings first.")
        } else {
            candidates = devices
        }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok ->
        btGranted = ok
        if (ok) {
            if (car == null) openPicker()
        } else {
            showMessage("Nearby devices permission is needed to detect your car disconnecting.")
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok -> notifGranted = ok }

    val chooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { /* Result arrives through the CompanionDeviceManager callback (onCreated / onError). */ }

    fun pickFromPaired() {
        if (hasBluetoothConnect(context)) {
            openPicker()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    fun pairViaChooser() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        CarAssociation.requestCompanionAssociation(
            context,
            onPending = { sender ->
                try {
                    chooserLauncher.launch(IntentSenderRequest.Builder(sender).build())
                } catch (e: Exception) {
                    showMessage("Couldn't open the device chooser.")
                }
            },
            onCreated = { chosen ->
                car = ChosenCar(chosen.name, chosen.address)
                showMessage("Auto-save on for ${chosen.name}")
                // The ACL disconnect receiver still needs Nearby devices on API 31+.
                if (!hasBluetoothConnect(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            },
            onError = { error -> showMessage("Pairing failed${error?.let { ": $it" } ?: ""}") },
        )
    }

    SectionHeader("Quick save")

    ListItem(
        leadingContent = { Icon(Icons.Filled.TouchApp, null) },
        headlineContent = { Text("Quick Settings tile") },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Swipe down from the top of the screen and tap \"Save parking\" to save your spot " +
                        "without opening the app, even from the lock screen.",
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    FilledTonalButton(onClick = { requestAddTile(context, showMessage) }) {
                        Text("Add tile to Quick Settings")
                    }
                } else {
                    Text(
                        "To add it, open Quick Settings, tap the edit (pencil) button and drag the tile in.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )

    ListItem(
        leadingContent = { Icon(Icons.Filled.Widgets, null) },
        headlineContent = { Text("Home-screen widget") },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "One-tap Save button that also shows when your car was saved and how far away it is. " +
                        "Long-press your home screen, choose Widgets and find ParkPin.",
                )
                if (canPinWidget(context)) {
                    OutlinedButton(onClick = { requestPinWidget(context, showMessage) }) {
                        Text("Add widget")
                    }
                }
            }
        },
    )

    ListItem(
        leadingContent = { Icon(Icons.Filled.Bluetooth, null) },
        headlineContent = { Text("Car Bluetooth auto-save") },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val current = car
                if (current == null) {
                    Text("Off. Pick your car's Bluetooth and ParkPin reacts when it disconnects.")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        FilledTonalButton(onClick = { pickFromPaired() }) { Text("Choose paired device") }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            OutlinedButton(onClick = { pairViaChooser() }) { Text("Pair via system chooser") }
                        }
                    }
                } else {
                    Text("On for ${current.name} (${current.address})", style = MaterialTheme.typography.bodyMedium)
                    if (!btGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Text(
                            "Nearby devices permission is off, so disconnects may be missed.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                            Text("Allow Nearby devices")
                        }
                    }
                    OutlinedButton(onClick = {
                        CarAssociation.clearCar(context)
                        car = null
                        showMessage("Car forgotten. Auto-save is off.")
                    }) { Text("Forget car") }
                }
                if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Text(
                        "Notifications are off, so the \"Save parking\" prompt can't appear.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("Allow notifications")
                    }
                }
            }
        },
    )

    ListItem(
        leadingContent = { Icon(Icons.Filled.Info, null) },
        headlineContent = { Text("How car auto-save works") },
        supportingContent = {
            Text(
                "ParkPin does not ask for \"Allow all the time\" location. When your car disconnects, " +
                    "you get a one-tap \"Save parking\" notification; tapping it saves the spot where you are. " +
                    "Nothing is tracked in the background.",
            )
        },
    )

    candidates?.let { list ->
        AlertDialog(
            onDismissRequest = { candidates = null },
            icon = { Icon(Icons.Filled.DirectionsCar, null) },
            title = { Text("Choose your car") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list, key = { it.address }) { candidate ->
                        ListItem(
                            modifier = Modifier.padding(vertical = 2.dp),
                            headlineContent = { Text(candidate.name) },
                            supportingContent = { Text(candidate.address) },
                            trailingContent = {
                                TextButton(onClick = {
                                    CarAssociation.chooseCar(context, candidate)
                                    car = ChosenCar(candidate.name, candidate.address)
                                    candidates = null
                                    showMessage("Auto-save on for ${candidate.name}")
                                }) { Text("Use") }
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { candidates = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestAddTile(context: Context, showMessage: (String) -> Unit) {
    val sbm = context.getSystemService(StatusBarManager::class.java)
    if (sbm == null) {
        showMessage("Quick Settings is not available on this device.")
        return
    }
    try {
        sbm.requestAddTileService(
            ComponentName(context, ParkTileService::class.java),
            context.getString(R.string.quicksave_tile_label),
            Icon.createWithResource(context, R.drawable.quicksave_ic_car),
            ContextCompat.getMainExecutor(context),
        ) { result ->
            when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> showMessage("Tile added to Quick Settings")
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> showMessage("Tile is already in Quick Settings")
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> Unit
                else -> showMessage("Couldn't add the tile. Add it from the Quick Settings edit screen.")
            }
        }
    } catch (e: Exception) {
        showMessage("Couldn't add the tile. Add it from the Quick Settings edit screen.")
    }
}

private fun canPinWidget(context: Context): Boolean = try {
    AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true
} catch (e: Exception) {
    false
}

private fun requestPinWidget(context: Context, showMessage: (String) -> Unit) {
    val ok = try {
        AppWidgetManager.getInstance(context)
            ?.requestPinAppWidget(ComponentName(context, ParkWidgetProvider::class.java), null, null) == true
    } catch (e: Exception) {
        false
    }
    if (!ok) showMessage("Your launcher can't add widgets from here. Long-press the home screen instead.")
}
