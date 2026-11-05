package com.realworldmagic.parkpin.domain

/**
 * Rate-limits spoken (TalkBack live-region) guidance so distance updates every second do not
 * flood the screen reader. Pure Kotlin.
 *
 * Usage from the UI: on every new candidate text call [delayBeforeAnnouncing]; null means
 * "already said, nothing to do", otherwise wait that many ms (0 = now), then publish the text
 * and call [markAnnounced]. If the text changes again while waiting, start over with the new text:
 * the latest text wins and at most one routine announcement happens per [minIntervalMs].
 *
 * @param minIntervalMs minimum gap between routine announcements.
 * @param urgentIntervalMs minimum gap for urgent ones (e.g. "You have arrived").
 */
class AnnouncementThrottle(
    val minIntervalMs: Long = 8_000L,
    val urgentIntervalMs: Long = 0L,
) {
    var lastText: String? = null
        private set
    private var lastTimeMs: Long = Long.MIN_VALUE

    fun delayBeforeAnnouncing(text: String, nowMs: Long, urgent: Boolean = false): Long? {
        if (text == lastText) return null
        if (lastTimeMs == Long.MIN_VALUE) return 0L
        val interval = if (urgent) urgentIntervalMs else minIntervalMs
        val elapsed = nowMs - lastTimeMs
        return (interval - elapsed).coerceAtLeast(0L)
    }

    /** True when [text] may be announced right now. */
    fun shouldAnnounce(text: String, nowMs: Long, urgent: Boolean = false): Boolean =
        delayBeforeAnnouncing(text, nowMs, urgent) == 0L

    fun markAnnounced(text: String, nowMs: Long) {
        lastText = text
        lastTimeMs = nowMs
    }

    /** Announce-if-allowed. Returns [text] when it should be spoken now (and records it), else null. */
    fun offer(text: String, nowMs: Long, urgent: Boolean = false): String? {
        if (!shouldAnnounce(text, nowMs, urgent)) return null
        markAnnounced(text, nowMs)
        return text
    }

    fun reset() {
        lastText = null
        lastTimeMs = Long.MIN_VALUE
    }
}
