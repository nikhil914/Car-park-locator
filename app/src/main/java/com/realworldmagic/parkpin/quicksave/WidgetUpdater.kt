package com.realworldmagic.parkpin.quicksave

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.realworldmagic.parkpin.ParkPinApp
import com.realworldmagic.parkpin.R
import com.realworldmagic.parkpin.domain.GeoMath
import com.realworldmagic.parkpin.domain.ParkingSpot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Refreshes every [ParkWidgetProvider] instance and the Quick Settings tile.
 * Call [refreshAll] (fire-and-forget) after any spot save/clear/delete, e.g. from the UI layer.
 */
object WidgetUpdater {
    private const val TAG = "WidgetUpdater"
    private const val RC_SAVE = 4601
    private const val RC_ROOT_SAVE = 4602

    /** Only show a distance if the cached last-known fix is at most this old. */
    private const val MAX_LAST_KNOWN_AGE_MS = 15 * 60_000L

    /** Fire-and-forget; safe from any thread. */
    fun refreshAll(context: Context) {
        val app = context.applicationContext
        ParkWidgetProvider.widgetScope.launch { refreshAllNow(app) }
    }

    suspend fun refreshAllNow(context: Context) {
        val app = context.applicationContext
        ParkTileService.requestRefresh(app)
        val mgr = AppWidgetManager.getInstance(app) ?: return
        val ids = mgr.getAppWidgetIds(ComponentName(app, ParkWidgetProvider::class.java))
        if (ids.isNotEmpty()) update(app, mgr, ids)
    }

    suspend fun update(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val views = try {
            build(context)
        } catch (e: Exception) {
            Log.w(TAG, "Widget build failed", e)
            emptyViews(context)
        }
        mgr.updateAppWidget(ids, views)
    }

    private suspend fun build(context: Context): RemoteViews {
        val container = (context.applicationContext as ParkPinApp).container
        val spot = container.parkingRepository.observeActiveSpot().first() ?: return emptyViews(context)
        val metric = try {
            container.settingsRepository.settings.first().useMetric
        } catch (e: Exception) {
            true
        }
        val distanceM = if (QuickSavePermissions.hasLocation(context)) {
            try {
                container.locationProvider.lastKnownFix()
                    ?.takeIf { System.currentTimeMillis() - it.timeMs <= MAX_LAST_KNOWN_AGE_MS }
                    ?.let { GeoMath.distanceMeters(it.point, spot.point) }
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        return spotViews(context, spot, distanceM, metric)
    }

    private fun baseViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.quicksave_widget)
        views.setOnClickPendingIntent(R.id.quicksave_widget_save, savePendingIntent(context, RC_SAVE))
        return views
    }

    private fun emptyViews(context: Context): RemoteViews = baseViews(context).apply {
        setTextViewText(R.id.quicksave_widget_status, context.getString(R.string.quicksave_widget_empty))
        setViewVisibility(R.id.quicksave_widget_guide, View.GONE)
        setOnClickPendingIntent(R.id.quicksave_widget_root, savePendingIntent(context, RC_ROOT_SAVE))
    }

    private fun spotViews(context: Context, spot: ParkingSpot, distanceM: Double?, metric: Boolean): RemoteViews =
        baseViews(context).apply {
            val time = QuickSaveTime.clock(context, spot.savedAtMs)
            val text = if (distanceM != null) {
                context.getString(R.string.quicksave_widget_distance_time, GeoMath.formatDistance(distanceM, metric), time)
            } else {
                context.getString(R.string.quicksave_widget_time, time)
            }
            setTextViewText(R.id.quicksave_widget_status, text)
            val guide = QuickSaveNotifications.openAppPendingIntent(context, spot.id)
            setViewVisibility(R.id.quicksave_widget_guide, View.VISIBLE)
            setOnClickPendingIntent(R.id.quicksave_widget_guide, guide)
            setOnClickPendingIntent(R.id.quicksave_widget_root, guide)
        }

    private fun savePendingIntent(context: Context, requestCode: Int): PendingIntent =
        PendingIntent.getForegroundService(
            context,
            requestCode,
            QuickSaveService.intent(context, QuickSaveService.SOURCE_WIDGET),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
