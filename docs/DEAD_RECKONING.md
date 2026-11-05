# Pedestrian dead reckoning (PDR)

Underground garages and dense parking structures often have no usable GPS. ParkPin
bridges those gaps with pedestrian dead reckoning: it counts steps and uses the compass
heading to move a position estimate forward from the last good GPS fix.

Status: the domain logic and the step sensor are ready. The guide screen does not use
them yet (see "Integrating into guidance" below).

## Pieces

| File | Role |
|---|---|
| `domain/DeadReckoning.kt` | Pure Kotlin. `DeadReckoning` holds the estimate. `DeadReckoning.fuse(...)` picks or blends GPS and PDR. `FusedPosition.toLocationFix()` turns the result into a `LocationFix`. |
| `domain/StepEvent.kt` | `StepEvent(timeMs, count)` and the `DeadReckoning.onStep(event, headingDeg)` extension. |
| `sensors/StepSensor.kt` | `TYPE_STEP_DETECTOR` as `Flow<StepEvent>`. Timestamps use the epoch-millisecond clock, the same clock as `LocationFix.timeMs`. |
| `app/src/test/.../DeadReckoningTest.kt`, `FuseTest.kt` | JVM unit tests (`scripts/jvm-domain-test.sh`). |

## Model

- **Anchor.** A GPS fix with accuracy ≤ 15 m (`GOOD_FIX_ACCURACY_M`) resets the estimate
  to that fix. Distance walked and step count go back to 0. Fixes with worse accuracy do
  not reset the estimate; `fuse` combines them with PDR instead. Fixes that arrive out of
  order and invalid fixes are ignored.
- **Step.** Each step moves the estimate `stepLengthM` along the heading on a great
  circle (`DeadReckoning.destination`).
  - The default step length is 0.72 m (`DEFAULT_STEP_LENGTH_M`).
  - `DeadReckoning.stepLengthFromHeightCm(h)` returns 0.415 × height. It returns the
    default when the height is outside 100–250 cm.
  - Step lengths that are ≤ 0, non-finite or > 2.5 m fall back to the default.
- **Uncertainty.** `uncertaintyM = anchor.accuracyM + 0.05 × distanceSinceFixM`. Step
  detection and heading errors both grow with the distance walked, which is why the
  uncertainty is about 5% of that distance.
- **Reliability.** After more than 600 m without a good fix (`maxUsefulDistanceM`),
  `isReliable` becomes false and the instance `fuse()` stops using PDR.

Example: from a fix with 5 m accuracy, 100 steps north at 0.72 m per step puts the
estimate about 72 m north, with an uncertainty of 5 + 3.6 = 8.6 m.

## `fuse(gps, dr, nowMs)`

`fuse` applies these rules in order:

1. A fresh GPS fix (at most 10 s old) with accuracy ≤ 15 m is used as-is. Source: `GPS`.
2. If there is a fresh but poor GPS fix and a PDR estimate, the two are blended by
   inverse variance. The weights are 1/σ², and the combined accuracy is
   1/√(1/σ_gps² + 1/σ_pdr²). Source: `BLENDED`.
3. If there is no fresh GPS fix but there is a PDR estimate, the PDR estimate is used.
   Source: `DEAD_RECKONING`.
4. Otherwise the stale or poor GPS fix is used if there is one. If there is none, `fuse`
   returns null.

## Integrating into guidance (for `ui/guide` / `GuideViewModel`)

```kotlin
val pdr = DeadReckoning(stepLengthM = DeadReckoning.stepLengthFromHeightCm(userHeightCm))
var lastHeadingDeg: Double? = null
var lastGps: LocationFix? = null

// 1. GPS fixes
locationProvider.locationUpdates(1000).onEach { fix -> lastGps = fix; pdr.onFix(fix) }
// 2. Heading (true north). While walking, the phone usually points the way you walk.
compass.headingUpdates().onEach { h -> lastHeadingDeg = h.azimuthDeg.toDouble() }
// 3. Steps (only when stepSensor.canDetectSteps)
stepSensor.stepEvents().onEach { e -> lastHeadingDeg?.let { pdr.onStep(e, it) } }
// 4. On each tick / fix / step, compute the position to guide from:
val position = pdr.fuse(lastGps, nowMs = System.currentTimeMillis())?.toLocationFix()
// Feed `position` to GuidanceCalculator in place of the raw fix.
// position.provider is "gps", "pdr" or "fused".
// Show "Estimated (no GPS)" with ±accuracyM when the provider is "pdr".
```

Notes:

- Call `pdr.reset()` when guidance stops. Collect the step flow only while the guide
  screen is visible (battery).
- Use one coroutine (for example `combine`/`merge` on `viewModelScope`) for all three
  flows. `DeadReckoning` is not thread-safe.
- Arrival detection should keep using the fused accuracy, so a growing PDR uncertainty
  widens the arrival radius in the same way as poor GPS.

## Permission: `ACTIVITY_RECOGNITION`

On Android 10 (API 29) and later, step sensors need the **runtime** permission
`android.permission.ACTIVITY_RECOGNITION`. On API 26–28 they need no permission.

- **Manifest (not yet added).** Add this line to `app/src/main/AndroidManifest.xml`:
  `<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />`
- **Runtime.** Request `StepSensor.requiredPermission` (null below API 29). For example,
  wrap the indoor mode in `PermissionGate(listOf(it))`.
- **When the permission is missing.** `stepEvents()` completes immediately and guidance
  uses GPS only, so the feature degrades without breaking guidance.
- **Privacy.** Steps are counted on the device only. They are never stored or sent.
