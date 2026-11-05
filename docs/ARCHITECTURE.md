# ParkPin — "Where Did I Park?" Offline Locator

Part of the **Real-World Magic AR Toolbox**. First tool: save where you parked, get guided back — fully offline.

## Tech stack (fixed — all agents follow this)

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin 2.0.21 | standard |
| Build | AGP 8.7.3, Gradle 8.14.3 (local `/opt/gradle`), version catalog `gradle/libs.versions.toml` | |
| SDK | compileSdk 35, targetSdk 35, minSdk 26 | covers ~97% devices |
| UI | Jetpack Compose (BOM 2024.12.01), Material 3, Navigation-Compose | |
| DI | Manual: `ParkPinApp.container: AppContainer` | no annotation processing beyond Room |
| DB | Room 2.6.1 with KSP 2.0.21-1.0.28 | offline persistence |
| Prefs | DataStore Preferences 1.1.1 | settings |
| Location | `play-services-location` 21.3.0 FusedLocationProviderClient, fallback `LocationManager` GPS provider when Play Services missing | GPS works without network |
| Map | MapLibre Native Android 13.0.2 + OpenFreeMap "liberty" vector style (no API key); `OfflineManager` region (~1 km, z14-17) around the car | maintained, offline download allowed by provider, free |
| Compass | `TYPE_ROTATION_VECTOR` sensor → azimuth; magnetic declination via `GeomagneticField` | true-north bearing |
| AR view | CameraX 1.4.1 preview + Compose overlay arrow (bearing − azimuth, pitch-aware) | no ARCore requirement; ARCore Geospatial is a later phase |
| Background | WorkManager 2.10.0 (meter reminder), `AlarmManager` exact alarm optional | |
| Tests | JUnit4 on JVM for pure-Kotlin domain code | runs without device |

Package root: `com.realworldmagic.parkpin`

## Module layout (single `:app` module)

```
app/src/main/java/com/realworldmagic/parkpin/
  ParkPinApp.kt               Application; builds AppContainer, notification channels
  MainActivity.kt             Compose host, NavHost
  di/AppContainer.kt          manual DI
  domain/                     PURE KOTLIN, no android.* imports (unit tested)
    GeoPoint.kt               data class GeoPoint(lat: Double, lng: Double)
    GeoMath.kt                object GeoMath: distanceMeters, initialBearingDeg, normalizeDeg, relativeBearingDeg, formatDistance
    ParkingSpot.kt            domain model
    GuidanceState.kt          sealed/ data class for guidance UI
    ArrivalDetector.kt        hysteresis "you've arrived" logic
    AccuracyFilter.kt         rejects poor fixes, smoothing
  data/
    db/ParkingSpotEntity.kt, ParkingDao.kt, ParkPinDatabase.kt
    ParkingRepository.kt      interface + ParkingRepositoryImpl (Room)
    SettingsRepository.kt     DataStore (units, meter reminder lead, map tiles cache)
  location/
    LocationProvider.kt       interface: fun locationUpdates(intervalMs: Long): Flow<LocationFix>; suspend fun currentFix(timeoutMs: Long): LocationFix?
    LocationFix.kt            data class LocationFix(point: GeoPoint, accuracyM: Float, altitudeM: Double?, timeMs: Long, provider: String)  (domain-ish, lives in domain/)
    FusedLocationProvider.kt  Play Services impl
    PlatformLocationProvider.kt LocationManager impl
    LocationProviderFactory.kt picks impl at runtime
  sensors/
    CompassSensor.kt          fun headingUpdates(): Flow<Heading>; Heading(azimuthDeg true-north, pitchDeg, rollDeg, accuracy)
  reminders/
    MeterReminderScheduler.kt, MeterReminderWorker.kt
  ui/
    theme/                    Material3 theme, dark mode
    navigation/ParkPinNavHost.kt  routes: home, guide/{spotId}, ar/{spotId}, map/{spotId}, history, settings, spotEdit/{spotId}
    home/HomeScreen.kt + HomeViewModel.kt   big "I parked here" button, current active spot card
    guide/GuideScreen.kt + GuideViewModel.kt compass arrow + distance + ETA walk
    ar/ArGuideScreen.kt       CameraX preview + overlay arrow/marker
    map/MapScreen.kt          MapLibre MapView in AndroidView, user dot + car pin + line layers, offline banner
    map/OfflineRegions.kt     MapStyle constant, OfflineManager download/list/delete wrapper
    history/HistoryScreen.kt  past spots, delete, re-navigate
    spot/SpotEditScreen.kt    note (level/row), photo, meter timer
    settings/SettingsScreen.kt
    components/               shared composables (DirectionArrow, PermissionGate, DistanceText)
```

