package com.realworldmagic.parkpin.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private val LeadOptions = listOf(5, 10, 15, 30)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                leadingContent = { Icon(Icons.Filled.Straighten, null) },
                headlineContent = { Text("Metric units") },
                supportingContent = { Text(if (s.useMetric) "Meters / kilometers" else "Feet / miles") },
                trailingContent = { Switch(checked = s.useMetric, onCheckedChange = vm::setUseMetric) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Notifications, null) },
                headlineContent = { Text("Meter reminder") },
                supportingContent = {
                    Column {
                        Text("Notify ${s.meterReminderLeadMinutes} min before the meter expires")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LeadOptions.forEach { m ->
                                FilterChip(
                                    selected = s.meterReminderLeadMinutes == m,
                                    onClick = { vm.setLeadMinutes(m) },
                                    label = { Text("$m min") },
                                )
                            }
                        }
                    }
                },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.ViewInAr, null) },
                headlineContent = { Text("AR guide") },
                supportingContent = { Text("Show the camera-based AR view") },
                trailingContent = { Switch(checked = s.arEnabled, onCheckedChange = vm::setArEnabled) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.WbSunny, null) },
                headlineContent = { Text("Keep screen on while guiding") },
                trailingContent = { Switch(checked = s.keepScreenOn, onCheckedChange = vm::setKeepScreenOn) },
            )
            HorizontalDivider()
            ListItem(
                leadingContent = { Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                headlineContent = { Text("Delete all history") },
                supportingContent = { Text("Removes every saved spot, including the active one") },
                trailingContent = {
                    Button(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete") }
                },
            )
            HorizontalDivider()
            ListItem(
                leadingContent = { Icon(Icons.Filled.Lock, null) },
                headlineContent = { Text("Privacy") },
                supportingContent = {
                    Text("Your data never leaves this device. No accounts, no analytics. GPS, compass and the saved spots stay on your phone.")
                },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Map, null) },
                headlineContent = { Text("Map data") },
                supportingContent = {
                    Text("Map tiles © OpenStreetMap contributors, available under the Open Database License (openstreetmap.org/copyright). Tiles viewed online are cached for offline use.")
                },
            )
            ListItem(
                headlineContent = { Text("About ParkPin") },
                supportingContent = {
                    Text("Where Did I Park? An offline car locator. Part of the Real-World Magic AR Toolbox.")
                },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all history?") },
            text = { Text("All saved parking spots, notes and meter reminders will be removed. This can't be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        vm.deleteAllHistory { n -> scope.launch { snackbar.showSnackbar("Deleted $n spot(s)") } }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
