package com.realworldmagic.parkpin.ui.spot

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.realworldmagic.parkpin.ui.components.MeterCountdownChip
import com.realworldmagic.parkpin.ui.util.formatTime
import com.realworldmagic.parkpin.ui.util.shareSpot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val MeterPresets = listOf(15, 30, 60, 120)

private fun newPhotoUri(context: Context, spotId: Long): Uri {
    val dir = File(context.filesDir, "photos").apply { mkdirs() }
    val file = File(dir, "spot_${spotId}_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun loadThumbnail(context: Context, uriString: String, maxPx: Int = 1200): Bitmap? = try {
    val uri = if (uriString.startsWith("/")) Uri.fromFile(File(uriString)) else Uri.parse(uriString)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > maxPx || bounds.outHeight / sample > maxPx) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
} catch (e: Exception) {
    null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpotEditScreen(spotId: Long, onBack: () -> Unit) {
    val vm: SpotEditViewModel = viewModel(key = "edit-$spotId", factory = SpotEditViewModel.factory(spotId))
    val ui by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(ui.saved) { if (ui.saved) onBack() }

    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) vm.setPhoto(pendingPhoto)
        pendingPhoto = null
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    var customDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spot details", modifier = Modifier.semantics { heading() }) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    ui.toSpot()?.let { spot ->
                        IconButton(onClick = { shareSpot(context, spot) }) { Icon(Icons.Filled.Share, "Share spot") }
                    }
                    IconButton(onClick = { vm.save() }, enabled = ui.original != null) { Icon(Icons.Filled.Check, "Save") }
                },
            )
        },
    ) { padding ->
        if (ui.loaded && ui.original == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), verticalArrangement = Arrangement.Center) {
                Text("This spot no longer exists.")
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = ui.note,
                onValueChange = vm::setNote,
                label = { Text("Note") },
                placeholder = { Text("e.g. Level P2, row C, near elevator") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = ui.floorLabel,
                onValueChange = vm::setFloor,
                label = { Text("Floor / level") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Photo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val photo = ui.photoUri
            if (photo != null) {
                var bitmap by remember(photo) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(photo) {
                    bitmap = withContext(Dispatchers.IO) { loadThumbnail(context, photo)?.asImageBitmap() }
                }
                bitmap?.let {
                    Image(
                        it,
                        contentDescription = "Photo of parking spot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)),
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                    val uri = newPhotoUri(context, ui.original?.id ?: 0L)
                    pendingPhoto = uri.toString()
                    takePicture.launch(uri)
                }) {
                    Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(6.dp))
                    Text(if (photo == null) "Take photo" else "Retake photo")
                }
                if (photo != null) {
                    OutlinedButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { vm.setPhoto(null) }) {
                        Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text("Remove photo")
                    }
                }
            }

            Text("Parking meter", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            ui.meterExpiresAtMs?.let { exp ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MeterCountdownChip(exp, modifier = Modifier.align(Alignment.CenterVertically))
                    Text(
                        "until ${formatTime(exp)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MeterPresets.forEach { min ->
                    FilterChip(
                        selected = false,
                        onClick = { vm.setMeterMinutes(min); askNotificationsIfNeeded() },
                        label = { Text(if (min < 60) "$min min" else "${min / 60} h") },
                        modifier = Modifier.semantics {
                            contentDescription = if (min < 60) "Set meter to $min minutes" else
                                "Set meter to ${min / 60} hour" + if (min / 60 == 1) "" else "s"
                        },
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { customDialog = true },
                    label = { Text("Custom") },
                    modifier = Modifier.semantics { contentDescription = "Set custom meter time" },
                )
                if (ui.meterExpiresAtMs != null) {
                    FilterChip(
                        selected = false,
                        onClick = vm::clearMeter,
                        label = { Text("No meter") },
                        modifier = Modifier.semantics { contentDescription = "Remove meter timer" },
                    )
                }
            }
            Text(
                "You'll be reminded ${ui.leadMinutes} min before it expires (change in Settings).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ui.meterExpiresAtMs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == false
            ) {
                // Without the exact-alarm permission the reminder may arrive up to ~10 min early.
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)),
                        )
                    }
                }) { Text("Allow on-time reminders") }
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = ui.original != null) {
                Text("Save")
            }
        }
    }

    if (customDialog) {
        var text by remember { mutableStateOf("") }
        val minutes = text.toIntOrNull()?.takeIf { it in 1..1440 }
        AlertDialog(
            onDismissRequest = { customDialog = false },
            title = { Text("Meter time", modifier = Modifier.semantics { heading() }) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Minutes (1–1440)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(enabled = minutes != null, onClick = {
                    minutes?.let { vm.setMeterMinutes(it); askNotificationsIfNeeded() }
                    customDialog = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { customDialog = false }) { Text("Cancel") } },
        )
    }
}
