# ParkPin Testing Strategy

ParkPin must work in places where GPS and compass readings are poor: garages, city streets,
airplane mode. Automated tests cover the logic. Field tests cover the sensors.

| Layer | Tool | Runs on | Status |
|---|---|---|---|
| Domain (pure Kotlin) | JUnit 4 | JVM (`testDebugUnitTest`) | Phase 0 |
| Room DAO / repository | Robolectric (JVM) or instrumented (`androidTest`) | JVM / device | Planned |
| ViewModels | JUnit 4 + `kotlinx-coroutines-test` + fakes | JVM | Planned |
| Compose UI | `ui-test-junit4` | Device / emulator, Robolectric | Planned |
| Sensors and GPS | Manual field tests + mock locations | Real devices | Each release |

CI ([android.yml](../.github/workflows/android.yml)) runs unit tests, lint and a debug build on every push and pull request to `main`.

```bash
./gradlew :app:testDebugUnitTest          # JVM unit tests
./gradlew :app:connectedDebugAndroidTest  # instrumented tests (device/emulator attached)
```

## 1. JVM unit tests: domain

Location: `app/src/test/java/com/realworldmagic/parkpin/domain/`. No Android framework; fast and deterministic.

### `GeoMath`
- `distanceMeters`: zero for identical points; known pairs (e.g. London to Paris ≈ 343.5 km within 0.5 %); short distances (≈ 10 m) within 0.1 m; symmetric.
- Antimeridian crossing (179.9° to −179.9°) and near-pole points do not blow up.
- `initialBearingDeg`: due N = 0, E = 90, S = 180, W = 270; result always in `[0, 360)`.
- `normalizeDeg`: −10 → 350, 360 → 0, 725 → 5.
- `relativeBearingDeg`: result in `[−180, 180]`; bearing 10 / heading 350 → +20; bearing 350 / heading 10 → −20; 180 boundary.
- `formatDistance`: metric `"45 m"`, `"1.2 km"`; imperial `"150 ft"`, `"0.8 mi"`; rounding at unit switch thresholds.
- `walkingEtaMinutes`: 0 m → 0; 81 m at 1.35 m/s → 1; rounds up partial minutes.
- `cardinal`: 0 → N, 44 → NE, 359 → N, 180 → S; 8 sectors boundaries.

### `ArrivalDetector`
- Arrives only after 3 consecutive fixes within `max(8 m, accuracy)`.
- One far fix in between resets the counter.
- Stays "arrived" between 8 m and 20 m (hysteresis); leaves when > 20 m.
- Large accuracy (e.g. 25 m) widens the arrival radius.

### `AccuracyFilter`
- Rejects fixes worse than the threshold; accepts good ones.
- Best-of-N selection keeps the most accurate fix; early stop at ≤ 10 m.
- Smoothing does not lag a large real jump forever; ignores single outliers.
- Stale fixes (old `timeMs`) are rejected.
- Heading smoothing uses a circular mean: 359° and 1° average to 0°, not 180°.

### `GuidanceState` assembly
- `gpsWeak` true when accuracy > 30 m.
- `relativeDeg` null when heading is missing; distance still computed.
- `compassNeedsCalibration` follows sensor accuracy.

## 2. Room DAO and repository (planned)

Preferred: Robolectric so the tests run in CI without an emulator, using `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries()`. Keep an instrumented copy under `androidTest` for a real SQLite check before releases.

- `saveNewSpot` inserts and deactivates the previous active spot: exactly one `isActive = true` row.
- `observeActiveSpot` emits `null` → spot → `null` after `clearActive()`.
- `observeHistory` is ordered newest first and includes inactive spots.
- `update` keeps the id; note, photo URI and meter fields round-trip.
- `delete` removes the row; deleting the active spot makes `observeActiveSpot` emit `null`.
- Entity ↔ domain mapping preserves nullable `altitudeM`, `floorLabel`, `address`.
- Migrations: once schema v2 exists, `MigrationTestHelper` test for every version step (export schemas to `app/schemas/`).
- `SettingsRepository`: DataStore with a temp file; defaults, writes and reads.

## 3. ViewModels (planned)

Use fakes for `LocationProvider`, `CompassSensor` (behind an interface or a fake Flow source) and `ParkingRepository`, with `StandardTestDispatcher` and Turbine-style collection.

- Home: save with good fix, timeout → fallback "approximate", permission missing state.
- Guide: distance / relative bearing update on fake fixes and headings; arrival haptic event emitted once.
- History: delete and re-guide actions.

## 4. Compose UI tests (planned)

`createAndroidComposeRule<MainActivity>()` on emulator, or `createComposeRule()` for single screens with fake state.

