package com.realworldmagic.parkpin.quicksave

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal object QuickSavePermissions {
    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Fine or coarse ("while in use"). Note: returns true even while the app is in the background. */
    fun hasLocation(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** "Allow all the time" (API 29+). Below 29 foreground location implies background access. */
    fun hasBackgroundLocation(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasLocation(context) && granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            hasLocation(context)
        }

    /** BLUETOOTH_CONNECT is a runtime permission from API 31; earlier the install-time BLUETOOTH suffices. */
    fun hasBluetoothConnect(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }
}
