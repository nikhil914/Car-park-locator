package com.realworldmagic.parkpin.quicksave

import android.content.Context
import android.content.SharedPreferences

/**
 * Quick-save settings that live outside the main DataStore so this feature stays self-contained.
 * Auto-save on car Bluetooth disconnect is opt-in: it is enabled exactly when [carBtAddress] is set.
 */
object QuickSavePrefs {
    const val PREFS_NAME = "quicksave_prefs"
    const val KEY_CAR_BT_ADDRESS = "car_bt_address"
    private const val KEY_CAR_NAME = "car_bt_name"
    private const val KEY_CDM_ASSOCIATION_ID = "car_cdm_association_id"
    private const val KEY_LAST_AUTO_TRIGGER_MS = "last_auto_trigger_ms"

    /** Ignore a second disconnect trigger (ACL + CDM both fire, or flaky links) within this window. */
    const val AUTO_TRIGGER_DEBOUNCE_MS = 90_000L

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Upper-case MAC ("AA:BB:CC:DD:EE:FF") of the chosen car head unit, or null when auto-save is off. */
    fun carBtAddress(context: Context): String? =
        prefs(context).getString(KEY_CAR_BT_ADDRESS, null)?.takeIf { it.isNotBlank() }

    fun carName(context: Context): String? = prefs(context).getString(KEY_CAR_NAME, null)

    fun isAutoSaveEnabled(context: Context): Boolean = carBtAddress(context) != null

    fun setCar(context: Context, address: String, name: String?) {
        prefs(context).edit()
            .putString(KEY_CAR_BT_ADDRESS, address.uppercase())
            .putString(KEY_CAR_NAME, name)
            .apply()
    }

    fun clearCar(context: Context) {
        prefs(context).edit()
            .remove(KEY_CAR_BT_ADDRESS)
            .remove(KEY_CAR_NAME)
            .remove(KEY_CDM_ASSOCIATION_ID)
            .apply()
    }

    /** CompanionDeviceManager association id (API 33+), -1 when none. */
    fun cdmAssociationId(context: Context): Int = prefs(context).getInt(KEY_CDM_ASSOCIATION_ID, -1)

    fun setCdmAssociationId(context: Context, id: Int) {
        prefs(context).edit().putInt(KEY_CDM_ASSOCIATION_ID, id).apply()
    }

    fun matchesCar(context: Context, address: String?): Boolean {
        val stored = carBtAddress(context) ?: return false
        return address != null && address.equals(stored, ignoreCase = true)
    }

    /** Returns true (and records now) if an auto trigger may proceed; false if debounced. */
    @Synchronized
    fun tryClaimAutoTrigger(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(context)
        val last = p.getLong(KEY_LAST_AUTO_TRIGGER_MS, 0L)
        if (nowMs - last in 0 until AUTO_TRIGGER_DEBOUNCE_MS) return false
        p.edit().putLong(KEY_LAST_AUTO_TRIGGER_MS, nowMs).apply()
        return true
    }
}
