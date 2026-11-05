package com.realworldmagic.parkpin.domain

/** Pure combination of spot + fix + heading into a [GuidanceState]. Arrival state comes from an [ArrivalDetector]. */
object GuidanceCalculator {
    const val GPS_WEAK_ACCURACY_M = 30f

    fun calculate(
        spot: ParkingSpot?,
        fix: LocationFix?,
        heading: Heading?,
        arrived: Boolean = false,
        gpsWeakThresholdM: Float = GPS_WEAK_ACCURACY_M,
    ): GuidanceState {
        val distance: Double?
        val bearing: Double?
        if (spot != null && fix != null) {
            distance = GeoMath.distanceMeters(fix.point, spot.point)
            bearing = GeoMath.initialBearingDeg(fix.point, spot.point)
        } else {
            distance = null
            bearing = null
        }
        val relative = if (bearing != null && heading != null) {
            GeoMath.relativeBearingDeg(bearing, heading.azimuthDeg.toDouble())
        } else {
            null
        }
        return GuidanceState(
            spot = spot,
            fix = fix,
            heading = heading,
            distanceM = distance,
            bearingDeg = bearing,
            relativeDeg = relative,
            arrived = arrived && distance != null,
            gpsWeak = fix != null && fix.accuracyM > gpsWeakThresholdM,
            compassNeedsCalibration = heading?.needsCalibration == true,
        )
    }

    /**
     * Convenience: feeds the detector with the new distance (if any) and returns the state.
     * The detector is the only stateful part; everything else is pure.
     */
    fun calculate(
        spot: ParkingSpot?,
        fix: LocationFix?,
        heading: Heading?,
        arrivalDetector: ArrivalDetector,
        gpsWeakThresholdM: Float = GPS_WEAK_ACCURACY_M,
    ): GuidanceState {
        val base = calculate(spot, fix, heading, false, gpsWeakThresholdM)
        val d = base.distanceM
        val arrived = if (d != null && fix != null) arrivalDetector.update(d, fix.accuracyM) else arrivalDetector.arrived
        return base.copy(arrived = arrived && d != null)
    }

    /** TalkBack-friendly sentence, e.g. "Car at 2 o'clock, 40 m". */
    fun describe(state: GuidanceState, metric: Boolean): String {
        val d = state.distanceM ?: return "Waiting for location"
        if (state.arrived) return "You have arrived at your car"
        val dist = GeoMath.formatDistance(d, metric)
        val rel = state.relativeDeg
        return if (rel != null) {
            "Car at ${ClockDirection.describe(rel)}, $dist"
        } else {
            "Car $dist ${GeoMath.cardinal(state.bearingDeg ?: 0.0)}"
        }
    }
}
