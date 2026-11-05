package com.realworldmagic.parkpin.ui.components

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.realworldmagic.parkpin.ui.util.findActivity
import com.realworldmagic.parkpin.ui.util.openAppSettings

/**
 * Shows [content] once at least [requiredCount] (default: any for location-style groups when
 * [anyGranted] = true, else all) of [permissions] are granted; otherwise shows a rationale card
 * with a request button, or an "Open settings" button when the user permanently denied.
 */
@Composable
fun PermissionGate(
    permissions: List<String>,
    title: String,
    rationale: String,
    modifier: Modifier = Modifier,
    anyGranted: Boolean = false,
    icon: ImageVector = Icons.Filled.LocationOn,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    fun check(): Boolean {
        val results = permissions.map {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        return if (anyGranted) results.any { it } else results.all { it }
    }

    var granted by remember { mutableStateOf(check()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    // Re-check when returning from system settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = check()
                if (granted) permanentlyDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        askedOnce = true
        granted = check()
        if (!granted) {
            val activity = context.findActivity()
            val canAskAgain = activity != null && permissions.any {
                result[it] != true && ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
            permanentlyDenied = !canAskAgain
        }
    }

    if (granted) {
        content()
        return
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            rationale,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (permanentlyDenied && askedOnce) {
            Text(
                "Permission was denied. You can enable it in system settings.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { context.openAppSettings() }) { Text("Open settings") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { launcher.launch(permissions.toTypedArray()) }) { Text("Try again") }
        } else {
            Button(onClick = { launcher.launch(permissions.toTypedArray()) }) { Text("Grant permission") }
        }
    }
}

/** Convenience: true if any of the given permissions is granted right now. */
fun hasAnyPermission(context: android.content.Context, permissions: List<String>): Boolean =
    permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

val LocationPermissions = listOf(
    android.Manifest.permission.ACCESS_FINE_LOCATION,
    android.Manifest.permission.ACCESS_COARSE_LOCATION,
)
