package com.realworldmagic.parkpin.quicksave

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.IntentCompat

/**
 * Manifest-registered receiver for [BluetoothDevice.ACTION_ACL_DISCONNECTED]. This action is on the
 * Android 8+ implicit-broadcast exemption list, so manifest receivers still get it on current
 * Android. On API 31+ the broadcast is only delivered to apps holding BLUETOOTH_CONNECT; we check
 * it again before touching the device. Opt-in: does nothing unless a car address is stored in
 * [QuickSavePrefs].
 */
class BluetoothCarDisconnectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
        val app = context.applicationContext
        if (!QuickSavePrefs.isAutoSaveEnabled(app)) return
        if (!QuickSavePermissions.hasBluetoothConnect(app)) {
            Log.d(TAG, "BLUETOOTH_CONNECT not granted; ignoring ACL disconnect")
            return
        }
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            ?: return
        val address = try {
            device.address
        } catch (e: SecurityException) {
            return
        }
        if (!QuickSavePrefs.matchesCar(app, address)) return
        CarDisconnectHandler.onCarDisconnected(app, trigger = "acl")
    }

    private companion object {
        const val TAG = "BtCarReceiver"
    }
}
