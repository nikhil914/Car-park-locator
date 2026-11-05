package com.realworldmagic.parkpin.location

import com.realworldmagic.parkpin.domain.AccuracyFilter
import com.realworldmagic.parkpin.domain.FixFreshness
import com.realworldmagic.parkpin.domain.LocationFix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Result of [captureBestFix].
 * @param approximate true when the fix came from the last-known cache or is weaker than [APPROXIMATE_ACCURACY_M].
 * @param samples number of usable live fixes collected.
 */
data class CaptureResult(
    val fix: LocationFix?,
    val approximate: Boolean,
    val samples: Int = 0,
    val fromLastKnown: Boolean = false,
) {
    /** The chosen fix was flagged as coming from a mock location provider. */
    val isMock: Boolean get() = fix?.isMock == true

    companion object {
        const val APPROXIMATE_ACCURACY_M = 30f
    }
}

/**
 * Save-spot algorithm: collect live high-accuracy fixes for up to [maxWaitMs], stopping early as
 * soon as one is at or below [targetAccuracyM]; pick the best (outliers rejected). If none arrive,
 * fall back to the last known fix (if not older than [maxLastKnownAgeMs]) flagged approximate.
 *
 * Unusable fixes (NaN / null-island coordinates, bad accuracy) are ignored, so they can neither
 * be saved nor end the capture early. Stale cached replays in live updates are dropped by the
 * providers on the monotonic clock; the last-known fallback is age-checked here against [nowMs]
 * (wall clock, injectable for tests). Never throws on missing permission.
 */
suspend fun captureBestFix(
    provider: LocationProvider,
    maxWaitMs: Long = 15_000L,
    targetAccuracyM: Float = 10f,
    maxLastKnownAgeMs: Long = FixFreshness.MAX_LAST_KNOWN_AGE_MS,
    nowMs: () -> Long = System::currentTimeMillis,
): CaptureResult {
    val fixes = mutableListOf<LocationFix>()
    try {
        withTimeoutOrNull(maxWaitMs) {
            provider.locationUpdates(500L)
                .filter { FixFreshness.isUsable(it) }
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
    }?.takeIf { FixFreshness.isUsable(it) && FixFreshness.isFresh(it, nowMs(), maxLastKnownAgeMs) }
    return CaptureResult(last, approximate = true, samples = 0, fromLastKnown = last != null)
}
