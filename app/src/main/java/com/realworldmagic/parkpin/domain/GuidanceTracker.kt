package com.realworldmagic.parkpin.domain

/**
 * Stateful position pipeline for the guide screen. Pure Kotlin, not thread-safe: drive it from
 * one coroutine.
 *
 * Raw GPS fix -> [AccuracyFilter] (rejects poor / teleporting fixes, smooths) -> optional
 * pedestrian dead reckoning ([DeadReckoning.fuse]) -> [ArrivalDetector].
 *
 * - Every accepted fix also feeds [DeadReckoning.onFix], so a good fix re-anchors the PDR track.
 * - Steps move the PDR estimate only while [stepTracking] is on and a heading is known.
 * - While [stepTracking] is on, the guidance position is the fused GPS/PDR estimate; when GPS is
 *   weak or gone this is the dead-reckoned position (provider `"pdr"`, see [Snapshot.estimated]).
 * - The arrival detector is updated only when the position actually moves (fix or step), never on
 *   heading changes or clock ticks, and it is reset when the target spot id changes.
 */
class GuidanceTracker(
    private val filter: AccuracyFilter = AccuracyFilter(maxAccuracyM = DEFAULT_MAX_FIX_ACCURACY_M),
    private val deadReckoning: DeadReckoning = DeadReckoning(),
    private val arrivalDetector: ArrivalDetector = ArrivalDetector(),
) {
    /** One output of the pipeline. */
    data class Snapshot(
        val spot: ParkingSpot?,
        /** Position to guide from (filtered GPS, blended or dead-reckoned); null before the first usable fix. */
        val position: LocationFix?,
        val arrived: Boolean,
        /** True when [position] is a pure dead-reckoning estimate (no usable GPS). */
        val estimated: Boolean,
        /** The latest raw fix came from a mock location provider. */
        val mockLocation: Boolean,
    )

    /** When true, step events move the estimate and the guidance position uses PDR fusion. */
    var stepTracking: Boolean = false

    private var spot: ParkingSpot? = null
    private var gps: LocationFix? = null
    private var mock = false
    private var headingDeg: Double? = null
    private var position: LocationFix? = null
    private var arrived = false

    fun snapshot(): Snapshot = Snapshot(
        spot = spot,
        position = position,
        arrived = arrived,
        estimated = position?.provider == PROVIDER_PDR,
        mockLocation = mock,
    )

    /** New or updated target spot. A different spot id resets arrival detection. */
    fun onSpot(newSpot: ParkingSpot?, nowMs: Long): Snapshot {
        if (newSpot?.id != spot?.id) {
            arrivalDetector.reset()
            arrived = false
        }
        spot = newSpot
        recomputePosition(nowMs)
        return snapshot()
    }

    /** Raw fix from the location provider. Returns null when the filter rejected it and nothing changed. */
    fun onFix(raw: LocationFix, nowMs: Long): Snapshot? {
        val mockChanged = raw.isMock != mock
        mock = raw.isMock
        val accepted = filter.process(raw)
        if (accepted == null) return if (mockChanged) snapshot() else null
        gps = accepted
        deadReckoning.onFix(accepted)
        recomputePosition(nowMs)
        updateArrival()
        return snapshot()
    }

    /** Latest true-north heading in degrees (the walking direction while the phone is held forward). */
    fun onHeading(azimuthDeg: Double?) {
        headingDeg = azimuthDeg?.takeIf { it.isFinite() }
    }

    /** Step-detector event. Returns null when it was ignored (tracking off, no heading, no anchor). */
    fun onStep(event: StepEvent, nowMs: Long): Snapshot? {
        if (!stepTracking) return null
        val h = headingDeg ?: return null
        deadReckoning.onStep(event, h) ?: return null
        recomputePosition(nowMs)
        updateArrival()
        return snapshot()
    }

    /** Clock tick: lets the fused position fall back to PDR once the last GPS fix goes stale. */
    fun onTick(nowMs: Long): Snapshot {
        recomputePosition(nowMs)
        return snapshot()
    }

    /** Forgets everything (guidance stopped). */
    fun reset() {
        filter.reset()
        deadReckoning.reset()
        arrivalDetector.reset()
        spot = null
        gps = null
        mock = false
        headingDeg = null
        position = null
        arrived = false
    }

    private fun recomputePosition(nowMs: Long) {
        val g = gps
        position = if (!stepTracking) {
            g
        } else {
            val fused = deadReckoning.fuse(g, nowMs)
            when {
                fused == null -> g
                // Keep the original fix (provider, altitude, mock flag) when GPS wins outright.
                fused.source == PositionSource.GPS && g != null && fused.point == g.point -> g
                else -> fused.toLocationFix(g?.altitudeM).copy(isMock = g?.isMock == true)
            }
        }
    }

    private fun updateArrival() {
        val s = spot ?: return
        val p = position ?: return
        val d = GeoMath.distanceMeters(p.point, s.point)
        arrived = arrivalDetector.update(d, p.accuracyM)
    }

    companion object {
        /**
         * Fixes worse than this are dropped. Generous on purpose: a 60 m fix still lets the guide
         * show "GPS weak" and a rough direction instead of "Waiting for GPS".
         */
        const val DEFAULT_MAX_FIX_ACCURACY_M = 100f
        const val PROVIDER_PDR = "pdr"
    }
}