## Core contracts (signatures agents must match)

```kotlin
// domain/GeoPoint.kt
data class GeoPoint(val lat: Double, val lng: Double)

// domain/LocationFix.kt
data class LocationFix(val point: GeoPoint, val accuracyM: Float, val altitudeM: Double? = null,
                       val timeMs: Long, val provider: String = "unknown")

// domain/ParkingSpot.kt
data class ParkingSpot(
    val id: Long = 0,
    val point: GeoPoint,
    val accuracyM: Float,
    val altitudeM: Double? = null,
    val savedAtMs: Long,
    val note: String = "",          // e.g. "Level P2, row C"
    val floorLabel: String? = null,
    val photoUri: String? = null,   // content:// or file path in app storage
    val meterExpiresAtMs: Long? = null,
    val isActive: Boolean = true,   // only one active at a time
    val address: String? = null     // optional reverse-geocode when online
)

// domain/Heading.kt
data class Heading(val azimuthDeg: Float, val pitchDeg: Float, val rollDeg: Float, val accuracy: Int)

// domain/GeoMath.kt
object GeoMath {
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double          // haversine, R=6371008.8
    fun initialBearingDeg(from: GeoPoint, to: GeoPoint): Double   // 0..360, true north
    fun normalizeDeg(d: Double): Double                           // 0..360
    fun relativeBearingDeg(targetBearing: Double, heading: Double): Double // -180..180
    fun formatDistance(meters: Double, metric: Boolean): String   // "45 m", "1.2 km", "150 ft", "0.8 mi"
    fun walkingEtaMinutes(meters: Double, speedMps: Double = 1.35): Int
    fun cardinal(bearingDeg: Double): String                      // N, NE, E...
}

// domain/GuidanceState.kt
data class GuidanceState(
    val spot: ParkingSpot? = null, val fix: LocationFix? = null, val heading: Heading? = null,
    val distanceM: Double? = null, val bearingDeg: Double? = null, val relativeDeg: Double? = null,
    val arrived: Boolean = false, val gpsWeak: Boolean = false, val compassNeedsCalibration: Boolean = false
)

// data/ParkingRepository.kt
interface ParkingRepository {
    fun observeActiveSpot(): Flow<ParkingSpot?>
    fun observeHistory(): Flow<List<ParkingSpot>>
    fun observeSpot(id: Long): Flow<ParkingSpot?>
    suspend fun saveNewSpot(spot: ParkingSpot): Long   // deactivates previous active
    suspend fun update(spot: ParkingSpot)
    suspend fun clearActive()                          // "I found my car"
    suspend fun delete(id: Long)
}

// location/LocationProvider.kt
interface LocationProvider {
    fun locationUpdates(intervalMs: Long = 1000L): Flow<LocationFix>
    suspend fun currentFix(timeoutMs: Long = 15_000L): LocationFix?
}

// sensors/CompassSensor.kt
class CompassSensor(context: Context) { fun headingUpdates(): Flow<Heading>; val isAvailable: Boolean }

// di/AppContainer.kt
class AppContainer(context: Context) {
    val parkingRepository: ParkingRepository
    val settingsRepository: SettingsRepository
    val locationProvider: LocationProvider
    val compassSensor: CompassSensor
    val meterReminderScheduler: MeterReminderScheduler
}
// Access from composables: (LocalContext.current.applicationContext as ParkPinApp).container
// ViewModels built with viewModel(factory = viewModelFactory { initializer { HomeViewModel(container...) } })
```

## Save-spot algorithm
1. Request fresh fix (high accuracy, up to 15 s). Collect fixes; keep best accuracy; stop early when accuracy ≤ 10 m.
2. If no fix → fall back to last known fix (flag as "approximate").
3. Persist with accuracy + altitude (altitude helps multi-level garages; plus manual floor label).

## Guidance algorithm
- distance = haversine(fix, spot); bearing = initialBearing(fix, spot)
- heading = rotation-vector azimuth + GeomagneticField declination (true north)
- relative = normalize(bearing − heading) → rotate arrow
- low-pass filter heading (circular mean / exp smoothing α≈0.15)
- arrival: distance < max(8 m, accuracy) for 3 consecutive fixes → arrived; leave when > 20 m (hysteresis)
- gpsWeak when accuracy > 30 m

Fully offline: GPS + sensors + Room need no network. Map tiles: MapLibre ambient cache plus user-saved offline regions; with no data the map still shows markers + line.
