package com.realworldmagic.parkpin.domain

import kotlin.math.max

/**
 * Hysteresis "you've arrived" detector.
 *
 * Arrives once distance < max([minEnterRadiusM], fix accuracy) for [requiredConsecutive] consecutive fixes.
 * Leaves the arrived state only when distance exceeds max([exitRadiusM], current enter radius),
 * so a large accuracy circle can't cause flicker.
 */
class ArrivalDetector(
    private val minEnterRadiusM: Double = 8.0,
    private val requiredConsecutive: Int = 3,
    private val exitRadiusM: Double = 20.0,
) {
    var arrived: Boolean = false
        private set
    private var consecutive = 0

    fun update(distanceM: Double, accuracyM: Float): Boolean {
        val enterRadius = max(minEnterRadiusM, accuracyM.toDouble().takeIf { it.isFinite() } ?: 0.0)
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
