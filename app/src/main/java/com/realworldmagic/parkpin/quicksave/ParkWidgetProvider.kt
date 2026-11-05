package com.realworldmagic.parkpin.quicksave

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Classic RemoteViews home-screen widget: "Tap to save parking" or "Car: 230 m · saved 14:05",
 * with Save (-> QuickSaveService via PendingIntent.getForegroundService, which is exempt from both
 * the background-start and while-in-use restrictions because it is a widget interaction) and
 * Guide (-> MainActivity with extra spotId).
 */
class ParkWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        widgetScope.launch {
            try {
                // goAsync() gives ~10 s; keep well inside it.
                withTimeoutOrNull(8_000L) { WidgetUpdater.update(app, appWidgetManager, appWidgetIds) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        internal val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
