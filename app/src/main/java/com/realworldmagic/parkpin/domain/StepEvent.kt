package com.realworldmagic.parkpin.domain

/** One or more detected steps at wall-clock [timeMs] (epoch millis, same clock as [LocationFix.timeMs]). */
data class StepEvent(val timeMs: Long, val count: Int = 1)

/** Applies a [StepEvent] walked towards [headingDeg] (true-north azimuth). */
fun DeadReckoning.onStep(event: StepEvent, headingDeg: Double): DeadReckoningEstimate? =
    onStep(headingDeg, event.timeMs, event.count)
