package com.realworldmagic.parkpin.domain

/** Staleness / sanity checks for location fixes (wall-clock based, tolerant of small clock skew). */
object FixFreshness {
    /** Live fixes older than this are cached/historical deliveries, not the current position. */
    const val MAX_LIVE_AGE_MS = 120_000L

    /** Oldest last-known fix still acceptable as an "approximate" spot when no live fix arrives. */
    const val MAX_LAST_KNOWN_AGE_MS = 10 * 60_000L

    /** Fixes stamped further than this in the future are treated as bogus (bad GPS/device time). */
    const val MAX_FUTURE_SKEW_MS = 60_000L

    /** Age of [fix] at [nowMs] in ms (negative if the fix claims to be from the future). */
    fun ageMs(fix: LocationFix, nowMs: Long): Long = nowMs - fix.timeMs

    /** True when [fix] is no older than [maxAgeMs] and not implausibly far in the future. */
    fun isFresh(
        fix: LocationFix,
        nowMs: Long,
        maxAgeMs: Long = MAX_LIVE_AGE_MS,
        maxFutureSkewMs: Long = MAX_FUTURE_SKEW_MS,
    ): Boolean {
        val age = ageMs(fix, nowMs)
        return age <= maxAgeMs && age >= -maxFutureSkewMs
    }

    /** Valid coordinates (not NaN, in range, not null island) + finite non-negative accuracy. */
    fun isUsable(fix: LocationFix): Boolean =
        fix.point.isValid && fix.accuracyM.isFinite() && fix.accuracyM >= 0f
}
