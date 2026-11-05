package com.realworldmagic.parkpin.quicksave

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/** A paired Bluetooth device the user may pick as "my car". */
data class CarCandidate(val name: String, val address: String)

/**
 * Helpers for choosing the car Bluetooth device (opt-in auto-save). UI (settings screen) calls these.
 *
 * Option A (all API levels, simple): [bondedDevices] -> user picks -> [chooseCar]. Works through
 *   [BluetoothCarDisconnectReceiver] (ACL broadcast).
 * Option B (API 33+, more robust): [requestCompanionAssociation] shows the system CDM chooser.
 *   Once associated we observe presence via [CarCompanionService], which also exempts us from
 *   the background FGS-start restriction.
 */
object CarAssociation {
    private const val TAG = "CarAssociation"

    /** Paired devices, sorted by name. Empty when BLUETOOTH_CONNECT (API 31+) is missing or BT is unavailable. */
    @SuppressLint("MissingPermission") // checked via QuickSavePermissions.hasBluetoothConnect
    fun bondedDevices(context: Context): List<CarCandidate> {
        if (!QuickSavePermissions.hasBluetoothConnect(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty()
                .map { CarCandidate(it.name ?: it.address, it.address.uppercase()) }
                .sortedBy { it.name.lowercase() }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** Opt in with a device picked from [bondedDevices]. */
    fun chooseCar(context: Context, candidate: CarCandidate) {
        // Switching cars: drop the previous car's CDM association/presence observation, otherwise
        // its stored association id keeps matching in CarCompanionService and the old car would
        // still trigger auto-save.
        if (!QuickSavePrefs.matchesCar(context, candidate.address)) clearCar(context)
        QuickSavePrefs.setCar(context, candidate.address, candidate.name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // If an existing CDM association matches, start observing it too.
            companionManager(context)?.let { cdm ->
                try {
                    cdm.myAssociations.firstOrNull {
                        it.deviceMacAddress?.toString().equals(candidate.address, ignoreCase = true)
                    }?.let { startObserving(context, cdm, it) }
                } catch (e: Exception) {
                    Log.w(TAG, "CDM lookup failed", e)
                }
            }
        }
    }

    /** Opt out: forget the car, stop presence observation and drop the CDM association. */
    fun clearCar(context: Context) {
        val address = QuickSavePrefs.carBtAddress(context)
        val associationId = QuickSavePrefs.cdmAssociationId(context)
        QuickSavePrefs.clearCar(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val cdm = companionManager(context) ?: return
            if (address != null) {
                try {
                    @Suppress("DEPRECATION")
                    cdm.stopObservingDevicePresence(address)
                } catch (e: Exception) {
                    Log.d(TAG, "stopObservingDevicePresence failed", e)
                }
            }
            try {
                if (associationId >= 0) cdm.disassociate(associationId)
            } catch (e: Exception) {
                Log.d(TAG, "disassociate failed", e)
            }
        }
    }

    /**
     * Starts the CompanionDeviceManager chooser (API 33+). Launch [onPending]'s IntentSender with
     * ActivityResultContracts.StartIntentSenderForResult from the UI; the result is delivered to
     * [onCreated] via the CDM callback ([onError] on failure), so the activity result itself can be ignored.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun requestCompanionAssociation(
        context: Context,
        onPending: (IntentSender) -> Unit,
        onCreated: (CarCandidate) -> Unit,
        onError: (CharSequence?) -> Unit,
    ) {
        val cdm = companionManager(context)
        if (cdm == null) {
            onError("Companion device manager unavailable")
            return
        }
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().build())
            .setSingleDevice(false)
            .build()
        val app = context.applicationContext
        cdm.associate(request, ContextCompat.getMainExecutor(app), object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) = onPending(intentSender)

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                val address = associationInfo.deviceMacAddress?.toString()?.uppercase()
                if (address == null) {
                    onError("Association has no MAC address")
                    return
                }
                val name = associationInfo.displayName?.toString() ?: address
                val previousId = QuickSavePrefs.cdmAssociationId(app)
                if (previousId != associationInfo.id &&
                    (previousId >= 0 || !QuickSavePrefs.matchesCar(app, address))
                ) {
                    // Replacing another car: release its association first (see chooseCar).
                    clearCar(app)
                }
                QuickSavePrefs.setCar(app, address, name)
                QuickSavePrefs.setCdmAssociationId(app, associationInfo.id)
                startObserving(app, cdm, associationInfo)
                onCreated(CarCandidate(name, address))
            }

            override fun onFailure(error: CharSequence?) {
                onError(error)
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun startObserving(context: Context, cdm: CompanionDeviceManager, info: AssociationInfo) {
        val address = info.deviceMacAddress?.toString() ?: return
        QuickSavePrefs.setCdmAssociationId(context, info.id)
        try {
            // Deprecated in API 36 (ObservingDevicePresenceRequest); still functional. Requires
            // REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE (normal permission).
            @Suppress("DEPRECATION")
            cdm.startObservingDevicePresence(address)
        } catch (e: Exception) {
            Log.w(TAG, "startObservingDevicePresence failed", e)
        }
    }

    private fun companionManager(context: Context): CompanionDeviceManager? =
        context.getSystemService(CompanionDeviceManager::class.java)
}
