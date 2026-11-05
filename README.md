# ParkPin — Where Did I Park?

[![Android CI](https://github.com/nikhil914/car-park-locator/actions/workflows/android.yml/badge.svg)](https://github.com/nikhil914/car-park-locator/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![minSdk 26](https://img.shields.io/badge/minSdk-26-green)
![targetSdk 36](https://img.shields.io/badge/targetSdk-36-green)

**ParkPin** saves where you parked with one tap and guides you back on foot, **fully offline**.
It is the first tool in the **Real-World Magic AR Toolbox**: small, private utilities that
use your phone's sensors to make the physical world easier to deal with.

No account. No ads. No analytics. Your parking spots never leave your phone.

---

## Features

| | Feature | Details |
|---|---|---|
| 📍 | **Offline pin drop** | One tap on *I parked here*. ParkPin takes the best of several GPS fixes (stops early at ≤ 10 m accuracy) and shows you how accurate the pin is. Works in airplane mode. |
| 🧭 | **Compass guide** | A large arrow points to your car using GPS bearing and the rotation-vector compass (corrected to true north). Shows distance, walking ETA and cardinal direction, and vibrates when you arrive. |
| 🪄 | **AR guide** | Hold up the phone: a live camera preview with a floating pin over your car and an edge arrow when it is behind you. No ARCore required. |
| 🗺️ | **Offline map** | OpenStreetMap view (osmdroid) with your position, your car and a straight-line path. Tiles you have viewed are cached for offline use. |
| 🕘 | **History** | Past spots with time and notes. Re-guide to any of them or delete them. |
| 📝 | **Notes, photo, meter reminder** | Add "Level P2, row C", a floor label, a photo of the pillar sign, and a parking-meter timer with a reminder notification before it expires. |
| 🔒 | **Privacy first** | Everything is stored on-device. Location is used only while the app is open. See [PRIVACY.md](docs/PRIVACY.md). |

## Screenshots

> Screenshots are coming with the first public build.

| Home | Compass guide | AR guide | Map | History |
|---|---|---|---|---|
| _placeholder_ | _placeholder_ | _placeholder_ | _placeholder_ | _placeholder_ |

<!-- Put images in docs/screenshots/ and reference them, e.g. ![Home](docs/screenshots/home.png) -->

## Architecture

Single `:app` module, Kotlin + Jetpack Compose, manual dependency injection through
`ParkPinApp.container`. The `domain/` package is pure Kotlin (no `android.*` imports),
so the geo math and guidance logic run as fast JVM unit tests.

```
┌──────────────────────────────────────────────────────────────────┐
│ UI  (Jetpack Compose, Material 3, Navigation-Compose)            │
│  Home · Guide · AR Guide · Map · History · Spot edit · Settings  │
│  ViewModels expose StateFlow<…UiState>                           │
└───────────────────────────────┬──────────────────────────────────┘
                                │ observes Flows / calls suspend fns
┌───────────────────────────────▼──────────────────────────────────┐
│ Domain  (pure Kotlin, unit tested)                               │
│  GeoMath · ArrivalDetector · AccuracyFilter · GuidanceState      │
│  ParkingSpot · LocationFix · Heading · GeoPoint                  │
└──────┬─────────────────┬──────────────────┬───────────────┬──────┘
       │                 │                  │               │
┌──────▼──────┐  ┌───────▼────────┐  ┌──────▼──────┐  ┌─────▼──────┐
│ Data        │  │ Location       │  │ Sensors     │  │ Reminders  │
│ Room DB     │  │ Fused (Play    │  │ Rotation-   │  │ WorkManager│
│ DataStore   │  │ Services) or   │  │ vector      │  │ meter      │
│ Repository  │  │ LocationManager│  │ compass +   │  │ reminder   │
│             │  │ GPS fallback   │  │ declination │  │            │
└─────────────┘  └────────────────┘  └─────────────┘  └────────────┘
              wired together by di/AppContainer (manual DI)
```

Full details, contracts and algorithms: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.4.21 (KSP 2.3.13) |
| Build | Android Gradle Plugin 9.5.0 (built-in Kotlin), Gradle 9.6.1, version catalog (`gradle/libs.versions.toml`) |
| SDK levels | minSdk 26 (Android 8.0), compileSdk 37, targetSdk 36 |
| UI | Jetpack Compose (BOM 2026.09.00), Material 3, Navigation-Compose 2.10.2, Activity 1.13.0, Lifecycle 2.11.0 |
| Persistence | Room 2.8.5 (KSP2), DataStore Preferences 1.3.0 |
| Location | `play-services-location` 21.4.0 with `LocationManager` GPS fallback |
| Compass | `TYPE_ROTATION_VECTOR` + `GeomagneticField` declination |
| Map | osmdroid 6.1.20 (OpenStreetMap, no API key) |
| AR view | CameraX 1.6.2 preview + Compose overlay |
| Background work | WorkManager 2.12.0, coroutines 1.11.0 |
| DI | Manual (`AppContainer`) |
| Tests | JUnit 4 on the JVM for domain code, Robolectric 4.17 (SDK 36) |
| CI | GitHub Actions ([android.yml](.github/workflows/android.yml), [release.yml](.github/workflows/release.yml)) |

## Building

Requirements: **JDK 21** (JDK 17 builds the app, but Robolectric needs 21 to run SDK 36 tests) and the **Android SDK** (platform 37, build-tools 36).

### Android Studio

1. Clone the repo: `git clone https://github.com/nikhil914/car-park-locator.git`
2. **File > Open** and pick the project folder.
3. Let Gradle sync, choose the `app` run configuration and press **Run**.

### Command line

```bash
./gradlew assembleDebug             # APK in app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest         # JVM unit tests
./gradlew lintDebug                 # Android lint
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If you have no Android SDK yet, or want an offline/headless setup, see
[docs/BUILD.md](docs/BUILD.md).

### Releases

Pushing a tag such as `v1.0.0` triggers [release.yml](.github/workflows/release.yml),
which builds a release APK and attaches it to a GitHub Release. The APK is signed when
the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` repository
secrets are set; otherwise an unsigned APK is published.

## Permissions

ParkPin asks only for what a feature needs, at the moment you use that feature.
Every feature that needs a missing permission shows an explanation and a way to continue.

| Permission | Why | When asked |
|---|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Save the spot and compute distance and direction back to it. Used only while the app is on screen. No background location. | First time you tap *I parked here* |
| `CAMERA` | Live preview behind the AR guide arrow, and optional photo of the parking sign. Frames are never recorded or uploaded. | First time you open AR guide or add a photo |
| `POST_NOTIFICATIONS` (Android 13+) | Parking-meter reminder. | When you set a meter timer |
| `SCHEDULE_EXACT_ALARM` (optional) | Fire the meter reminder at the exact minute. Falls back to WorkManager timing if not granted. | When you set a meter timer |
| `VIBRATE` | Haptic pulse when you arrive at your car. | Install time (normal permission) |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Download OpenStreetMap tiles for the map view, only when online. Everything else works without network. | Install time (normal permission) |

## Roadmap

See [docs/ROADMAP.md](docs/ROADMAP.md). Highlights: Quick Settings tile and widget,
auto-save on car Bluetooth disconnect, underground-garage floor estimation, offline map
region download, Wear OS arrow, and more Real-World Magic tools sharing the same sensor
and AR core.

Testing approach and the field-test checklist: [docs/TESTING.md](docs/TESTING.md).

## Contributing

Contributions are welcome.

1. Open an issue first for larger changes, using the bug or feature templates.
2. Fork, create a branch from `main`, and keep changes focused.
3. Keep `domain/` free of Android imports and add unit tests for new logic.
4. Run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` before pushing.
5. Open a pull request and fill in the template. CI must be green.

Project principles: works offline, data stays on device, no background location by
default, accessible with TalkBack.

## License

ParkPin is released under the [MIT License](LICENSE). Copyright (c) 2026 nikhil914.

## Map data attribution

Map tiles and data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors,
available under the [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/).
The in-app map shows this attribution. Tile usage follows the
[OSM Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/); heavy users
should configure their own tile server.
