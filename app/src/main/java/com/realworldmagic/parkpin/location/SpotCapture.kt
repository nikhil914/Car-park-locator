package com.realworldmagic.parkpin.location

import com.realworldmagic.parkpin.domain.AccuracyFilter
import com.realworldmagic.parkpin.domain.LocationFix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Result of [captureBestFix].
 * @param approximate true when the fix came from the last-known cache or is weaker than [APPROXIMATE_ACCURACY_M].
 * @param samples number of live fixes collected.
 */
data class CaptureResult(
    val fix: LocationFix?,
    val approximate: Boolean,
    val samples: Int = 0,
    val fromLastKnown: Boolean = false,
) {
    companion object {
        const val APPROXIMATE_ACCURACY_M = 30f
    }
}

/**
 * Save-spot algorithm: collect live high-accuracy fixes for up to [maxWaitMs], stopping early as
 * soon as one is at or below [targetAccuracyM]; pick the best (outliers rejected). If none arrive,
 * fall back to the last known fix flagged approximate. Never throws on missing permission.
 */
suspend fun captureBestFix(
    provider: LocationProvider,
    maxWaitMs: Long = 15_000L,
    targetAccuracyM: Float = 10f,
): CaptureResult {
    val fixes = mutableListOf<LocationFix>()
    try {
        withTimeoutOrNull(maxWaitMs) {
            provider.locationUpdates(500L)
                .onEach { fixes += it }
                .first { it.accuracyM <= targetAccuracyM }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: NoSuchElementException) {
        // Flow completed (e.g. permission missing) without a good fix.
    } catch (e: SecurityException) {
        // Permission revoked mid-capture.
    }

    val best = AccuracyFilter.selectBest(fixes)
    if (best != null) {
        return CaptureResult(best, best.accuracyM > CaptureResult.APPROXIMATE_ACCURACY_M, fixes.size)
    }
    val last = try {
        provider.lastKnownFix()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
    return CaptureResult(last, approximate = true, samples = 0, fromLastKnown = last != null)
}
