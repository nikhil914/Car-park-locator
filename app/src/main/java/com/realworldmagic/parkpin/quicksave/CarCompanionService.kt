package com.realworldmagic.parkpin.quicksave

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * CompanionDeviceManager presence observer for the associated car head unit. The system binds
 * this service (raising our process priority) when the associated device appears/disappears.
 * From API 33 Bluetooth Classic connect/disconnect drives presence for associated devices; on
 * 31-32 presence is BLE-scan based and unreliable for car head units, so [CarAssociation] only
 * starts observing on 33+.
 *
 * Holding REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND exempts us from the API 31+
 * background FGS-start restriction. It does NOT grant while-in-use location; that still needs
 * ACCESS_BACKGROUND_LOCATION on API 34+ ([CarDisconnectHandler] handles this).
 *
 * API 36 deprecates these callbacks in favour of onDevicePresenceEvent(DevicePresenceEvent); with
 * compileSdk 35 we override the API 33 callbacks, which the platform still dispatches when
 * onDevicePresenceEvent is not overridden.
 */
@RequiresApi(Build.VERSION_CODES.S)
class CarCompanionService : CompanionDeviceService() {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        val stored = QuickSavePrefs.cdmAssociationId(this)
        val address = associationInfo.deviceMacAddress?.toString()
        if (associationInfo.id == stored || QuickSavePrefs.matchesCar(this, address)) {
            CarDisconnectHandler.onCarDisconnected(this, trigger = "cdm")
        }
    }

    @Deprecated("API 31-32 only; API 33+ uses the AssociationInfo overload")
    override fun onDeviceDisappeared(address: String) {
        if (QuickSavePrefs.matchesCar(this, address)) {
            CarDisconnectHandler.onCarDisconnected(this, trigger = "cdm")
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        // Car connected: nothing to do. Override so the base class does not need to forward.
    }

    @Deprecated("API 31-32 only")
    override fun onDeviceAppeared(address: String) {
    }
}
