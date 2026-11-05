package com.realworldmagic.parkpin.ui.map

import android.content.Context
import com.realworldmagic.parkpin.domain.OfflineAreaPlan
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.util.Locale
import kotlin.coroutines.resume

/** Map style used for both display and offline regions. */
object MapStyle {
    /**
     * The ONLY place the map style URL lives.
     *
     * OpenFreeMap (https://openfreemap.org) is free, needs no API key, has no request limits and
     * explicitly allows offline / bulk use of its tiles (it also publishes full-planet downloads).
     * Data is OpenStreetMap (ODbL) in the OpenMapTiles schema, so attribution to both is required.
     * Their docs ask heavy users to self-host, so offline regions stay small (~1 km around the car).
     *
     * To change provider, swap this URL for any MapLibre style whose terms allow offline download
     * and update [ATTRIBUTION]. Never point this at tile.openstreetmap.org: the OSM tile usage
     * policy forbids offline prefetch.
     */
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    const val ATTRIBUTION = "OpenFreeMap © OpenMapTiles © OpenStreetMap contributors"
}

/** One downloaded offline area, as listed in the map menu. */
data class SavedRegion(
    val id: Long,
    val name: String,
    val createdAtMs: Long,
    val sizeBytes: Long,
    val complete: Boolean,
    internal val region: OfflineRegion,
)

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

private const val META_NAME = "name"
private const val META_CREATED = "createdAt"

private fun encodeMetadata(name: String, createdAtMs: Long): ByteArray =
    JSONObject().put(META_NAME, name).put(META_CREATED, createdAtMs).toString().toByteArray(Charsets.UTF_8)

private fun decodeName(metadata: ByteArray): Pair<String, Long> = try {
    val json = JSONObject(String(metadata, Charsets.UTF_8))
    json.optString(META_NAME, "Offline area") to json.optLong(META_CREATED, 0L)
} catch (e: Exception) {
    "Offline area" to 0L
}

/**
 * Thin wrapper around MapLibre [OfflineManager]. All MapLibre callbacks arrive on the main thread,
 * so callers must use these from the main thread too.
 */
internal class OfflineRegions(context: Context) {
    private val appContext = context.applicationContext
    private val manager: OfflineManager get() = OfflineManager.getInstance(appContext)

    private var active: OfflineRegion? = null
    private var generation = 0

    /** Lists saved regions with their on-disk size. */
    suspend fun list(): List<SavedRegion> {
        val regions = suspendCancellableCoroutine<Array<OfflineRegion>> { cont ->
            manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    if (cont.isActive) cont.resume(offlineRegions ?: emptyArray())
                }

                override fun onError(error: String) {
                    if (cont.isActive) cont.resume(emptyArray())
                }
            })
        }
        return regions.map { r ->
            val status = status(r)
            val (name, created) = decodeName(r.metadata)
            SavedRegion(
                id = r.id,
                name = name,
                createdAtMs = created,
                sizeBytes = status?.completedResourceSize ?: 0L,
                complete = status?.isComplete == true,
                region = r,
            )
        }.sortedByDescending { it.createdAtMs }
    }

    private suspend fun status(region: OfflineRegion): OfflineRegionStatus? =
        suspendCancellableCoroutine { cont ->
            region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                override fun onStatus(status: OfflineRegionStatus?) {
                    if (cont.isActive) cont.resume(status)
                }

                override fun onError(error: String?) {
                    if (cont.isActive) cont.resume(null)
                }
            })
        }

    /** Deletes [region]. Resources shared with other regions stay on disk. */
    suspend fun delete(region: SavedRegion): Boolean = delete(region.region)

    private suspend fun delete(region: OfflineRegion): Boolean = suspendCancellableCoroutine { cont ->
        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        region.setObserver(null)
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                if (cont.isActive) cont.resume(true)
            }

            override fun onError(error: String) {
                if (cont.isActive) cont.resume(false)
            }
        })
    }

    /**
     * Starts downloading [plan] for [MapStyle.STYLE_URL]. [onProgress] reports completed and
     * required resources (style, sprites, glyphs and tiles); [onFinished] reports success or an
     * error message. Only one download runs at a time; starting a new one cancels the previous.
     */
    fun start(
        name: String,
        plan: OfflineAreaPlan,
        pixelRatio: Float,
        onProgress: (done: Long, total: Long) -> Unit,
        onFinished: (error: String?) -> Unit,
    ) {
        cancelActive(deleteRegion = true)
        val myGeneration = ++generation
        val b = plan.box
        val definition = OfflineTilePyramidRegionDefinition(
            MapStyle.STYLE_URL,
            LatLngBounds.from(b.north, b.east, b.south, b.west),
            plan.minZoom.toDouble(),
            plan.maxZoom.toDouble(),
            pixelRatio,
        )
        manager.createOfflineRegion(
            definition,
            encodeMetadata(name, System.currentTimeMillis()),
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    if (myGeneration != generation) {
                        // Cancelled before the region existed: clean it up.
                        offlineRegion.delete(NoopDelete)
                        return
                    }
                    active = offlineRegion
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            if (myGeneration != generation) return
                            onProgress(status.completedResourceCount, status.requiredResourceCount)
                            if (status.isComplete) {
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                offlineRegion.setObserver(null)
                                active = null
                                onFinished(null)
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            // Transient errors are retried by MapLibre; only surface hard failures.
                            if (myGeneration != generation) return
                            if (error.reason == OfflineRegionError.REASON_NOT_FOUND ||
                                error.reason == OfflineRegionError.REASON_OTHER
                            ) {
                                cancelActive(deleteRegion = false)
                                onFinished(error.message)
                            }
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            if (myGeneration != generation) return
                            cancelActive(deleteRegion = true)
                            onFinished("Area too large to save offline (limit $limit tiles).")
                        }
                    })
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {
                    if (myGeneration == generation) onFinished(error)
                }
            },
        )
    }

    /** Stops the running download and deletes its partial region. Callbacks are ignored afterwards. */
    fun cancel() = cancelActive(deleteRegion = true)

    private fun cancelActive(deleteRegion: Boolean) {
        generation++
        active?.let { r ->
            r.setDownloadState(OfflineRegion.STATE_INACTIVE)
            r.setObserver(null)
            if (deleteRegion) r.delete(NoopDelete)
        }
        active = null
    }

    private object NoopDelete : OfflineRegion.OfflineRegionDeleteCallback {
        override fun onDelete() = Unit
        override fun onError(error: String) = Unit
    }
}
