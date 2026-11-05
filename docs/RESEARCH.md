# ParkPin research notes (November 2026)

This document collects research for ParkPin ("Where Did I Park?"), the first tool in the Real-World Magic AR Toolbox. It covers competitors, location and sensor accuracy, Android 12-17 platform and Play policy, offline maps, AR, indoor positioning, background work, privacy, new feature ideas, and dependency updates.

It was researched on 2026-11-05. Every section ends with **Recommendations** for this codebase. Sources are linked inline and collected in [Sources](#sources).

> **Research limits.** The sandbox could reach `developer.android.com`, `github.com` and Maven Central. It could not reach `developers.google.com`, `support.google.com`, `operations.osmfoundation.org`, `maplibre.org` or Google Maven (`dl.google.com`). Facts from those hosts come from search-result snippets and mirrors, and they are marked *(via search)*. Check them before you make a compliance decision.

---

## Table of contents

1. [Executive summary](#1-executive-summary)
2. [Competitors](#2-competitors)
3. [Location accuracy](#3-location-accuracy)
4. [Android 12-17 permissions and Play policy](#4-android-1217-permissions-and-play-policy)
5. [Compass and heading](#5-compass-and-heading)
6. [Offline maps](#6-offline-maps)
7. [AR guidance](#7-ar-guidance)
8. [Indoor and underground](#8-indoor-and-underground)
9. [Battery, scheduling and surfaces](#9-battery-scheduling-and-surfaces)
10. [Privacy and Play Data safety](#10-privacy-and-play-data-safety)
11. [New feature suggestions](#11-new-feature-suggestions)
12. [Recommended changes to architecture](#12-recommended-changes-to-architecture)
13. [Sources](#sources)

---

## 1. Executive summary

| # | Finding | Why it matters | Action |
|---|---|---|---|
| 1 | **New Play location policy.** Every app that requests `ACCESS_FINE_LOCATION` must file a Play Console declaration. The declaration opens in November 2026 and becomes mandatory on **27 January 2027**. Apps that target API 37 and only need one-time precise location must use the new **location button**. | ParkPin requests fine location. Without the declaration, an update can be rejected. | File the declaration with "turn-by-turn style walking guidance back to the car" as the core feature. Add `androidx.core:core-locationbutton` for the in-app "I parked here" button. See [§4.2](#42-the-2026-precise-location-policy-new). |
| 2 | **osmdroid is archived** (read-only since 20 Nov 2024, last release 6.1.20). | There will be no fixes for new Android versions. | Plan a migration to **MapLibre Native Android 13.x** with an offline vector source. See [§6](#6-offline-maps). |
| 3 | **The OSM tile policy forbids offline prefetch** on `tile.openstreetmap.org`. "Download area for offline" is named as prohibited. | Roadmap Phase 4 ("osmdroid CacheManager for bounding box") would break the policy and can get the app's User-Agent blocked. | Remove that roadmap item. Use OpenFreeMap or Protomaps PMTiles extracts, which allow downloading. |
| 4 | **Targeting is behind.** Play has required API 36 for new apps and updates since 31 Aug 2026. The repo targets 35. | Updates are blocked until the target is raised. | Raise to `compileSdk 36/37`, `targetSdk 36`. Handle mandatory edge-to-edge and predictive back. |
| 5 | **The toolchain is about 2 years old.** AGP 8.7.3 → 9.5, Kotlin 2.0.21 → 2.4.20, Compose BOM 2024.12 → 2026.09, Room 2.6.1 → 2.8.5. | Security fixes, Android 16/17 support, and the KSP2 requirement. | Upgrade in one migration PR. See [§12](#12-recommended-changes-to-architecture). |
| 6 | **The meter reminder uses delayed WorkManager.** Doze can defer it well past the expiry time. | A late "meter expires" reminder is worse than none. | Use `AlarmManager.setWindow` (10-minute window), or `setAlarmClock` after an opt-in `SCHEDULE_EXACT_ALARM` grant. |
| 7 | **`FusedOrientationProviderClient` exists** (play-services-location 21.2+). It gives a fused heading referenced to true north, with an accuracy value, and needs no permission. | It fixes compass jitter and declination handling on GMS devices. | Use it as the primary heading source. Keep `CompassSensor` as the fallback when GMS is missing. |
| 8 | **Competitors:** Google Maps on Android has no automatic parking save; only iOS and Android Auto do. Most third-party "find my car" apps are ad-heavy. None combines an offline map, AR and floor estimation. | This is the gap ParkPin can fill. | Lead the Play listing with "Private, offline, works underground". |

---

## 2. Competitors

### 2.1 Feature matrix

| Product | Auto-save trigger | Offline | AR view | Indoor/floor | Notes/photo | Meter timer | Account/ads | Platform |
|---|---|---|---|---|---|---|---|---|
| **Google Maps (Android)** | **None.** Manual "Save your parking" from the blue dot | Partial (offline areas need a download while online) | Live View walking (online, VPS) | No | Notes + photo | "Time left" field | Google account, location history | Android/iOS |
| **Google Maps (iOS)** | Automatic when the iPhone disconnects from CarPlay or Bluetooth (Dec 2025) | Partial | Live View | No | Yes | Yes | Google account | iOS |
| **Google Maps on Android Auto** | Prompt on arrival: "Save parking location" | No | No | No | No | No | Google account | Car |
| **Apple Maps "Parked Car"** | CarPlay or Bluetooth disconnect, using Significant Locations | Partial | No | No | Notes + photo | No | Apple ID | iOS |
| **SpotAngels** | Car Bluetooth plus motion sensors | No (data-driven) | No | No | Limited | Street-cleaning alerts (US cities) | Account, ads, booking | Android/iOS |
| **ParkMobile** | n/a (payment app) | No | No | No | No | Session expiry and extension | Account plus payment | Android/iOS |
| **Carrow** | Manual | Yes (compass arrow) | No | No | ? | ? | ? | Android |
| **ParkSave** (May 2026) | Bluetooth or Android Auto disconnect | ? | Yes (AR camera) | No | ? | ? | Free, no ads | Android |
| **Find My Car with AR Tracker** | Manual | Yes | **Paid** unlock | No | Yes | ? | Paid | iOS |
| **ParkPin (this app)** | Tile, widget, Bluetooth/CDM disconnect, Activity Recognition (planned) | **Fully** (GPS + sensors + Room) | **Yes, no ARCore needed** | Barometer floor estimate | Note, floor, photo | Yes | **None** | Android |

Sources: Google Maps Android is manual only and auto-save is iOS only ([BetaNews, Dec 2025](https://betanews.com/2025/12/12/google-maps-now-makes-it-easier-for-iphone-users-to-find-their-cars/); [androidayuda](https://en.androidayuda.com/google-maps-allows-you-to-register-where-you-parked/)). Android Auto prompt ([9to5Google](https://9to5google.com/2023/12/07/android-auto-parking-location-google-maps/); [Android Authority](https://androidauthority.com/android-auto-google-maps-parking-location-3392613)). Apple Maps ([Apple Support 101587](https://support.apple.com/en-qa/101587); [iPhoneLife](https://iphonelife.com/content/how-to-find-your-parked-car-if-you-dont-have-bluetooth-or-carplay)). SpotAngels ([App Store listing](https://apps.apple.com/us/app/id897809583); [company Medium post](https://medium.com/spot-angels/why-spotangels-fbce053ed754)). Android "find my car" apps ([Carrow on Play](https://play.google.com/store/apps/details?id=fr.carrow.app); [ParkSave on AppGoblin](https://appgoblin.info/apps/com.sahil.parksave); [Find My Car AR on the App Store](https://apps.apple.com/app/id1065080375)).

### 2.2 Review complaints by theme

| Theme | Where it shows up | Evidence | ParkPin answer |
|---|---|---|---|
| Wrong or stale data costs money (tickets) | SpotAngels, ParkMobile | Aggregated reviews name "Parking Information Accuracy" as the top SpotAngels complaint ([mwm.ai](https://mwm.ai/apps/spotangels-parking-gas/897809583); [Kimola](https://kimola.com/reports/spotangels-review-report-see-what-users-really-think-app-store-us-156048)). ParkMobile users report "paid and still got a ticket" ([Unstar 2026](https://unstar.app/blog/parkmobile-paid-still-got-ticket-app-not-working-reviews-2026)). | Do not claim enforcement data. Show the **accuracy circle** honestly. |
| Late expiry notification | ParkMobile | Unstar's 2026 analysis lists "late expiry notifications" as a recurring theme ([Unstar](https://unstar.app/blog/parkmobile-spothero-paybyphone-parkwhiz-passport-parking-apps-ranked-2026)). | Use alarm-based reminders, not deferrable work ([§9.2](#92-meter-reminder-workmanager-vs-alarmmanager)). |
| Wrong vehicle or account friction | ParkMobile | A Trustpilot reviewer got a ticket after the app defaulted to the other car ([Trustpilot](https://www.trustpilot.com/review/parkmobile.io?page=8)). | Make the "car profile" (BT device) visible on every auto-save notification. |
| Unnecessary permissions and nagging | SpotAngels | "asking for unnecessary permissions", "keeps asking for a rating" ([Kimola GP](https://kimola.com/reports/spot-angel-feedback-report-navigate-parking-app-reviews-google-play-en-144451)) | Ask for permissions incrementally. Never prompt for ratings in the guide flow. |
| Auto-detection misfires | SpotAngels | The developer replied that they are "actively working on improving the parking detection" ([mwm.ai](https://mwm.ai/apps/spotangels-parking-gas/897809583)). | Make auto-save a **suggestion** with a one-tap confirm, plus a 90 s debounce (already in `QuickSavePrefs`). |
| No auto-save on Android | Google Maps | Auto-save is iOS only ([BetaNews](https://betanews.com/2025/12/12/google-maps-now-makes-it-easier-for-iphone-users-to-find-their-cars/)). | CDM presence plus a Bluetooth ACL receiver (already built), plus Activity Recognition. |
| CarPlay/BT dependency | Apple Maps | "only updated based on the last time your iPhone connected to CarPlay" ([SlashGear](https://www.slashgear.com/989226/how-to-find-where-you-parked-your-car-using-apple-maps)) | Several triggers: tile, widget, BT, AR transition. |
| AR is paywalled | Find My Car AR (iOS) | Paid AR unlock ([App Store](https://apps.apple.com/app/id1065080375)) | Free AR with no ARCore requirement. |
| GPS bad between buildings or indoors | Carrow listing | The listing admits lower precision between tall buildings or indoors ([Play](https://play.google.com/store/apps/details?id=fr.carrow.app)). | Last good outdoor fix, barometer floor, photo of the pillar. |

### 2.3 Positioning

**Differentiation statement:** *"Your car, found — even underground, even offline. No account, no ads, nothing leaves your phone."*

1. **Offline first, everywhere.** No competitor works end to end in airplane mode with a map. ParkPin's offline map gap closes with §6.
2. **Underground garages.** No competitor estimates floors. ParkPin has `BarometricFloorEstimator` and a pillar photo. Market this as the headline feature.
3. **AR without ARCore or a network.** The sensor overlay works on more than 95% of devices that have a magnetometer.
4. **Privacy.** "Data not collected" in Data safety ([§10](#10-privacy-and-play-data-safety)) is a real store-listing advantage over SpotAngels and ParkMobile.

---

## 3. Location accuracy

### 3.1 Fused vs platform `LocationManager`

| Aspect | `FusedLocationProviderClient` (GMS) | `LocationManager.GPS_PROVIDER` |
|---|---|---|
| Sources | GNSS + Wi-Fi + cell + sensors. Uses the device's GNSS chipset for `PRIORITY_HIGH_ACCURACY`. | GNSS only |
| Airplane mode | Still gets GNSS fixes with `PRIORITY_HIGH_ACCURACY`. Balanced priority may stop updating when no Wi-Fi or cell is in range ([SO via mirror](https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/21022297/fused-location-provider-doesn-t-seem-to-use-gps-receiver)). | Works |
| "GPS only" device mode | Reported unusable or settings check fails ([B4X forum](https://www.b4x.com/android/forum/posts/721459)) | Works |
| TTFF offline | Without network there is no A-GPS/SUPL assistance. A cold start can take 30 s to several minutes. Cached ephemeris (valid for hours) and PSDS/XTRA predicted orbits (downloaded when online, valid for days) shorten it. | Same chipset, same constraints |
| Indoor | Wi-Fi location only if a Wi-Fi scan is possible and a cached model exists | Usually no fix |
| Devices without GMS | n/a | Required (Huawei, some AOSP ROMs) |
| `getCurrentLocation` | Built-in timeout and `maxUpdateAgeMillis` via `CurrentLocationRequest` | Use `getCurrentLocation()` on API 30+ |

`play-services-location` **21.4.0** (mid-2026) changed `IMPLICIT_MIN_UPDATE_INTERVAL` to half the requested interval *(via search: [Google Play services release notes](https://developers.google.com/android/guides/releases))*. Pass `setMinUpdateIntervalMillis` explicitly so guidance stays predictable.

**Recommendation.** Keep `LocationProviderFactory`, but run **both providers in parallel during the save window** and take the best fix. When the platform provider gives a better accuracy, prefer it. When no fix arrives within 15 s, the "approximate" fallback in ARCHITECTURE stays correct.

```kotlin
// location/SpotCapture.kt — best-of-N, both providers, honours approximate-only grants
suspend fun captureBestFix(
    providers: List<LocationProvider>,
    timeoutMs: Long = 15_000,
    goodEnoughM: Float = 10f,
): LocationFix? = withTimeoutOrNull(timeoutMs) {
    providers.map { it.locationUpdates(intervalMs = 1_000) }
        .merge()
        .filterNot { it.isMock }                        // see §3.4
        .runningFold<LocationFix, LocationFix?>(null) { best, fix ->
            if (best == null || fix.accuracyM < best.accuracyM) fix else best
        }
        .filterNotNull()
        .first { it.accuracyM <= goodEnoughM }
} ?: providers.firstNotNullOfOrNull { it.lastKnown() }?.copy(provider = "last-known")
```

(`withTimeoutOrNull` drops the running best when it times out. In production, keep the best in a local `var` so it can be returned after the timeout.)

### 3.2 Urban canyons, garages and dual-frequency L5

- **L5/E5a dual frequency** greatly reduces multipath. A Hong Kong urban-canyon study reached under 10 m on average with L1+L5 smartphone data, and about 5 m with 3D-mapping-aided GNSS ([PolyU paper](https://www.polyu.edu.hk/aae/ipn-lab/us/publications/Fullpaper/Urban%20Positioning_3D%20Mapping%20Aided%20GNSS%20using%20Dual-Frequency%20Pseudorange%20Measurements%20from%20Smartphones.pdf); [NAVI journal](https://navi.ion.org/content/68/4/727)).
- Phones with dual frequency include Xiaomi Mi 8 (2018), Samsung and Asus flagships (2020+), Pixel (2021+) and iPhone 14 Pro+. The GPS L5 constellation is not complete until about 2027 ([ABI Research](https://www.abiresearch.com/market-research/insight/7781281-dual-frequency-gnss-gaining-momentum-in-hi)).
- **Garages.** GNSS is effectively gone below ground level. The reported `accuracy` can look plausible (20-40 m) while the position has drifted. **Never trust a fix whose accuracy gets worse while the user is walking down a ramp.**

**Recommendations**

| # | Change |
|---|---|
| L1 | Log `GnssStatus` (satellites used in fix, C/N0 mean) next to each fix, and store `satsUsed` on `ParkingSpot`. Fewer than 4 used satellites, or mean C/N0 under 20 dB-Hz, means "probably indoors". |
| L2 | **Last-good-fix buffer:** keep a 5-minute ring buffer of fixes while the app or the quick-save FGS is active. When the user saves indoors, offer *"Use your last outdoor position (2 min ago, ±6 m)"*. See [§8.4](#84-last-good-fix). |
| L3 | Show the device's dual-frequency capability on a diagnostics screen (`GnssCapabilities`, API 31+; the carrier frequency of `GnssStatus` satellites on 30+). This explains accuracy to power users. |

### 3.3 Altitude

- `Location.getAltitude()` is **WGS84 ellipsoid height**, not sea level. The difference (geoid undulation) is about -100 to +85 m.
- API 34 added `android.location.altitude.AltitudeConverter.addMslAltitudeToLocation()`. It is offline (bundled geoid assets) and must run off the main thread. API 35 added `tryAddMslAltitudeToLocation()` for the main thread after warm-up ([reference](https://developer.android.com/reference/android/location/altitude/AltitudeConverter)).
- For **floor estimation**, use barometer deltas ([§8.1](#81-barometer-floor-estimation)). GNSS vertical error (often over 10 m) is about three floors.

### 3.4 Mock location detection

- API 31+: `Location.isMock()`. Below 31: `isFromMockProvider()`, which is deprecated ([API diff 31](https://developer.android.com/sdk/api_diff/31/changes/android.location.Location)).
- `Settings.Secure.ALLOW_MOCK_LOCATION` checks have been useless since API 23 ([MockLocationDetector](https://github.com/dinatha08/MockLocationDetector)).
- A parking app is a low-value target, so **do not block mocked fixes**. Flag them in the UI ("Test location") and exclude them from best-of-N during save. Keep accepting them in debug builds for testing.

```kotlin
val Location.isMockCompat: Boolean
    get() = if (Build.VERSION.SDK_INT >= 31) isMock else @Suppress("DEPRECATION") isFromMockProvider
```

---

## 4. Android 12-17 permissions and Play policy

### 4.1 Version-by-version summary

| API | Change | ParkPin impact | Status in repo |
|---|---|---|---|
| 31 (12) | Approximate vs precise choice in the dialog | The user can grant only coarse (about 3 km²). The app must still work and ask to upgrade. | Check `PermissionGate` for an upgrade prompt |
| 31 | `BLUETOOTH_CONNECT` runtime permission | Needed for bonded-device names and the ACL receiver | Declared |
| 31 | Background FGS start restrictions | Quick save via trampoline, CDM and expedited work | Done (QUICKSAVE.md) |
| 31 | Exact alarms need `SCHEDULE_EXACT_ALARM` | Meter reminder | WorkManager today, see §9.2 |
| 31 | `Location.isMock()` | §3.4 | Not used |
| 33 (13) | `POST_NOTIFICATIONS` | Meter and quick-save notifications | Declared |
| 33 | `requestAddTileService()` (`StatusBarManager`) | Prompt to add the QS tile from onboarding | **Not used. Add it.** |
| 34 (14) | FGS types are mandatory, plus `FOREGROUND_SERVICE_LOCATION`. WIU rules for location FGS. | QuickSaveService | Done |
| 34 | `SCHEDULE_EXACT_ALARM` denied by default for new installs ([docs](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)) | Meter reminder must ask or degrade | n/a |
| 34 | `startActivityAndCollapse(Intent)` deprecated → PendingIntent | Tile | Done |
| 34 | `AltitudeConverter` | §3.3 | Optional |
| 35 (15) | Edge-to-edge enforced (opt-out possible) | Compose insets | Check |
| 35 | `dataSync` FGS 6 h limit, no starts from `BOOT_COMPLETED` | None | n/a |
| 36 (16) | **Edge-to-edge opt-out removed. Predictive back on by default** (`onBackPressed` not called). **Orientation lock ignored on sw ≥ 600 dp.** ([behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16)) | AR screen must handle landscape on tablets and foldables (see §5.3). Back handling must use `BackHandler` / `OnBackPressedDispatcher`. | **Needed for the targetSdk 36 bump** |
| 36 | CDM `onDevicePresenceEvent`, `removeBond` | QUICKSAVE.md already notes the legacy callbacks | Revisit |
| 36 | Local network protection (opt-in) | None (no LAN) | n/a |
| 37 (17) | **Location button** (`USE_LOCATION_BUTTON`, `onlyForLocationButton` flag). Stricter minimum-scope Play policy. `ACCESS_LOCAL_NETWORK` runtime permission. | See §4.2 | Plan |

### 4.2 The 2026 precise-location policy (NEW)

- **Location button.** A system-rendered button gives *session-scoped precise location* after a direct tap. It ships as the Jetpack library `androidx.core:core-locationbutton` (`1.0.0-alpha01`, June 2026). On Android 16 and lower, it falls back to the normal permission dialog. "If your app targets Android 17 (API level 37) or later and only contains features that require session-based location access to function, Google Play policy requires you to use the location button" ([developer.android.com](https://developer.android.com/guide/topics/permissions/private-alternatives/location-button)).
- **Precise-location declaration.** *(via search)* Every app that requests `ACCESS_FINE_LOCATION` must complete a Play Console declaration: which features need it, and why coarse location or the location button is not enough. The form opens in **November 2026**. Compliance is mandatory from **27 January 2027**, and 30-day extensions are available ([Play Console Help 17033915](https://support.google.com/googleplay/android-developer/answer/17033915?hl=en); [policy announcement 29 Oct 2026](https://support.google.com/googleplay/android-developer/answer/18100766?hl=en-GB); corroborated by [epicollect5 #304](https://github.com/epicollect5/epicollect5-development-plan/issues/304) and [Moby #111](https://github.com/Xlient/Moby/issues/111)).

**How ParkPin's features map to the policy**

| Feature | Location need | Fits the location button? |
|---|---|---|
| "I parked here" in-app | One precise fix, user-initiated | **Yes** |
| Guide/AR/Map screens | **Continuous** precise location for minutes while walking | No. This is navigation-like, so it needs the declaration. |
| QS tile, widget, Bluetooth auto-save | Precise fix with no button visible | No. This needs the declaration. |
| History, settings | None | n/a |

**Recommendation.** ParkPin cannot be "button-only", so it must **file the declaration** with this justification: *"Pedestrian guidance back to a parked car needs precise, continuous foreground location. Coarse location (about 3 km²) cannot point to a car in a car park, and the location button covers one fix, not a walk."* Also adopt `LocationButton` on the Home screen as the first-run path. Users who decline the full dialog can still save a spot, and reviewers can see minimum scope.

```kotlin
// ui/home/HomeScreen.kt — first-run save path (library falls back to the normal dialog on ≤ API 36)
LocationButton(
    textType = LocationButtonTextType.UsePreciseLocation,
    onPermissionResult = { granted -> if (granted) viewModel.saveHere() else viewModel.onDenied() },
)
```

Do **not** add `android:usesPermissionFlags="onlyForLocationButton"`. It would break guidance.

### 4.3 Background location

- Play requires a permissions declaration form, a **≤ 30 s video** showing the prominent disclosure and runtime prompt and the background feature firing, and a blocking in-app disclosure that says "location" and "background / when the app is closed" ([Play Help 9799150](https://support.google.com/googleplay/android-developer/answer/9799150?hl=en); [Radar guide](https://radar.com/blog/guide-to-play-store-background-location-approval)).
- Automatic parking detection is an accepted kind of core feature. Google's own Activity Recognition reference uses parking detection as its example. Approval still costs a review cycle, and it is the most common reason for rejection.
- **Recommendation:** ship v1 **without** `ACCESS_BACKGROUND_LOCATION`. QUICKSAVE.md already allows this: the one-tap "Car disconnected · tap to save" notification is WIU-exempt. Add background location in a later release behind an opt-in screen, and record the review video then.

### 4.4 Activity Recognition (auto parking detection)

- Transition API: subscribe to `IN_VEHICLE` EXIT and `WALKING` ENTER ([guide](https://developer.android.com/develop/sensors-and-location/location/transitions)).
- Permission: `android.permission.ACTIVITY_RECOGNITION` is a runtime permission on API 29+. The older `com.google.android.gms.permission.ACTIVITY_RECOGNITION` is for 28 and below. Declare both.
- Detection latency varies by device. The callback is a `PendingIntent` broadcast, which is **not** an FGS or WIU exemption. Use the same `CarDisconnectHandler` decision tree.

```kotlin
// quicksave/DriveTransitionRegistrar.kt
private val transitions = listOf(
    ActivityTransition.Builder().setActivityType(DetectedActivity.IN_VEHICLE)
        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build(),
    ActivityTransition.Builder().setActivityType(DetectedActivity.WALKING)
        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
)

@RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
fun register(context: Context) {
    val pi = PendingIntent.getBroadcast(
        context, 0, Intent(context, DriveTransitionReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE, // GMS fills extras
    )
    ActivityRecognition.getClient(context)
        .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pi)
}

class DriveTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val events = ActivityTransitionResult.extractResult(intent)?.transitionEvents ?: return
        val exitedCar = events.any {
            it.activityType == DetectedActivity.IN_VEHICLE &&
                it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT
        }
        if (exitedCar) CarDisconnectHandler.handle(context, source = "activity")  // same tree as BT
    }
}
```

Re-register on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`. Registrations are lost on reboot.

### 4.5 Bluetooth

- `BLUETOOTH_CONNECT` (runtime, API 31+) is needed for `BluetoothDevice.getName()` and bonded-device lists. It is **not** needed for the ACL broadcast itself. On API 31+, the receiver gets `ACTION_ACL_DISCONNECTED` only when the permission is granted, so keep it.
- On Android 16+, handle `ACTION_KEY_MISSING`. A bond-loss event can cause a disconnect that is not a real "parked" event. Ignore disconnects that follow `ACTION_KEY_MISSING` ([behavior changes 16](https://developer.android.com/about/versions/16/behavior-changes-16)).

---

## 5. Compass and heading

### 5.1 Sensor options

| Source | Fusion | Pros | Cons | Use in ParkPin |
|---|---|---|---|---|
| `TYPE_ROTATION_VECTOR` | Accel + gyro + mag (HAL) | Smooth, absolute (magnetic north). `values[4]` = estimated heading accuracy (rad) when present. | Magnetic disturbance pulls it. Quality varies by OEM. | Current primary |
| `TYPE_GEOMAGNETIC_ROTATION_VECTOR` | Accel + mag, no gyro | Low power | Lower accuracy, jittery ([docs](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position)) | Fallback for devices without a gyro |
| `TYPE_GAME_ROTATION_VECTOR` | Accel + gyro, **no mag** | Not affected by magnetic fields | No north. Drifts like the gyro. | **Short-term stabiliser** in AR (see §5.4) |
| Accel + mag + `getRotationMatrix` | App-level | Works everywhere | Noisy, tilt errors | Current fallback |
| **`FusedOrientationProviderClient`** | Google fusion, GMS | Quaternion **plus heading and heading accuracy, referenced to true north** when declination is known. **No permissions.** Android 5+ with accel, gyro and mag. | Foreground only. GMS only. Update rate not guaranteed. | **Recommended primary on GMS devices** |

FOP facts: available since `play-services-location:21.2.0`. Use `LocationServices.getFusedOrientationProviderClient(context)`, `DeviceOrientationRequest.Builder(OUTPUT_PERIOD_DEFAULT)`, `requestOrientationUpdates(request, executor, listener)`, and `removeOrientationUpdates(listener)` ([Android Developers Blog, Mar 2024](https://android-developers.googleblog.com/2024/03/introducing-fused-orientation-provider-api.html?m=1); [ProAndroidDev](https://proandroiddev.com/update-for-your-compass-new-android-orientation-api-dc4e5c25ca35)).

```kotlin
// sensors/FusedHeadingSource.kt — drop-in alternative behind the same Flow<Heading> contract
class FusedHeadingSource(context: Context) {
    private val client = LocationServices.getFusedOrientationProviderClient(context)

    fun headingUpdates(): Flow<Heading> = callbackFlow {
        val listener = DeviceOrientationListener { o: DeviceOrientation ->
            // headingDegrees is already true-north when FOP knows declination (it does once it has a fix)
            val q = o.attitude // [x, y, z, w]
            val pitch = pitchFromQuaternion(q)        // see §5.3 for camera-axis pitch
            trySend(
                Heading(
                    azimuthDeg = o.headingDegrees,
                    pitchDeg = pitch,
                    rollDeg = 0f,
                    accuracy = when {
                        o.headingErrorDegrees <= 10f -> SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                        o.headingErrorDegrees <= 25f -> SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                        else -> SensorManager.SENSOR_STATUS_ACCURACY_LOW
                    },
                ),
            )
        }
        val request = DeviceOrientationRequest.Builder(DeviceOrientationRequest.OUTPUT_PERIOD_DEFAULT).build()
        client.requestOrientationUpdates(request, Dispatchers.Default.asExecutor(), listener)
            .addOnFailureListener { close(it) }
        awaitClose { client.removeOrientationUpdates(listener) }
    }
}
```

Expose the accuracy as **degrees** in `Heading` (add `accuracyDeg: Float?`). Then the UI can draw a heading-uncertainty cone around the arrow. This is better than the 4-level enum. On the platform path, use `values[4]` of `TYPE_ROTATION_VECTOR` when `values.size > 4 && values[4] >= 0`. The current code ignores it.

### 5.2 Declination

- `android.hardware.GeomagneticField` **still uses WMM-2020** ("valid until 2025, but should produce acceptable results for several years after that") ([reference](https://developer.android.com/reference/android/hardware/GeomagneticField)).
- WMM2025 was released on 17 Dec 2024 and is valid through 2029 ([NOAA NCEI](https://www.ncei.noaa.gov/news/world-magnetic-model-2025-released)). The 2020→2025 secular change is typically well under 1° at mid-latitudes, and larger near the poles.
- **Recommendation:** keep `GeomagneticField`. The error is small next to magnetometer error (5-15° indoors). If FOP is used, it handles declination itself. Optionally ship a WMM2025 coefficient file (public domain, about 10 KB) in `domain/` as pure Kotlin for the AR toolbox's star-finder later.

### 5.3 remapCoordinateSystem and display rotation

The current `CompassSensor` uses `remapCoordinateSystem(R, AXIS_X, AXIS_Z)` for camera mode. That is correct **only in portrait (ROTATION_0)**. Android 16 ignores orientation locks on sw ≥ 600 dp, and foldables and tablets will rotate the AR screen. **Pick the axes from `display.rotation`:**

```kotlin
fun cameraRemap(rotation: Int): Pair<Int, Int> = when (rotation) {
    Surface.ROTATION_0   -> SensorManager.AXIS_X       to SensorManager.AXIS_Z
    Surface.ROTATION_90  -> SensorManager.AXIS_Z       to SensorManager.AXIS_MINUS_X
    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Z
    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Z to SensorManager.AXIS_X
    else -> SensorManager.AXIS_X to SensorManager.AXIS_Z
}
// val (x, y) = cameraRemap(context.display.rotation)  // API 30+, else windowManager.defaultDisplay
// SensorManager.remapCoordinateSystem(rotation, x, y, remapped)
```

The same applies to flat-compass mode (`AXIS_Y, AXIS_MINUS_X` for 90°, and so on). Add a JVM unit test using synthetic rotation matrices. The remap is pure math once the matrix is passed in.

### 5.4 Interference and calibration UX

- **Interference sources in car parks:** steel-reinforced concrete pillars, the car itself (sitting in the car can shift heading 10-30°), magnetic phone cases and mounts, and nearby EV chargers.
- **Detect it.** Compare `|B|` from `TYPE_MAGNETIC_FIELD` with `GeomagneticField.fieldStrength` (nT → µT). Flag disturbance when the measured magnitude differs from expected by more than 25%, or when the inclination is off by more than 10°. This is more reliable than `SENSOR_STATUS_*`, which many OEMs pin at HIGH (the code comment already notes this).
- **Calibration UX:**
  1. Show a figure-8 animation only when disturbance or low accuracy lasts more than 3 s. This exists as `CompassCalibrationDialog`.
  2. Show the arrow **as a cone**, with width equal to the heading error. Users then understand uncertainty without a modal.
  3. During disturbance in AR, **freeze absolute heading and integrate `GAME_ROTATION_VECTOR` yaw deltas** (gyro only) until the field is clean again. This is a simple complementary filter and fixes "arrow swings near a pillar".
  4. When walking (speed > 0.8 m/s and GPS accuracy < 15 m), the **GPS course over ground** (`Location.bearing`) is a magnetic-free heading cue. Blend it in when the phone points roughly along the walking direction.

```kotlin
// domain/HeadingFuser.kt — pure Kotlin, unit-testable
class HeadingFuser(private val tauSec: Double = 2.0) {
    private var fused: Double? = null
    private var lastGyroYaw: Double? = null

    /** magHeading: absolute (true north) or null when disturbed; gyroYaw: GAME_ROTATION_VECTOR yaw. */
    fun update(magHeading: Double?, gyroYaw: Double, dtSec: Double): Double? {
        val dYaw = lastGyroYaw?.let { GeoMath.relativeBearingDeg(gyroYaw, it) } ?: 0.0
        lastGyroYaw = gyroYaw
        val predicted = fused?.let { GeoMath.normalizeDeg(it + dYaw) } ?: magHeading ?: return null
        fused = if (magHeading == null) predicted else {
            val k = dtSec / (tauSec + dtSec)                       // complementary gain
            GeoMath.normalizeDeg(predicted + k * GeoMath.relativeBearingDeg(magHeading, predicted))
        }
        return fused
    }
}
```

---

## 6. Offline maps

### 6.1 osmdroid status

- **Archived** by its owner on **20 Nov 2024**. The repository is read-only and "will no longer receive updates or new releases" ([README](https://github.com/osmdroid/osmdroid/blob/master/README.md); [OSM wiki](https://wiki.openstreetmap.org/wiki/Osmdroid)). The last artifact is `org.osmdroid:osmdroid-android:6.1.20` (Maven Central, Aug 2024).
- It still works. It is View-based, raster only, and has known edge-to-edge and Compose interop quirks. There will be no fixes for API 36/37 regressions.

### 6.2 OSM tile usage policy: legal constraint

From `tile.openstreetmap.org`'s policy *(via search: [OSMF tile policy](https://operations.osmfoundation.org/policies/tiles/); [OSM blog](https://blog.openstreetmap.org/tag/bulk-download/); [Blocked tiles wiki](https://wiki.openstreetmap.org/wiki/Blocked_tiles))*:

- **Bulk downloading is prohibited**, meaning "any pre-emptive fetching of tiles other than those a user is actively viewing". "Download city/country for offline use" features are named as prohibited. Older text set a 250-tile limit at z ≥ 13; current text has no threshold.
- You must send a **valid, identifying User-Agent** and honour HTTP caching (a 7-day minimum is quoted) with at most 2 connections.
- **Visible attribution:** "© OpenStreetMap contributors" linked to the copyright page.
- OSMF's **vector** tile service bans offline archives too ([vector policy](https://operations.osmfoundation.org/policies/vector/)).
- Apps that make "exceptional demands in aggregate" get blocked. A block hits **all** ParkPin users at once.

**Implications for this repo**

| Item | Status | Action |
|---|---|---|
| ROADMAP Phase 4: "Offline map region download (osmdroid CacheManager for bounding box around spot)" | **Violates policy** if pointed at `tile.openstreetmap.org` | Remove it, or switch it to a provider that allows downloads |
| `userAgentValue = packageName` | Acceptable, but weak | Use `"ParkPin/${BuildConfig.VERSION_NAME} (+https://github.com/nikhil914/car-park-locator)"` |
| "Tiles you have viewed are cached" (README) | Allowed: viewing-time caching | Keep. Make sure the cache honours `Expires` (osmdroid does by default). |
| Attribution | Check `MapScreen` | Add a `CopyrightOverlay` or a Compose text overlay |

### 6.3 Options

| Option | Rendering | Offline method | Data source and licence | Size (city) | Compose | Maintenance (2026) | Verdict |
|---|---|---|---|---|---|---|---|
| osmdroid 6.1.20 | Raster | Viewing cache only (OSM). MBTiles/SQLite archives from an allowed provider. | Any raster | ~50-200 MB raster | AndroidView | **Archived** | Short-term only |
| **MapLibre Native Android 13.7.1** | Vector (GL/Vulkan) | `OfflineManager` regions (style + bbox + zoom), **or** local `pmtiles://file://…` | OpenFreeMap (MIT; OSM ODbL data, OpenMapTiles attribution), Protomaps builds, MapTiler (commercial) | ~5-30 MB vector | AndroidView (community Compose wrappers exist) | Very active. Maven Central shows 13.7.1 plus 2026.x "globe" builds. | **Recommended** |
| Mapsforge 0.25.0 / VTM | Vector (on-device render) | `.map` files (one file per region) | OpenAndroMaps / mapsforge downloads (ODbL data) | ~10-60 MB per region | AndroidView | Active but slow. LGPL-3 with linking exceptions ([repo](https://github.com/mapsforge/vtm)). | Good fully-offline alternative. Older look. |
| Google Maps SDK | Vector | No real offline for SDK apps | Google ToS | n/a | maps-compose | Active | Rejected: needs an API key and network, against the privacy stance |

Key MapLibre facts:

- PMTiles is supported from MapLibre Android **11.7.0**. Use `pmtiles://file:///…` for device storage. `pmtiles://asset://` does **not** work, because the asset source has no range reads. **PMTiles sources cannot be used with offline packs or the ambient cache.** Ship or download the file yourself *(via search: [MapLibre PMTiles example](https://maplibre.org/maplibre-native/android/examples/data/PMTiles/); [MapAtlas docs](https://docs.mapatlas.xyz/overview/sdk/android-native/data/PMTiles))*.
- `OfflineManager` regions are bounding boxes with a zoom range and pixel ratio. Deleting a region frees only resources that no other region uses ([Flutter MapLibre offline docs](https://maplibre.org/flutter-maplibre-gl/advanced/offline-regions/)).
- **OpenFreeMap** has no request limits and no API key. It publishes **weekly full-planet MBTiles/PMTiles downloads**. Attribution is required: "OpenFreeMap © OpenMapTiles Data from OpenStreetMap" ([OpenFreeMap README](https://github.com/hyperknot/openfreemap)). This makes regional offline packs **legal**. For example, run `pmtiles extract` on a city bbox from a self-hosted planet file, or use `OfflineManager` against the OpenFreeMap style. Their docs ask heavy users to self-host, so keep regions small (2 km around the spot).

### 6.4 Recommendation

1. **Now (v0.x):** keep osmdroid. Fix the User-Agent and attribution. **Delete the CacheManager-bbox roadmap item.** Offline already degrades to "grid + markers + straight line", which is what a car-park guide needs within about 500 m.
2. **Phase 4: migrate to MapLibre Native 13.x** behind a `MapRenderer` interface in `ui/map/`, so the domain is untouched.
   - Online: OpenFreeMap "liberty" or "positron" style.
   - **"Save area for offline" when parking:** `OfflineManager.createOfflineRegion` with a bbox of about 1.5 km around the spot at z12-z17, triggered **only on Wi-Fi or unmetered networks**, and **only against a provider whose terms allow it** (OpenFreeMap, or a self-hosted PMTiles server). About 2-6 MB per region.
   - Optional: a user-imported `.pmtiles` file (SAF picker → copy to `filesDir` → `pmtiles://file://`) for fully offline travel.
3. Keep **Mapsforge** as plan B if the MapLibre APK size (+8-12 MB of native libs per ABI) is unacceptable. Use App Bundle ABI splits either way.

```kotlin
// ui/map/OfflineRegionDownloader.kt (MapLibre) — sketch
fun downloadAroundSpot(ctx: Context, spot: GeoPoint, styleUrl: String) {
    val d = 0.0135 // ≈1.5 km
    val bounds = LatLngBounds.from(spot.lat + d, spot.lng + d, spot.lat - d, spot.lng - d)
    val def = OfflineTilePyramidRegionDefinition(styleUrl, bounds, 12.0, 17.0, ctx.resources.displayMetrics.density)
    OfflineManager.getInstance(ctx).createOfflineRegion(def, "spot-${spot.lat},${spot.lng}".toByteArray(),
        object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(region: OfflineRegion) { region.setDownloadState(OfflineRegion.STATE_ACTIVE) }
            override fun onError(error: String) { /* log, no retry storm */ }
        })
}
```

Licensing checklist: show OSM ODbL attribution on the map at all times, add OpenMapTiles/OpenFreeMap credit, and add a third-party licence screen for MapLibre (BSD-2) or Mapsforge (LGPL-3).

---

## 7. AR guidance

### 7.1 Sensor-overlay math (current approach, no ARCore)

Inputs: user fix `U`, spot `S`, camera azimuth `ψ` (true north), camera pitch `θ` (elevation of the optical axis), roll `φ`, and the displayed FOV `(hFov, vFov)`.

1. Bearing `β = initialBearing(U, S)` and distance `d = haversine(U, S)`.
2. Target elevation `ε = atan2(Δh − eyeHeight, d)`, where `Δh` = spot altitude − user altitude (barometric if available), and `eyeHeight` ≈ 1.5 m. For distances over 30 m, `ε` is about 0.
3. Relative angles: `Δψ = wrap(β − ψ)`, `Δθ = ε − θ`.
4. Pinhole projection, in half-screen units: `x' = tan(Δψ)/tan(hFov/2)`, `y' = −tan(Δθ)/tan(vFov/2)`.
5. **Roll compensation (missing today):** rotate `(x', y')` by `−φ` about the centre: `x = x'cosφ + y'sinφ`, `y = −x'sinφ + y'cosφ`.
6. `px = cx·(1 + x)`, `py = cy·(1 + y)`. If `|Δψ| ≥ 90°` or the point lies outside the inset rectangle, draw an **edge indicator** at the border intersection of the ray. `ArProjection` already does this.

**The FOV must match what is displayed.** PreviewView in `FILL_CENTER` crops the sensor image, so the visible FOV is smaller than the sensor FOV on one axis.

```kotlin
// ar/CameraFov.kt — from Camera2 characteristics (CameraX doesn't expose FOV)
fun displayedFov(chars: CameraCharacteristics, viewW: Int, viewH: Int, sensorRotationDeg: Int): Pair<Double, Double> {
    val f = chars[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]!!.min()          // mm, widest
    val size = chars[CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE]!!                     // mm
    val (sw, sh) = if (sensorRotationDeg % 180 == 0) size.width to size.height else size.height to size.width
    var h = 2 * atan(sw / (2.0 * f)); var v = 2 * atan(sh / (2.0 * f))
    val viewAspect = viewW.toDouble() / viewH; val sensorAspect = sw / sh.toDouble()
    if (viewAspect > sensorAspect) v = 2 * atan(tan(h / 2) / viewAspect)   // FILL_CENTER crops top/bottom
    else h = 2 * atan(tan(v / 2) * viewAspect)                              // crops left/right
    return Math.toDegrees(h) to Math.toDegrees(v)
}
```

The formula `FOV = 2·atan(d / 2f)` and its caveats (crop region, physical vs logical camera on multi-camera phones) come from [CameraX developers group](https://groups.google.com/a/android.com/g/camerax-developers/c/cwodxv7kLhA) and the AOSP code mirrored on [e.foundation GitLab](https://gitlab.e.foundation/e/os/android_frameworks_av/-/commit/86150f33380a93cbc0d1554f5bcd0af2d3ebf246). On logical multi-camera devices, read the **active physical camera** via `Camera2CameraInfo`, and treat zoom ratio as dividing `tan(FOV/2)`.

**Error budget.** Heading ±10° and GPS ±8 m at 40 m put the pin off by about 15° → roughly 1/4 of the screen width. **Draw the pin as a soft halo sized by the angular uncertainty:** `atan(acc/d) + headingErr`. It tightens as the user gets closer, which is honest and reads well.

### 7.2 ARCore Geospatial API

| Aspect | Detail |
|---|---|
| What | VPS (Street View imagery) + GNSS + ARCore tracking → geodetic camera pose. Anchors: WGS84, Terrain, Rooftop. Streetscape Geometry (building and terrain meshes). |
| Accuracy | "Under typical conditions … positional accuracy of approximately 5 meters, and rotational accuracy of 5 degrees", often better where VPS localizes. Google's sample gates placement at 10 m and 15° ([developer guide via search](https://developers.google.cn/ar/develop/unity-arf/geospatial/developer-guide?hl=en)). |
| Coverage | Outdoors, where Street View exists. **Not in underground garages.** Check with `Session.checkVpsAvailabilityAsync`. |
| Network | **Required** (VPS is a cloud service). Contradicts "fully offline", so it must be opt-in. |
| Auth | Google Cloud project with the ARCore API enabled. API key, or keyless (OAuth, signing certificate). An in-app prompt is required to acknowledge device data use. |
| Quota and cost | 1,000 new sessions/min or 100,000 requests/min **per project** ([quota page](https://developers.google.cn/ar/develop/java/geospatial/api-usage-quota)). No published price; described as free *(unverified, [HackMD](https://hackmd.io/@jGfbfuUQRZu32YHgto8HVA/rJegMvZ2D6))*. |
| Device | ARCore-supported devices only (a subset of the market). APK +~5 MB; Google Play Services for AR. |
| Privacy | Camera frames are sent to Google for localization. **The Data safety form changes** (§10). |

**Verdict:** this is a premium, opt-in "precision mode" for outdoor open-air lots, after the core is solid. It does not solve the main pain point (garages). Defer it to Phase 5. Put it in a separate dynamic feature module (`:feature:geospatial`) so the base APK and its Data safety answers stay clean.

### 7.3 SceneView

- `io.github.sceneview:arsceneview` **4.66.0** (Maven Central, 4 Nov 2026). Apache-2.0, Filament renderer, stable Compose API (`ARSceneView` composable). Geospatial nodes: `TerrainAnchorNode`, `RooftopAnchorNode`, `StreetscapeGeometryNode` ([GitHub](https://github.com/SceneView/sceneview-android)).
- It is very actively released, so pin the exact version.
- **Recommendation:** if or when Geospatial ships, use SceneView rather than raw ARCore + OpenGL. Do **not** add it for the sensor overlay. A Compose `Canvas` over `PreviewView` is lighter (no Filament, about 0 MB extra) and works on non-ARCore phones.

---

## 8. Indoor and underground

### 8.1 Barometer floor estimation

- Absolute pressure differs by up to about 3 hPa between phones on the same table, and weather moves it by tens of hPa. **Only relative deltas over minutes are usable** ([SO via mirror](https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/31541658/android-barometer-altitude-reading-is-wrong)). Academic work: "no approach … near 100% accuracy", and pressure-pair maps improve it ([FloorPair, PMC6720727](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6720727/)).
- Physics: about 0.12 hPa per metre near sea level. Garage levels are about 2.8-3.5 m apart, which is about 0.35-0.42 hPa per level. Phone barometer relative noise is about 0.01-0.03 hPa after a 1-2 s average, so **level changes are clearly resolvable** over a few minutes.
- **Recommended algorithm** (refines the existing `BarometricFloorEstimator`):
  1. **Reference at entry.** While the car is moving and GPS is good (accuracy < 15 m, speed > 2 m/s), keep a rolling median of pressure as `p_street`. The quick-save FGS or the Bluetooth trigger can sample the barometer for 5 s.
  2. **At save:** `Δh = 44330·(1 − (p_save/p_street)^(1/5.255))` (this is `SensorManager.getAltitude(p_street, p_save)`), and `level ≈ round(Δh / 3.0)`. Show "≈ P2 (−6 m)", and let the user correct it. The corrected value becomes `floorLabel`.
  3. **At return:** sample `p_return_street` at the entrance. Compute the target pressure `p_target = p_return_street · (p_save/p_street)`, which cancels weather drift. Guide "go down 2 levels" or "you're on the right level" using the live delta.
  4. Discard references older than 2 h without a fresh street sample. Weather fronts move about 1 hPa/h, which is about 2-3 levels.

```kotlin
// domain/BarometricFloorEstimator.kt (pure) — weather-compensated level delta
fun levelsToGo(pStreetAtPark: Float, pAtCar: Float, pStreetNow: Float, pNow: Float, levelHeightM: Double = 3.0): Int {
    val pTargetNow = pStreetNow * (pAtCar / pStreetAtPark)         // scale for weather drift
    val dhM = altitudeDelta(pNow, pTargetNow)                       // + = car is above you
    return (dhM / levelHeightM).roundToInt()
}
fun altitudeDelta(p0: Float, p: Float) = 44330.0 * (1 - (p / p0).toDouble().pow(1 / 5.255))
```

### 8.2 Pedestrian dead reckoning (PDR)

- Smartphone PDR results in the literature: about 0.5-5% of distance walked under good conditions. Heading drift is the dominant long-term error, about 5° over 20 min when magnetic quasi-static periods recalibrate the gyro ([Sciforum 11302](https://sciforum.net/paper/view/11302); [Tampere IPIN post-print](https://trepo.tuni.fi/bitstream/10024/129025/1/IPIN_Racko_PostPrint.pdf); [PMC6210328](https://pmc.ncbi.nlm.nih.gov/articles/PMC6210328)).
- In a 100 m garage walk, 3% is about 3 m. That is good enough to say "the car is 20 m ahead-left".
- **Building blocks:** `TYPE_STEP_DETECTOR` (hardware, low power, about 2-3% step-count error), step length `≈ 0.415 × height` or Weinberg (`K·(a_max − a_min)^¼`), and heading from §5.4 (the gyro-stabilised fuser, since garage magnetics are bad).
- **Use in ParkPin:**
  - *Outbound breadcrumb:* after an indoor save, keep PDR running while the guide or the FGS is up, until GPS is good again (accuracy < 15 m). That anchors the PDR track to the outdoor fix and corrects the **car position** backwards. This "reverse anchoring" is the key trick: the car's true position = first good outdoor fix − PDR displacement.
  - *Inbound:* when GPS dies at the garage entrance, switch guidance to PDR from the last good fix.
- `TYPE_STEP_DETECTOR` and `TYPE_STEP_COUNTER` need `ACTIVITY_RECOGNITION` at runtime on API 29+. That is the same permission as §4.4, so ask once for "motion".

### 8.3 Wi-Fi RTT

- 802.11mc FTM: Android's 90th-percentile target is about 2 m at 80 MHz ([AOSP Wi-Fi RTT](https://source.android.google.cn/docs/core/connect/wifi-rtt?authuser=0)). 802.11az support exists from Android 15. It needs **at least 3 RTT-capable APs**, and their locations must be known (LCI) or surveyed. Car parks almost never have them ([CrowdConnected](https://crowdconnected.com/blog/when-will-cisco-and-aruba-switch-on-wifi-rtt-for-indoor-positioning)).
- It needs `ACCESS_FINE_LOCATION` and, on 33+, `NEARBY_WIFI_DEVICES`.
- **Verdict: not worth it** for ParkPin. Revisit only for a "pin anything" mall or airport mode.

### 8.4 Last good fix

This is the cheapest, highest-impact indoor feature.

```kotlin
// location/FixHistory.kt — in-memory ring buffer fed by any active location flow
class FixHistory(private val windowMs: Long = 5 * 60_000) {
    private val fixes = ArrayDeque<LocationFix>()
    fun add(f: LocationFix) { fixes.addLast(f); while (fixes.first().timeMs < f.timeMs - windowMs) fixes.removeFirst() }
    /** Best outdoor-looking fix in the window: accurate and recent. */
    fun lastGood(maxAccM: Float = 12f): LocationFix? = fixes.lastOrNull { it.accuracyM <= maxAccM }
}
```

UX at save time, when the current accuracy is over 25 m and `lastGood` is less than 3 min old: *"Signal is weak here. Use your position from 1 min ago at the garage entrance (±6 m)?"* Store both positions, and the entrance becomes a waypoint for the return trip ("walk to the entrance, then go down to P2").

---

## 9. Battery, scheduling and surfaces

### 9.1 Battery budget

| Mode | Sensors | Expected draw | Rule |
|---|---|---|---|
| Idle | None | 0 | No background location by default (ROADMAP quality bar) |
| Save (≤ 15 s) | GNSS high accuracy, barometer 5 s | Tiny | Stop early at ≤ 10 m (already done) |
| Guide screen | GNSS 1 Hz, rotation vector at `SENSOR_DELAY_UI` (~60 ms) | ~moderate | `repeatOnLifecycle(STARTED)`. Drop to 2 Hz GNSS when `d > 300 m`. |
| AR | Camera + GNSS + sensors | High | Auto-exit AR after 2 min stationary, or when the screen is off |
| Activity Recognition | Low-power hub | Very low | Opt-in |
| CDM presence | System | ~0 | Preferred auto trigger |

Use `LocationRequest.Builder(...).setMinUpdateDistanceMeters(1f)` and `setWaitForAccurateLocation(true)` during save. On API 31+, check `PowerManager.getLocationPowerSaveMode()`. If it returns `LOCATION_MODE_FOREGROUND_ONLY` or GPS is disabled under Battery Saver, show a chip ([permissions guide](https://developer.android.com/develop/sensors-and-location/location/permissions)).

### 9.2 Meter reminder: WorkManager vs AlarmManager

| API | Fires in Doze? | Precision | Permission | Fit |
|---|---|---|---|---|
| `WorkManager` delayed `OneTimeWorkRequest` (**current**) | Deferred to maintenance windows | Minutes to **hours** late | None | **Poor** for "meter expires at 14:30" |
| `AlarmManager.setWindow` | No (unless `AndAllowWhileIdle`) | ≥ 10 min window on 12+ | None | OK if the lead time is ≥ 15 min |
| `setAndAllowWhileIdle` | Yes (rate-limited ~1/9 min) | Inexact (≤ 1 h typical) | None | Fallback |
| `setExactAndAllowWhileIdle` | Yes | Exact | `SCHEDULE_EXACT_ALARM` (denied by default on 14+ for new installs) | Good |
| `setAlarmClock` | Yes, shows the alarm icon | Exact, highest priority | `SCHEDULE_EXACT_ALARM` | **Best UX** for "your meter expires" |

Sources: [Schedule alarms](https://developer.android.com/training/scheduling/alarms); [Android 14 exact alarms](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms). `USE_EXACT_ALARM` is **only for alarm clock and calendar apps**. Do not declare it, or Play will reject the app.

**Recommendation:** add `SCHEDULE_EXACT_ALARM` and gate on `canScheduleExactAlarms()`. If it returns true, use `setAlarmClock` (showing the spot) at `expiry − lead`. If false, use `setWindow(expiry − lead − 10 min, 10 min)` and show a one-time card: "Allow exact reminders?" → `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. Listen for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` to reschedule. Keep WorkManager only for follow-up work. Also reschedule on `BOOT_COMPLETED`, `TIME_SET` and `TIMEZONE_CHANGED`, because alarms do not survive a reboot.

```kotlin
fun scheduleMeterAlarm(ctx: Context, spotId: Long, fireAtMs: Long) {
    val am = ctx.getSystemService(AlarmManager::class.java)
    val op = PendingIntent.getBroadcast(ctx, spotId.toInt(),
        Intent(ctx, MeterAlarmReceiver::class.java).putExtra("spotId", spotId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
        val show = PendingIntent.getActivity(ctx, 0, MainActivity.guideIntent(ctx, spotId), PendingIntent.FLAG_IMMUTABLE)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(fireAtMs, show), op)
    } else {
        am.setWindow(AlarmManager.RTC_WAKEUP, fireAtMs - 10 * 60_000, 10 * 60_000, op)
    }
}
```

### 9.3 Quick Settings tile

- Already implemented (QUICKSAVE.md). **Add `StatusBarManager.requestAddTileService()`** (API 33+) to onboarding and settings. It shows a system prompt so users don't need to edit the QS panel.

```kotlin
if (Build.VERSION.SDK_INT >= 33) {
    ctx.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(ctx, ParkTileService::class.java), ctx.getString(R.string.quicksave_tile_label),
        Icon.createWithResource(ctx, R.drawable.quicksave_ic_car), ctx.mainExecutor) { result -> /* log */ }
}
```

- Mark the tile `android:exported="true"` with `BIND_QUICK_SETTINGS_TILE` (done). Consider `META_DATA_ACTIVE_TILE` so the tile updates only on `requestListeningState` (saves binder churn).

### 9.4 Widgets: RemoteViews vs Glance

- Glance `1.2.0` is stable (Aug 2026). It provides **generated previews** via `GlanceAppWidget.providePreview` on Android 15+ (compileSdk ≥ 35; the system limits `setWidgetPreviews` to about 2 calls/h) ([generated previews](https://developer.android.com/develop/ui/compose/glance/generated-previews)).
- **Recommendation:** migrate `ParkWidgetProvider` to Glance when adding a second widget size. The 2×2 could show "Car: 240 m NE · Meter 12 min" with a static arrow updated on save. **Do not** live-update heading in a widget, since that would need background sensors. Keep the RemoteViews version until then; it works and costs nothing.

### 9.5 Wear OS

- A standalone Wear app (`wear.compose 1.7.1`, `wear.tiles 1.6.2`, `wear.protolayout 1.4.2`) with **a Tile showing distance and direction** plus a **complication** ("Car 240 m"). Tapping opens a full-screen arrow using the watch's own GPS and compass, or phone-relayed data via the Data Layer.
- Data Layer sync of the active spot (`DataClient.putDataItem("/spot/active")`) stays on the user's devices. It does not count as "shared" in Data safety, but disclose the transfer in the privacy policy.
- Many watches have no magnetometer, so fall back to "direction relative to your walking direction" using GPS course.
- Effort is medium-high. Place it after the core and AR are solid (§11).

### 9.6 Android Auto

- **Parking** is an approved Car App Library category (POI: "such as parking and charging"). Use `PlaceListMapTemplate` with `androidx.car.app.category.POI`. car-app `1.7.0` is stable ([CAL overview](https://developer.android.com/design/ui/cars/guides/templates/overview); [POI guide](https://developer.android.com/training/cars/apps/poi)).
- A minimal Auto screen gives ParkPin a **reliable "parked" trigger**: the `CarAppService` session ends (`onDestroy`) when the phone leaves the car. Offer a "Save parking now" action at destination, as Google Maps does. Note that Android Auto apps need a separate Play review track. Wear OS and Automotive need target API 35 ([Play target SDK](https://developer.android.com/google/play/requirements/target-sdk)).

---

## 10. Privacy and Play Data safety

Data that is **processed only on the device and never transmitted** does not need to be declared as "collected" ([AppsFlyer](https://support.appsflyer.com/hc/en-us/articles/4417197430289); [AppMetrica](https://appmetrica.yandex.com/docs/en/data-security/google-data-safety)). Jetpack libraries send no data, so they do not change the form ([AndroidX versions page](https://developer.android.com/jetpack/androidx/versions)). Google Play services location runs on the device, but Google's own collection is covered by Google's own disclosure. You still declare what *your app* sends.

### 10.1 Recommended answers (current build: osmdroid, no analytics, no ARCore)

| Question | Answer | Reason |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Spots, photos, notes and settings stay in Room or app storage |
| Map tile requests | Not "collected". The tile server sees IP + tile x/y/z (≈ viewed area). | Covered by the privacy policy. **Disclose that the map fetches tiles from a third-party server.** IP + area is arguably approximate location sent to a third party. The conservative choice is to declare *Approximate location → Shared → App functionality, not processed ephemerally: No* **or** default the map to offline/grid until the user enables online tiles. |
| Is data encrypted in transit? | Yes (HTTPS tiles) | |
| Can users request deletion? | Yes (in-app "Delete all" + uninstall) | Matches the ROADMAP quality bar |
| Independent security review | No | |
| Account creation | Not required | |
| Families policy | Not targeted at children | |

### 10.2 Changes when features are added

| Feature | Data safety delta |
|---|---|
| Reverse geocoding (`ParkingSpot.address`) via `android.location.Geocoder` | Geocoder sends coordinates to the platform backend (Google on GMS). Declare **Precise location → Shared (with service provider) → App functionality, ephemeral** if the app triggers it. Or keep it opt-in. |
| ARCore Geospatial | Camera images + location sent to Google: **Photos/videos?** (frames are transient) and **Precise location → Shared → App functionality**. Add the in-app consent prompt. |
| Crash reporting (e.g. Firebase Crashlytics) | **Crash logs, Diagnostics → Collected.** Avoid: it breaks the "no analytics" promise. Prefer Play Console vitals. |
| Wear Data Layer sync | Not "shared" (same user, Google-mediated transport). Mention it in the privacy policy. |
| Share spot as `geo:` link | User-initiated sharing to an app of their choice is **not** "sharing" for Data safety. |

Also update `docs/PRIVACY.md`. **Precise location is needed for the Play Console declaration (§4.2)**, and the privacy policy URL must describe on-device processing and the third-party tile server.

---

## 11. New feature suggestions

Impact: 1-5 (user value × differentiation). Effort: S (≤ 2 days), M (≤ 1 week), L (≤ 3 weeks), XL (more).

| # | Feature | Impact | Effort | Depends on | Notes |
|---|---|---|---|---|---|
| 1 | **Play precise-location declaration + `LocationButton` first-run** | 5 (ship-blocker) | S | §4.2 | Deadline 27 Jan 2027 |
| 2 | **targetSdk 36 + toolchain upgrade** (edge-to-edge, predictive back, rotation-aware AR) | 5 (ship-blocker) | M | §12 | Play requires API 36 for updates |
| 3 | **Last-good-fix + garage entrance waypoint** | 5 | S | §8.4 | Biggest real-world win in garages |
| 4 | **Weather-compensated floor guidance** ("go down 2 levels") | 5 | M | §8.1, existing barometer | Unique among competitors |
| 5 | **Exact meter alarm (`setAlarmClock`)** | 4 | S | §9.2 | Fixes late reminders |
| 6 | **FusedOrientationProvider + uncertainty cone** | 4 | S | §5.1 | Better compass on most phones |
| 7 | **Gyro-stabilised heading near pillars** (`HeadingFuser`) | 4 | M | §5.4 | Pure Kotlin, testable |
| 8 | **Roll-aware AR projection + displayed-FOV calc** | 4 | S | §7.1 | Fixes pin drift when tilted |
| 9 | **Activity Recognition "you parked?" suggestion** | 4 | M | §4.4 | Opt-in, no background location |
| 10 | **`requestAddTileService` onboarding** | 3 | S | §9.3 | Better discoverability |
| 11 | **PDR reverse anchoring** (correct indoor car position from the first outdoor fix) | 4 | L | §8.2 | High "magic" factor |
| 12 | **MapLibre migration + legal offline region around spot** | 4 | L | §6 | Replaces the archived osmdroid |
| 13 | **Photo OCR of the pillar sign** (ML Kit Text Recognition v2, on-device, bundled model) → auto-fill `floorLabel` "P2 C14" | 3 | M | CameraX capture | Bundled model keeps it offline (+~4 MB per script) |
| 14 | **TalkBack clock-direction + haptic "warmer/colder"** | 3 | S | `ClockDirection.kt` exists | Accessibility quality bar |
| 15 | **Glance 2×2 widget with distance and meter** | 3 | M | §9.4 | |
| 16 | **Share spot** (geo: URI + plain text + QR for family) | 3 | S | | Not a Data-safety "share" |
| 17 | **Android Auto POI screen** (save at destination, reliable parked trigger) | 3 | L | §9.6 | Separate review |
| 18 | **Wear OS tile + complication + arrow** | 3 | L | §9.5 | |
| 19 | **"Pin anything" profiles** (car, bike, tent) | 3 | M | Room migration (`category`) | Toolbox direction |
| 20 | **ARCore Geospatial precision mode** (opt-in, online, outdoor) | 2 | XL | §7.2 | Dynamic feature module |
| 21 | Wi-Fi RTT | 1 | L | §8.3 | Skip |

**Suggested order:** Sprint A (compliance): 1, 2. Sprint B (garage magic): 3, 4, 5, 6, 8, 10. Sprint C: 7, 9, 14, 16. Then 12, 11, 13, 15, then the platform expansions 17-20.

---

## 12. Recommended changes to architecture

### 12.1 Dependency versions (checked 2026-11-05)

Stable versions are from the [AndroidX versions table](https://developer.android.com/jetpack/androidx/versions) (updated 4 Nov 2026), the [AGP release notes](https://developer.android.com/build/releases/gradle-plugin), the [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping), [Kotlin 2.4.20 release](https://blog.jetbrains.com/kotlin/2026/09/kotlin-2-4-20-released/), and Maven Central metadata (KSP, osmdroid, coroutines, MapLibre, Mapsforge, SceneView). Google Maven was not reachable, so check `play-services-location` and the exact AGP patch version in Android Studio.

| Catalog key | Current | Latest stable | Notes |
|---|---|---|---|
| `agp` | 8.7.3 | **9.5.0** (Gradle ≥ 9.6.0, JDK 17, max API 37) | AGP 9 has **built-in Kotlin**: remove the `org.jetbrains.kotlin.android` plugin, keep `kotlin.plugin.compose`. New DSL is the default. Migrate `kotlinOptions` → `kotlin { compilerOptions }`. Gradle 8.14.3 in `/opt/gradle` must be bumped. |
| `kotlin` | 2.0.21 | **2.4.21** (2.4.20 released 7 Sep 2026) | Compose compiler plugin version = Kotlin version |
| `ksp` | 2.0.21-1.0.28 | **2.3.13** | KSP2 has independent versioning since 2.3.0. Room 2.8 needs KSP2. |
| `coreKtx` | 1.15.0 | **1.19.1** | |
| `lifecycle` | 2.8.7 | **2.11.0** | |
| `activityCompose` | 1.9.3 | **1.13.0** | Predictive back APIs |
| `composeBom` | 2024.12.01 | **2026.09.00** (Compose 1.12.x, Material3 1.4.0) | Check whether a 2026.11 BOM exists, since 1.12.1 shipped 4 Nov |
| `navigation` | 2.8.5 | **2.10.2** | Or consider **Navigation 3 `1.2.1`** (Compose-first back stack). Not required. |
| `room` | 2.6.1 | **2.8.5** | `room3` 3.0.3 exists (KMP-first, new package). Stay on 2.8.x for a single-platform app. Export schemas + `autoMigrations`. |
| `datastore` | 1.1.1 | **1.3.0** | |
| `playServicesLocation` | 21.3.0 | **21.4.0** *(via search)* | Implicit min-interval change, see §3.1. FOP already in 21.2+. |
| `osmdroid` | 6.1.20 | **6.1.20 (final, archived)** | Replace with `org.maplibre.gl:android-sdk:13.7.1` (avoid `-globe` builds) |
| `camerax` | 1.4.1 | **1.6.2** (1.7.0-rc02) | `camera-compose` `CameraXViewfinder` can replace `AndroidView(PreviewView)` |
| `work` | 2.10.0 | **2.12.0** | Keep it for non-time-critical work only (§9.2) |
| `coroutines` | 1.9.0 | **1.11.0** | |
| new: `glance` | — | 1.2.0 | Optional (§9.4) |
| new: `core-locationbutton` | — | 1.0.0-alpha01 | Required for policy (§4.2). The only alpha dependency justified here. |
| new: `car-app` | — | 1.7.0 | Optional (§9.6) |
| new: `sceneview/arsceneview` | — | 4.66.0 | Only with Geospatial (§7.2) |

`compileSdk`: 35 → **37** (Android 17 stable since June 2026; AGP 9.5 supports 37). `targetSdk`: 35 → **36** now (Play requirement since 31 Aug 2026). Move to 37 when the precise-location work (§4.2) is done, because API 37 makes the location button mandatory for button-only apps. `minSdk`: keep **26**. AndroidX now defaults to minSdk 24, so 26 is safe.

Proposed `libs.versions.toml` `[versions]` block:

```toml
[versions]
agp = "9.5.0"
kotlin = "2.4.21"
ksp = "2.3.13"
coreKtx = "1.19.1"
lifecycle = "2.11.0"
activityCompose = "1.13.0"
composeBom = "2026.09.00"
navigation = "2.10.2"
room = "2.8.5"
datastore = "1.3.0"
playServicesLocation = "21.4.0"
osmdroid = "6.1.20"            # archived; MapLibre migration planned
maplibre = "13.7.1"            # Phase 4
camerax = "1.6.2"
work = "2.12.0"
coroutines = "1.11.0"
locationButton = "1.0.0-alpha01"
junit = "4.13.2"
```

Also remove `kotlin-android` from `[plugins]` under AGP 9 (built-in Kotlin). Gradle wrapper: 9.6.0+. Do the upgrade in **one PR with CI green**. AGP 8 → 9 is the risky step, so run `./gradlew :app:assembleDebug test lint` before merging.

### 12.2 Structural changes

| # | Change | Why |
|---|---|---|
| A1 | **`HeadingProvider` interface** (`FusedHeadingSource` → `CompassSensor` fallback) chosen in `AppContainer`, like `LocationProviderFactory` | FOP (§5.1). Mirrors the existing location pattern. |
| A2 | Add `accuracyDeg: Float?` to `Heading` and `satsUsed`/`isMock`/`entranceFix` to `LocationFix`/`ParkingSpot` | Uncertainty UI, garage waypoint. Room migration v→v+1 with `@AutoMigration`. |
| A3 | **`FixHistory` singleton** in `AppContainer`, fed by every active location flow | Last-good-fix (§8.4) |
| A4 | Move rotation remapping into a **pure `domain/OrientationMath.kt`** that takes a FloatArray(9) and display rotation | Makes §5.3 unit-testable on the JVM |
| A5 | `ArProjection.project(...)` gains `rollDeg` | §7.1 roll compensation |
| A6 | **`reminders/` switches to AlarmManager** (`MeterAlarmScheduler`, `MeterAlarmReceiver`, boot/time-change receiver). WorkManager is kept only for follow-up. | §9.2 |
| A7 | `ui/map/MapRenderer` interface (`OsmdroidRenderer` now, `MapLibreRenderer` later) + `OfflineRegionRepository` | §6.4. Keeps the migration contained. |
| A8 | `quicksave/` gains `DriveTransitionRegistrar` + receiver, routed through `CarDisconnectHandler` | §4.4 reuse |
| A9 | `permissions/` module: one place for the precise-upgrade prompt, LocationButton, `ACTIVITY_RECOGNITION`, exact alarm, notifications, background (later) | Incremental, policy-friendly asks |
| A10 | Remove the ROADMAP item "osmdroid CacheManager for bounding box". Set an identifying User-Agent. Add an attribution overlay. | §6.2 legal |
| A11 | Keep manual DI. When modules grow (`:feature:geospatial`, `:wear`, `:auto`), split into `:core:domain` (pure JVM), `:core:data`, `:core:sensors`, `:app`. | Faster JVM tests. Dynamic feature for ARCore. |
| A12 | Baseline Profile (`benchmark 1.5.0`) for the cold-start → "I parked here" path | Save must feel instant |

### 12.3 Updated ARCHITECTURE "Tech stack" rows (proposal)

| Concern | Proposed choice |
|---|---|
| Build | AGP 9.5.x (built-in Kotlin), Gradle 9.6+, Kotlin 2.4.21, KSP 2.3.13 |
| SDK | compileSdk 37, targetSdk 36 (→ 37 after §4.2), minSdk 26 |
| UI | Compose BOM 2026.09.00, Material3 1.4, Navigation 2.10 (Nav3 optional) |
| DB | Room 2.8.5 with KSP2, exported schemas |
| Location | play-services-location 21.4.0 Fused + platform fallback, both run during save. `LocationButton` for first-run save. |
| Compass | FusedOrientationProviderClient (GMS) → rotation vector fallback. Gyro-stabilised fuser. Display-rotation-aware remap. |
| Map | osmdroid 6.1.20 (archived, interim) → MapLibre Native 13.x + OpenFreeMap / PMTiles |
| AR | CameraX 1.6.x + Compose overlay (roll-aware, displayed FOV). ARCore Geospatial + SceneView only in an optional dynamic feature. |
| Background | AlarmManager (`setAlarmClock` / `setWindow`) for meter. WorkManager 2.12 for deferrable work. CDM + Activity Recognition triggers. |

---

## Sources

**Competitors**
- BetaNews, Google Maps iPhone auto-parking (Dec 2025): https://betanews.com/2025/12/12/google-maps-now-makes-it-easier-for-iphone-users-to-find-their-cars/
- androidayuda, Google Maps parking guide: https://en.androidayuda.com/google-maps-allows-you-to-register-where-you-parked/
- 9to5Google, Android Auto parking prompt: https://9to5google.com/2023/12/07/android-auto-parking-location-google-maps/
- Android Authority, Android Auto parking: https://androidauthority.com/android-auto-google-maps-parking-location-3392613
- Apple Support 101587: https://support.apple.com/en-qa/101587
- iPhoneLife: https://iphonelife.com/content/how-to-find-your-parked-car-if-you-dont-have-bluetooth-or-carplay
- SlashGear, Apple Maps parked car: https://www.slashgear.com/989226/how-to-find-where-you-parked-your-car-using-apple-maps
- SpotAngels App Store: https://apps.apple.com/us/app/id897809583
- SpotAngels Medium: https://medium.com/spot-angels/why-spotangels-fbce053ed754
- mwm.ai SpotAngels analysis: https://mwm.ai/apps/spotangels-parking-gas/897809583
- Kimola SpotAngels reports: https://kimola.com/reports/spotangels-review-report-see-what-users-really-think-app-store-us-156048 , https://kimola.com/reports/spot-angel-feedback-report-navigate-parking-app-reviews-google-play-en-144451
- Trustpilot ParkMobile: https://www.trustpilot.com/review/parkmobile.io?page=8
- Unstar ParkMobile analyses: https://unstar.app/blog/parkmobile-paid-still-got-ticket-app-not-working-reviews-2026 , https://unstar.app/blog/parkmobile-spothero-paybyphone-parkwhiz-passport-parking-apps-ranked-2026
- Carrow (Play): https://play.google.com/store/apps/details?id=fr.carrow.app
- ParkSave (AppGoblin): https://appgoblin.info/apps/com.sahil.parksave
- Find My Car with AR Tracker: https://apps.apple.com/app/id1065080375

**Location and GNSS**
- PolyU dual-frequency urban positioning: https://www.polyu.edu.hk/aae/ipn-lab/us/publications/Fullpaper/Urban%20Positioning_3D%20Mapping%20Aided%20GNSS%20using%20Dual-Frequency%20Pseudorange%20Measurements%20from%20Smartphones.pdf
- NAVI journal 68/4/727: https://navi.ion.org/content/68/4/727
- ABI Research dual-frequency: https://www.abiresearch.com/market-research/insight/7781281-dual-frequency-gnss-gaining-momentum-in-hi
- Location API diff 31 (isMock): https://developer.android.com/sdk/api_diff/31/changes/android.location.Location
- MockLocationDetector: https://github.com/dinatha08/MockLocationDetector
- AltitudeConverter: https://developer.android.com/reference/android/location/altitude/AltitudeConverter
- Fused GPS-only reports: https://www.b4x.com/android/forum/posts/721459 ; https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/21022297/fused-location-provider-doesn-t-seem-to-use-gps-receiver
- Google Play services release notes: https://developers.google.com/android/guides/releases

**Platform and policy**
- Location permissions: https://developer.android.com/develop/sensors-and-location/location/permissions
- Location button: https://developer.android.com/guide/topics/permissions/private-alternatives/location-button
- Play minimum-scope location (17033915): https://support.google.com/googleplay/android-developer/answer/17033915?hl=en
- Play policy announcement 29 Oct 2026: https://support.google.com/googleplay/android-developer/answer/18100766?hl=en-GB
- epicollect5 #304: https://github.com/epicollect5/epicollect5-development-plan/issues/304
- Moby #111: https://github.com/Xlient/Moby/issues/111
- Background location policy (9799150): https://support.google.com/googleplay/android-developer/answer/9799150?hl=en
- Radar background location guide: https://radar.com/blog/guide-to-play-store-background-location-approval
- Android 16 behavior changes: https://developer.android.com/about/versions/16/behavior-changes-16
- Android 17 behavior changes (all apps): https://developer.android.com/about/versions/17/behavior-changes-all
- FGS types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Android 14 exact alarms: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- Schedule alarms: https://developer.android.com/training/scheduling/alarms
- Activity transitions: https://developer.android.com/develop/sensors-and-location/location/transitions
- Play target SDK: https://developer.android.com/google/play/requirements/target-sdk
- TileService: https://developer.android.com/reference/android/service/quicksettings/TileService

**Sensors**
- Position sensors: https://developer.android.com/develop/sensors-and-location/sensors/sensors_position
- GeomagneticField: https://developer.android.com/reference/android/hardware/GeomagneticField
- WMM2025 (NOAA): https://www.ncei.noaa.gov/news/world-magnetic-model-2025-released
- Fused Orientation Provider blog: https://android-developers.googleblog.com/2024/03/introducing-fused-orientation-provider-api.html?m=1
- ProAndroidDev FOP: https://proandroiddev.com/update-for-your-compass-new-android-orientation-api-dc4e5c25ca35
- Barometer floor (FloorPair): https://www.ncbi.nlm.nih.gov/pmc/articles/PMC6720727/
- Barometer variance discussion: https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/31541658/android-barometer-altitude-reading-is-wrong
- PDR studies: https://sciforum.net/paper/view/11302 ; https://trepo.tuni.fi/bitstream/10024/129025/1/IPIN_Racko_PostPrint.pdf ; https://pmc.ncbi.nlm.nih.gov/articles/PMC6210328
- Wi-Fi RTT (AOSP): https://source.android.google.cn/docs/core/connect/wifi-rtt?authuser=0
- CrowdConnected RTT: https://crowdconnected.com/blog/when-will-cisco-and-aruba-switch-on-wifi-rtt-for-indoor-positioning

**Maps**
- osmdroid README: https://github.com/osmdroid/osmdroid/blob/master/README.md
- OSM wiki osmdroid: https://wiki.openstreetmap.org/wiki/Osmdroid
- OSMF tile policy: https://operations.osmfoundation.org/policies/tiles/
- OSMF vector tile policy: https://operations.osmfoundation.org/policies/vector/
- OSM blog bulk download: https://blog.openstreetmap.org/tag/bulk-download/
- Blocked tiles: https://wiki.openstreetmap.org/wiki/Blocked_tiles
- MapLibre PMTiles (Android): https://maplibre.org/maplibre-native/android/examples/data/PMTiles/
- MapAtlas PMTiles: https://docs.mapatlas.xyz/overview/sdk/android-native/data/PMTiles
- MapLibre offline regions: https://maplibre.org/flutter-maplibre-gl/advanced/offline-regions/
- OpenFreeMap: https://github.com/hyperknot/openfreemap
- Mapsforge VTM: https://github.com/mapsforge/vtm

**AR**
- Geospatial developer guide: https://developers.google.cn/ar/develop/unity-arf/geospatial/developer-guide?hl=en
- Geospatial quota: https://developers.google.cn/ar/develop/java/geospatial/api-usage-quota
- HackMD ARCore notes: https://hackmd.io/@jGfbfuUQRZu32YHgto8HVA/rJegMvZ2D6
- SceneView: https://github.com/SceneView/sceneview-android
- CameraX FOV discussion: https://groups.google.com/a/android.com/g/camerax-developers/c/cwodxv7kLhA
- AOSP FOV code mirror: https://gitlab.e.foundation/e/os/android_frameworks_av/-/commit/86150f33380a93cbc0d1554f5bcd0af2d3ebf246

**Surfaces, privacy and versions**
- Glance generated previews: https://developer.android.com/develop/ui/compose/glance/generated-previews
- Car App Library templates: https://developer.android.com/design/ui/cars/guides/templates/overview
- POI apps: https://developer.android.com/training/cars/apps/poi
- AppsFlyer Data safety: https://support.appsflyer.com/hc/en-us/articles/4417197430289
- AppMetrica Data safety: https://appmetrica.yandex.com/docs/en/data-security/google-data-safety
- AndroidX versions: https://developer.android.com/jetpack/androidx/versions
- AGP release notes: https://developer.android.com/build/releases/gradle-plugin
- Compose BOM mapping: https://developer.android.com/develop/ui/compose/bom/bom-mapping
- Kotlin 2.4.20: https://blog.jetbrains.com/kotlin/2026/09/kotlin-2-4-20-released/
- Maven Central (KSP, osmdroid, coroutines, MapLibre, Mapsforge, SceneView): https://repo1.maven.org/maven2/
