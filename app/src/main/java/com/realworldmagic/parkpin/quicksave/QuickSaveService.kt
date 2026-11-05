package com.realworldmagic.parkpin.quicksave

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Short-lived foreground service (type `location`) that captures the best fix, saves a new active
 * [com.realworldmagic.parkpin.domain.ParkingSpot], posts "Parking saved · ±8 m" with a "Guide me"
 * action, refreshes widgets/tile and stops itself.
 *
 * Manifest must declare `android:foregroundServiceType="location|shortService"`: `shortService`
 * (API 34+) is the fallback type used when the location type is refused (no permission, or started
 * from the background without while-in-use eligibility), so we can still satisfy the
 * startForegroundService() -> startForeground() contract, explain the problem, and stop cleanly.
 */
class QuickSaveService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    @Volatile
    private var latestStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val source = intent?.getStringExtra(EXTRA_SOURCE) ?: SOURCE_UNKNOWN
        QuickSaveNotifications.ensureChannel(this)
        latestStartId = startId

        if (job?.isActive == true) {
            // A capture is already running (repeat tap). Every startForegroundService() call must be
            // answered with startForeground(), so re-post the progress notification, but do not stop
            // the service here: that would remove the foreground state of the running capture.
            if (!enterForegroundLocation()) enterForegroundFallback()
            return START_NOT_STICKY
        }

        if (!QuickSavePermissions.hasLocation(this)) {
            enterForegroundFallback()
            QuickSaveNotifications.postPermissionNeeded(this)
            finish(startId)
            return START_NOT_STICKY
        }

        if (!enterForegroundLocation()) {
            // API 34+: SecurityException because the app is not eligible for while-in-use location
            // right now (started from background without an exemption). Ask the user to tap.
            enterForegroundFallback()
            QuickSaveNotifications.postTapToSave(this, carDisconnected = source == SOURCE_CAR_BLUETOOTH)
            finish(startId)
            return START_NOT_STICKY
        }

        job = scope.launch {
            val outcome = try {
                QuickSaveCore.captureAndSave(this@QuickSaveService)
            } catch (e: SecurityException) {
                Log.w(TAG, "Location access denied during capture", e)
                if (source == SOURCE_CAR_BLUETOOTH) null else QuickSaveOutcome.NoPermission
            } catch (e: Exception) {
                Log.e(TAG, "Quick save failed", e)
                QuickSaveOutcome.NoFix
            }
            if (outcome == null) {
                QuickSaveNotifications.postTapToSave(this@QuickSaveService, carDisconnected = true)
            } else {
                QuickSaveCore.publish(this@QuickSaveService, outcome)
            }
            // Use the newest start id: repeat taps during the capture bump it, and stopSelf(oldId)
            // would leave the service running.
            finish(latestStartId)
        }
        return START_NOT_STICKY
    }

    /** Returns false when the platform refuses the location FGS type. */
    private fun enterForegroundLocation(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            QuickSaveNotifications.ID_PROGRESS,
            QuickSaveNotifications.progress(this),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "Location FGS refused", e)
        false
    }

    /**
     * Enter foreground with a type that needs no runtime permission, so the
     * startForegroundService() contract is met before we stop. On API < 34 the platform does not
     * validate type permissions, so `location` is fine there.
     */
    private fun enterForegroundFallback() {
        val type = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            else -> 0
        }
        try {
            ServiceCompat.startForeground(this, QuickSaveNotifications.ID_PROGRESS, QuickSaveNotifications.progress(this), type)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback startForeground failed", e)
        }
    }

    private fun finish(startId: Int) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    /** API 34 shortService timeout (only reached if a fallback path somehow lingers). */
    override fun onTimeout(startId: Int) {
        finish(startId)
    }

    /** API 35 variant: also invoked for time-limited types; just stop. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        finish(startId)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "QuickSaveService"
        const val EXTRA_SOURCE = "quicksave_source"
        const val SOURCE_TILE = "tile"
        const val SOURCE_WIDGET = "widget"
        const val SOURCE_NOTIFICATION = "notification"
        const val SOURCE_CAR_BLUETOOTH = "car_bluetooth"
        const val SOURCE_APP = "app"
        const val SOURCE_UNKNOWN = "unknown"

        fun intent(context: Context, source: String): Intent =
            Intent(context, QuickSaveService::class.java).putExtra(EXTRA_SOURCE, source)

        /**
         * Starts a quick save. Only call while the app is in a state allowed to start a location
         * FGS (visible activity, widget/notification PendingIntent, tile click temp-allowlist).
         * Returns false if the platform refused the start (ForegroundServiceStartNotAllowedException
         * on API 31+, or IllegalStateException on 26-30 when the app is background-restricted).
         */
        fun start(context: Context, source: String = SOURCE_APP): Boolean = try {
            ContextCompat.startForegroundService(context, intent(context, source))
            true
        } catch (e: IllegalStateException) { // ForegroundServiceStartNotAllowedException extends it
            Log.w(TAG, "startForegroundService refused (source=$source)", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "startForegroundService security error (source=$source)", e)
            false
        }
    }
}
