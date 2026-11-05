package com.realworldmagic.parkpin.location

import com.realworldmagic.parkpin.domain.LocationFix
import kotlinx.coroutines.flow.Flow

/**
 * Source of location fixes. Implementations never throw on missing permission:
 * [locationUpdates] simply completes without emitting, and the suspend functions return null.
 */
interface LocationProvider {
    fun locationUpdates(intervalMs: Long = 1000L): Flow<LocationFix>
    suspend fun currentFix(timeoutMs: Long = 15_000L): LocationFix?

    /** Cached last known fix (may be old). Null when unavailable or permission missing. */
    suspend fun lastKnownFix(): LocationFix? = null

    /** Human-readable implementation name, for diagnostics. */
    val name: String get() = javaClass.simpleName
}
