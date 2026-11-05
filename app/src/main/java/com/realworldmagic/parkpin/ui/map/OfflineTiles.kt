package com.realworldmagic.parkpin.ui.map

import android.content.Context
import com.realworldmagic.parkpin.domain.OfflineAreaPlan
import com.realworldmagic.parkpin.domain.OfflineAreaPlanner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import java.io.File
import java.util.Locale

/** Tile source used for both map display and the small "save area for offline" download. */
object MapTiles {
    /**
     * The ONLY place the tile server URL lives.
     *
     * tile.openstreetmap.org is a donated community service. Its tile usage policy
     * (https://operations.osmfoundation.org/policies/tiles/) prohibits bulk downloading; it only
     * tolerates small offline saves (no more than 250 tiles at zoom 13+). This app therefore caps
     * every offline download at [OfflineAreaPlanner.MAX_TILES] tiles around the car, sends a
     * meaningful User-Agent, and always shows attribution.
     *
     * PRODUCTION: replace this URL with a tile provider whose terms explicitly allow offline
     * download (a self-hosted tile server, or a commercial provider with an offline-enabled plan)
     * and update [ATTRIBUTION] to match that provider's requirements.
     */
    const val TILE_SERVER_URL = "https://tile.openstreetmap.org/"

    const val ATTRIBUTION = "© OpenStreetMap contributors"

    /**
     * Named "Mapnik" so it shares osmdroid's cache with the stock OSM source. The policy omits
     * FLAG_NO_BULK only because downloads are hard-capped well inside the OSM policy limit.
     */
    val SOURCE: OnlineTileSourceBase = XYTileSource(
        "Mapnik", 0, 19, 256, ".png", arrayOf(TILE_SERVER_URL), ATTRIBUTION,
        TileSourcePolicy(
            2,
            TileSourcePolicy.FLAG_NO_PREVENTIVE or
                TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or
                TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED,
        ),
    )
}

/** Cached-tile usage for the map menu. */
data class TileCacheInfo(val tiles: Long, val bytes: Long) {
    val label: String
        get() = "Cached map: $tiles tiles · ${formatBytes(bytes)}"
}

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

/** Blocking: call off the main thread. */
internal fun readTileCacheInfo(mapView: MapView): TileCacheInfo {
    val writer = mapView.tileProvider.tileWriter as? SqlTileWriter
    val tiles = try {
        writer?.getRowCount(MapTiles.SOURCE.name()) ?: 0L
    } catch (e: Exception) {
        0L
    }
    val dir: File? = Configuration.getInstance().osmdroidTileCache
    val bytes = try {
        dir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    } catch (e: SecurityException) {
        0L
    }
    return TileCacheInfo(tiles, bytes)
}

/** Blocking: call off the main thread. Returns true when the on-disk tile cache was purged. */
internal fun purgeTileCache(mapView: MapView): Boolean {
    val writer = mapView.tileProvider.tileWriter as? SqlTileWriter ?: return false
    return try {
        writer.purgeCache()
    } catch (e: Exception) {
        false
    }
}

/**
 * Wraps osmdroid [CacheManager] for one capped download at a time. Callbacks arrive on the
 * main thread (CacheManager uses AsyncTask).
 */
internal class OfflineAreaDownloader(private val mapView: MapView) {
    private var manager: CacheManager? = null
    private var generation = 0

    sealed interface StartResult {
        data object Started : StartResult
        data class Refused(val reason: String) : StartResult
    }

    fun start(
        context: Context,
        plan: OfflineAreaPlan,
        onProgress: (done: Int, total: Int) -> Unit,
        onFinished: (failedTiles: Int) -> Unit,
    ): StartResult {
        cancel()
        val m = try {
            CacheManager(mapView)
        } catch (e: Exception) {
            return StartResult.Refused("This map source does not allow offline download.")
        }
        val b = plan.box
        val box = BoundingBox(b.north, b.east, b.south, b.west)
        // Belt and braces: re-check osmdroid's own count against the policy cap.
        val possible = m.possibleTilesInArea(box, plan.minZoom, plan.maxZoom)
        if (possible > OfflineAreaPlanner.MAX_TILES) {
            return StartResult.Refused("Area too large to save offline ($possible tiles).")
        }
        manager = m
        val myGeneration = ++generation
        var expected = possible
        m.downloadAreaAsyncNoUI(
            context, box, plan.minZoom, plan.maxZoom,
            object : CacheManager.CacheManagerCallback {
                override fun onTaskComplete() {
                    if (myGeneration == generation) onFinished(0)
                }

                override fun onTaskFailed(errors: Int) {
                    if (myGeneration == generation) onFinished(errors)
                }

                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                    if (myGeneration == generation) onProgress(progress, expected)
                }

                override fun downloadStarted() = Unit

                override fun setPossibleTilesInArea(total: Int) {
                    if (total > 0) expected = total
                }
            },
        )
        return StartResult.Started
    }

    /** Stops any running download; its callbacks are ignored afterwards. */
    fun cancel() {
        generation++
        manager?.cancelAllJobs()
        manager = null
    }
}
