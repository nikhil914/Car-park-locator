package com.realworldmagic.parkpin.domain

import kotlin.math.max
import kotlin.math.min

/**
 * Hysteresis "you've arrived" detector.
 *
 * Arrives once distance < max([minEnterRadiusM], fix accuracy) for [requiredConsecutive] consecutive fixes.
 * The accuracy contribution is capped at [maxEnterRadiusM]: a 300 m cell-tower fix must not
 * declare "arrived" while the user is still 250 m away.
 * Leaves the arrived state only when distance exceeds max([exitRadiusM], current enter radius),
 * so a large accuracy circle can't cause flicker. Non-finite / negative distances are ignored.
 */
class ArrivalDetector(
    private val minEnterRadiusM: Double = 8.0,
    private val requiredConsecutive: Int = 3,
    private val exitRadiusM: Double = 20.0,
    private val maxEnterRadiusM: Double = 30.0,
) {
    init {
        require(requiredConsecutive >= 1) { "requiredConsecutive must be >= 1" }
        require(maxEnterRadiusM >= minEnterRadiusM) { "maxEnterRadiusM must be >= minEnterRadiusM" }
    }

    var arrived: Boolean = false
        private set
    private var consecutive = 0

    fun update(distanceM: Double, accuracyM: Float): Boolean {
        if (!distanceM.isFinite() || distanceM < 0.0) return arrived
        val acc = accuracyM.toDouble().takeIf { it.isFinite() } ?: 0.0
        val enterRadius = min(maxEnterRadiusM, max(minEnterRadiusM, acc))
        if (arrived) {
            if (distanceM > max(exitRadiusM, enterRadius)) {
                arrived = false
                consecutive = 0
            }
        } else {
            if (distanceM < enterRadius) {
                consecutive++
                if (consecutive >= requiredConsecutive) arrived = true
            } else {
                consecutive = 0
            }
        }
        return arrived
    }

    fun reset() {
        arrived = false
        consecutive = 0
    }
}