- Home: big *I parked here* button visible and has a content description; active spot card shows accuracy.
- Permission gate: denied state shows rationale and *Open settings* button.
- Guide: arrow rotation semantic / distance text matches state; "GPS weak" banner at accuracy > 30 m; calibration prompt.
- History: list renders, swipe or button delete, empty state.
- Spot edit: note and floor label persist after navigation back.
- Navigation: every route in `ParkPinNavHost` reachable; back stack correct.
- Accessibility: TalkBack text such as "car at 2 o'clock, 40 m"; touch targets ≥ 48 dp; dark mode screenshots.

## 5. Manual field-test checklist

Run before every release. Record device, Android version, result and reported accuracy.

### Environments
- [ ] **Open parking lot**: save, walk 100–200 m away, guide back. Arrival within ~8 m; arrow stable.
- [ ] **Urban canyon** (tall buildings): accuracy often 20–50 m. Check *GPS weak* banner, no wild arrow jumps, arrival not triggered early.
- [ ] **Multi-level garage**: save on an upper/lower level. Floor label and note shown prominently; altitude saved; guidance degrades gracefully with no fix (shows last known, not a crash).
- [ ] **Underground garage**: save with no GPS fix → fallback to last known fix flagged "approximate".
- [ ] **Airplane mode**: full flow (save, guide, AR, history, notes, photo, meter reminder) works. Map shows cached tiles or grid plus markers.
- [ ] **Walking with phone in pocket and back out**: guidance resumes quickly; no background location use while screen off.

### Compass
- [ ] Rotate phone 360° in place: arrow stays pointed at the car within ~10–15°.
- [ ] Near metal / car body: low sensor accuracy triggers figure-8 calibration prompt; prompt clears after calibration.
- [ ] Portrait tilt (AR pose, pitch ≈ 90°): heading still correct.
- [ ] True-north correction: compare with a known landmark bearing.

### Permissions
- [ ] Deny location once: rationale shown; app does not crash.
- [ ] Deny location permanently ("Don't ask again"): *Open settings* path works.
- [ ] Approximate location only (Android 12+): save works, accuracy warning shown.
- [ ] Deny camera: AR guide falls back to compass guide; photo option disabled with explanation.
- [ ] Deny notifications (Android 13+): meter timer still shows in-app countdown with warning.
- [ ] Revoke a permission in system settings while app is in background: no crash on return.
- [ ] Location services switched off globally: clear prompt to enable.

### Devices / OS versions
- [ ] Android 8.0 / 8.1 (API 26–27): minSdk, no Play Services quirks.
- [ ] Android 12 (API 31): approximate-location dialog, exact alarm rules.
- [ ] Android 14 (API 34): foreground/notification behaviour, photo picker.
- [ ] Android 15 (API 35): edge-to-edge layout, targetSdk 35 behaviour.
- [ ] Device **without Google Play Services** (e.g. AOSP emulator image, de-Googled phone): `LocationManager` fallback.
- [ ] Device without rotation-vector sensor or magnetometer: compass guide hidden / map-only guidance.

### General
- [ ] Rotate screen and process death (*Don't keep activities*): state restored.
- [ ] Dark mode and large font (200 %).
- [ ] TalkBack pass on every screen.
- [ ] Battery: 15 min of guidance; no wakelocks left after leaving the screen.

## 6. Mock-location testing

### Emulator
Use the emulator's **Extended controls > Location** panel (single points, GPX/KML routes), or the console:

```bash
# Emulator console: longitude first, then latitude (and optional altitude)
adb emu geo fix -0.1276 51.5072          # set a fix
adb emu geo fix -0.1280 51.5075 35       # with altitude 35 m
```

Script a walk back to the car:

```bash
for p in "-0.1290 51.5080" "-0.1285 51.5077" "-0.1280 51.5075" "-0.1277 51.5073" "-0.1276 51.5072"; do
  adb emu geo fix $p; sleep 2
done
```

The emulator has no real magnetometer. Use **Extended controls > Virtual sensors** to rotate the device and test the compass arrow.

### Physical device
1. Enable **Developer options > Select mock location app** and pick a mock-location app (e.g. a GPX-replay app).
2. Replay a recorded GPX track of a real walk back from the car.
3. For instrumented tests, use `FusedLocationProviderClient.setMockMode(true)` / `setMockLocation(...)` or `LocationManager.addTestProvider(...)` with:

```bash
adb shell appops set com.realworldmagic.parkpin android:mock_location allow
```

Mock fixes carry `isMock` (API 31+, `isFromMockProvider` earlier). Debug builds may show a "mock location" badge; release builds must not trust mock fixes silently.

### Accuracy simulation
Feed fake `LocationFix` sequences into `AccuracyFilter` and `ArrivalDetector` unit tests: noisy 30–50 m urban fixes, sudden jumps, and a total loss of fix. This complements field tests and catches regressions in CI.
