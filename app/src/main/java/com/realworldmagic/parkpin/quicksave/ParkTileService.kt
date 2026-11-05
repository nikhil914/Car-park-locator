package com.realworldmagic.parkpin.quicksave

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.realworldmagic.parkpin.MainActivity
import com.realworldmagic.parkpin.ParkPinApp
import com.realworldmagic.parkpin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile "Save parking". Subtitle (API 29+) shows "Saved 12 min ago" when an active
 * spot exists. Click saves a new spot.
 *
 * Android-version rules (see docs/QUICKSAVE.md):
 *  - Tile clicks put the app on a ~15 s temporary allowlist, so the *background-start* FGS
 *    restriction (API 31+) is lifted, but while-in-use (WIU) location eligibility is not
 *    documented for tiles. On API 34+ a location FGS created without WIU throws SecurityException;
 *    on API 30-33 it starts but gets no location. So on API 30+ we launch an invisible
 *    [QuickSaveTrampolineActivity] (a visible activity always grants WIU) via
 *    startActivityAndCollapse; below 30 we start the service directly.
 *  - API 34+ (targetSdk 34+): startActivityAndCollapse(Intent) throws
 *    UnsupportedOperationException, so the PendingIntent overload is used.
 */
class ParkTileService : TileService() {

    private var scope: CoroutineScope? = null
    private var refreshJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val s = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
        refreshJob?.cancel()
        refreshJob = s.launch { refreshTile() }
    }

    override fun onStopListening() {
        refreshJob?.cancel()
        super.onStopListening()
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private suspend fun refreshTile() {
        val tile = qsTile ?: return
        val spot = try {
            (applicationContext as ParkPinApp).container.parkingRepository.observeActiveSpot().first()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read active spot", e)
            null
        }
        tile.label = getString(R.string.quicksave_tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.quicksave_ic_car)
        tile.state = if (spot != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        val subtitle = if (spot != null) {
            QuickSaveTime.savedAgo(this, spot.savedAtMs)
        } else {
            getString(R.string.quicksave_tile_subtitle_none)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = subtitle
        tile.contentDescription = "${tile.label}. $subtitle"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        when {
            !QuickSavePermissions.hasLocation(this) -> {
                // Permission missing: open the app so it can request it (needs unlocked device).
                val open = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (isLocked) unlockAndRun { launchActivity(open, RC_OPEN_APP) } else launchActivity(open, RC_OPEN_APP)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                // Trampoline shows over the keyguard, so no unlock needed.
                launchActivity(QuickSaveTrampolineActivity.intent(this, QuickSaveService.SOURCE_TILE), RC_TRAMPOLINE)
            else -> {
                if (!QuickSaveService.start(this, QuickSaveService.SOURCE_TILE)) {
                    launchActivity(QuickSaveTrampolineActivity.intent(this, QuickSaveService.SOURCE_TILE), RC_TRAMPOLINE)
                }
            }
        }
    }

    // The Intent overload is only reached below API 34 (SDK_INT check), where it is the only option.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchActivity(intent: Intent, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        private const val TAG = "ParkTileService"
        private const val RC_TRAMPOLINE = 4501
        private const val RC_OPEN_APP = 4502

        /** Ask SystemUI to rebind so the subtitle refreshes (no-op if tile not added). */
        fun requestRefresh(context: Context) {
            try {
                TileService.requestListeningState(context, ComponentName(context, ParkTileService::class.java))
            } catch (e: Exception) {
                // IllegalArgumentException if the service is not declared/enabled; SecurityException on some OEMs.
                Log.d(TAG, "requestListeningState failed", e)
            }
        }
    }
}
