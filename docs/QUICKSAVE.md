# Quick save (Phase 3)

Save the parking spot without opening the app, from four entry points:

| Entry point | Class | How it reaches `QuickSaveService` |
|---|---|---|
| Quick Settings tile "Save parking" | `quicksave/ParkTileService` | API 30+: `startActivityAndCollapse` -> invisible `QuickSaveTrampolineActivity` -> `startForegroundService`. API 26-29: direct `startForegroundService` |
| Home-screen widget (RemoteViews) | `quicksave/ParkWidgetProvider` + `WidgetUpdater` | `PendingIntent.getForegroundService` on the Save button |
| Car Bluetooth disconnect (opt-in) | `quicksave/BluetoothCarDisconnectReceiver` (ACL broadcast, all APIs) and `quicksave/CarCompanionService` (CDM presence, API 33+) | `CarDisconnectHandler` picks FGS, expedited `QuickSaveWorker`, or a "tap to save" notification |
| Any notification created by quick save | `QuickSaveNotifications` | `PendingIntent.getForegroundService` |

All code lives in `app/src/main/java/com/realworldmagic/parkpin/quicksave/`. All resources use the `quicksave_` prefix (`res/layout/quicksave_widget.xml`, `res/xml/quicksave_widget_info.xml`, `res/drawable/quicksave_*.xml`, `res/values/quicksave_strings.xml`).

## Flow

1. `QuickSaveService.onStartCommand` calls `startForeground` right away with type `location` and the progress notification on channel `quick_save`. The service creates the channel itself if it is missing.
2. `QuickSaveCore.captureAndSave` calls `captureBestFix(container.locationProvider)` from `location/SpotCapture.kt`, then `parkingRepository.saveNewSpot(ParkingSpot(...))`.
3. `QuickSaveCore.publish` posts the result notification: "Parking saved · ±8 m", with a **Guide me** action that opens `MainActivity` with extra `spotId` (`MainActivity.EXTRA_SPOT_ID`). It then calls `WidgetUpdater.refreshAllNow`, which refreshes the widgets and calls `TileService.requestListeningState`.
4. The service calls `stopForeground(REMOVE)` and then `stopSelf`.

Failure paths:
- **No location permission.** The service enters the foreground with a fallback type, posts "Location permission needed - open ParkPin" and stops.
- **No fix.** The service posts "Couldn't save parking" with a retry action.
- **Location FGS refused on API 34+.** This throws `SecurityException` when the app is not eligible for while-in-use location. The service enters the foreground as `shortService`, posts "Tap to save" and stops. This keeps the `startForegroundService()` -> `startForeground()` contract, so there is no `ForegroundServiceDidNotStartInTimeException` crash.

### Public API for other layers
- `QuickSaveService.start(context, source)` starts a save. Call it only from a visible activity or a user-interaction PendingIntent.
- `WidgetUpdater.refreshAll(context)` is fire-and-forget. **The UI and core layers should call it after any save, clear ("found my car") or delete**, so the widget and tile stay current.
- `QuickSavePrefs` uses SharedPreferences `"quicksave_prefs"` with key `"car_bt_address"`. Auto-save is on exactly when an address is stored.
- `CarAssociation.bondedDevices(context)`, `chooseCar(context, candidate)`, `clearCar(context)` and `requestCompanionAssociation(context, onPending, onCreated, onError)` (API 33+) are for a settings screen. To use `requestCompanionAssociation`, launch the `IntentSender` with `ActivityResultContracts.StartIntentSenderForResult`. The result arrives in `onCreated`.

## AndroidManifest.xml snippets the lead must merge

The `<manifest>` element already declares `xmlns:tools`.

### Permissions (inside `<manifest>`)
```xml
    <!-- Quick save: foreground service -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <!-- Optional, only for fully automatic Bluetooth auto-save on API 30+ ("Allow all the time").
         Play policy restricted: request only from an explicit opt-in screen, after fine/coarse is granted. -->
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    <!-- Car Bluetooth auto-save -->
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <!-- CompanionDeviceManager presence (API 31+/33+); both are normal (install-time) permissions -->
    <uses-permission android:name="android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE" />
    <uses-permission android:name="android.permission.REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND" />
    <uses-feature android:name="android.software.companion_device_setup" android:required="false" />
    <uses-feature android:name="android.hardware.bluetooth" android:required="false" />
```
`POST_NOTIFICATIONS`, `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` are already declared. Leave out the `ACCESS_BACKGROUND_LOCATION` line if you do not want the fully automatic path. The code then falls back to the "Car disconnected · tap to save" notification.

### Components (inside `<application>`)
```xml
        <!-- Quick save foreground service. shortService is the API 34+ fallback type used only
             to fulfil startForeground() when the location type is refused. -->
        <service
            android:name=".quicksave.QuickSaveService"
            android:exported="false"
            android:foregroundServiceType="location|shortService" />

        <!-- Invisible trampoline so the tile start counts as "visible activity" (while-in-use location). -->
        <activity
            android:name=".quicksave.QuickSaveTrampolineActivity"
            android:exported="false"
            android:excludeFromRecents="true"
            android:noHistory="true"
            android:taskAffinity=""
            android:launchMode="singleInstance"
            android:showWhenLocked="true"
            android:theme="@android:style/Theme.Translucent.NoTitleBar" />

        <!-- Quick Settings tile -->
        <service
            android:name=".quicksave.ParkTileService"
            android:exported="true"
            android:icon="@drawable/quicksave_ic_car"
            android:label="@string/quicksave_tile_label"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter>
                <action android:name="android.service.quicksettings.action.QS_TILE" />
            </intent-filter>
        </service>

        <!-- Home-screen widget -->
        <receiver
            android:name=".quicksave.ParkWidgetProvider"
            android:exported="false"
            android:label="@string/quicksave_tile_label">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/quicksave_widget_info" />
        </receiver>

        <!-- Car Bluetooth disconnect (exempt implicit broadcast; needs BLUETOOTH_CONNECT on 31+) -->
        <receiver
            android:name=".quicksave.BluetoothCarDisconnectReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.bluetooth.device.action.ACL_DISCONNECTED" />
            </intent-filter>
        </receiver>

        <!-- CompanionDeviceManager presence observer (bound by the system on API 31+) -->
        <service
            android:name=".quicksave.CarCompanionService"
            android:exported="true"
            android:permission="android.permission.BIND_COMPANION_DEVICE_SERVICE">
            <intent-filter>
                <action android:name="android.companion.CompanionDeviceService" />
            </intent-filter>
        </service>

        <!-- Only matters if WorkManager ever runs QuickSaveWorker as a foreground service (API < 31). -->
        <service
            android:name="androidx.work.impl.foreground.SystemForegroundService"
            android:foregroundServiceType="location"
            tools:node="merge" />
```
`android:exported="false"` on the widget receiver is valid. The launcher delivers `APPWIDGET_UPDATE` through `AppWidgetManager`, from the system UID, so the receiver does not need to be exported.

## Android-version caveats (researched November 2026)

### Foreground service and location
- **API 31+ background FGS-start restriction.** `startForegroundService()` from the background throws `ForegroundServiceStartNotAllowedException` unless an exemption applies. The exemptions that matter here:
  - The user acts on a **widget** or **notification**.
  - The app can start an activity from the background.
  - The app uses **CompanionDeviceManager** and holds `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`.
  - The user has turned off battery optimisation for the app.

  Source: developer.android.com/develop/background-work/services/fgs/restrictions-bg-start.
- **While-in-use (WIU) location.** On API 34+, creating a `location` FGS while the app lacks location permission *at that moment* throws `SecurityException`. A while-in-use grant only counts while the app has a visible activity. `checkSelfPermission` still returns GRANTED from the background, so it cannot predict this. On API 30-33 the service starts but gets no location.
  - The documented WIU exemptions are: started by a system component, by a **widget** interaction, by a **notification** interaction, or by a PendingIntent from a visible app.
  - Holding `ACCESS_BACKGROUND_LOCATION` removes the problem entirely.
  - CDM association is **not** a WIU exemption.
- **API 34+ also needs `FOREGROUND_SERVICE_LOCATION`** and a runtime location grant for the `location` type. `shortService` needs no extra permission.

### Quick Settings tile
- **API 34+:** `startActivityAndCollapse(Intent)` throws `UnsupportedOperationException` for apps targeting 34+. Use `startActivityAndCollapse(PendingIntent)`, which `ParkTileService.launchActivity` does. AOSP also stopped binding tiles with `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS` for U+.
- **FGS start on click.** AOSP SystemUI adds the tile's app to a ~15 s temporary allowlist on click ("Allow tile services to start foreground service when tile is clicked"). This lifts the *background-start* restriction. It is not documented as a **WIU** exemption, so a direct location FGS from `onClick` may throw `SecurityException` on 34+ or get no location on 30-33.
- **What the tile does.** On API 30+ it opens `QuickSaveTrampolineActivity`, which is translucent, has no UI, sets `showWhenLocked` and finishes at once. A visible activity always grants WIU, and the user never has to unlock. On API 26-29 there is no WIU restriction, so the tile starts the service directly.
- **Permission missing.** The tile opens `MainActivity`, using `unlockAndRun` if the device is locked.
- **Not verified.** I could not reach the AOSP source (android.googlesource.com is blocked in this sandbox). The 15 s allowlist comes from commit search results. The trampoline design does not depend on it.

### Widget
`PendingIntent.getForegroundService` from a widget button is exempt from both restrictions, so the widget starts the service directly. `updatePeriodMillis` is limited to at least 30 min. "Saved 14:05" is the primary text. The distance comes from `LocationProvider.lastKnownFix()` and is shown only if that fix is at most 15 min old. Otherwise the widget shows the time only.

### Bluetooth car disconnect
- **Manifest delivery still works.** `BluetoothDevice.ACTION_ACL_CONNECTED` and `ACTION_ACL_DISCONNECTED` are on the official implicit-broadcast exemption list, so manifest receivers still get them on current Android. On API 31+ they are delivered only to apps that hold **`BLUETOOTH_CONNECT`**, which is a runtime permission. The receiver checks it again before reading the device.
- **Starting the FGS from the receiver is the problem.** A Bluetooth broadcast is *not* an FGS-start exemption, and it is not a WIU exemption. `CarDisconnectHandler` therefore picks one of these paths:
  1. **API < 30, or `ACCESS_BACKGROUND_LOCATION` granted:** try `startForegroundService`. This works on API < 31, and on 31+ when an exemption applies (CDM association, battery-optimisation exemption).
  2. **The start is refused:** enqueue an expedited `QuickSaveWorker`. On 31+ it runs as a JobScheduler expedited job, with no FGS, and reads location using the background permission.
  3. **Only while-in-use location:** post "Car disconnected · tap to save". The notification tap starts the FGS and is WIU-exempt.
  4. **No location permission:** post "Open ParkPin".
- A 90 s debounce in `QuickSavePrefs.tryClaimAutoTrigger` dedupes the ACL receiver and CDM, since both can fire for one disconnect.
- **CompanionDeviceManager (recommended on API 33+):**
  - `CarAssociation.requestCompanionAssociation` shows the system chooser. It then stores the address and association id and calls `startObservingDevicePresence(address)`. That call is deprecated in API 36 in favour of `ObservingDevicePresenceRequest`, but it still works with compileSdk 35.
  - From API 33, Bluetooth Classic connect/disconnect drives presence for associated devices. The system then binds `CarCompanionService` (`onDeviceDisappeared(AssociationInfo)`), which raises the process priority.
  - `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` lifts the background-start restriction. Location still needs `ACCESS_BACKGROUND_LOCATION` on 34+.
  - On API 31-32, presence is BLE-scan based and unreliable for car head units, so we rely on the ACL receiver there.
  - API 36 deprecates the `onDeviceAppeared/Disappeared` callbacks in favour of `onDevicePresenceEvent(DevicePresenceEvent)`. The platform still dispatches the legacy callbacks when the new one is not overridden. Revisit this when compileSdk reaches 36.
- **Background location policy.** Without `ACCESS_BACKGROUND_LOCATION`, fully automatic saving is impossible on API 30+. The fallback is a one-tap notification, which is still useful: the user gets it as they walk away from the car. Play requires a prominent disclosure and a policy declaration before you request "Allow all the time".

### Notifications
On API 33+ the app shows result notifications only if the user has granted `POST_NOTIFICATIONS`. If it is denied, they are skipped silently. The save itself still happens, and the widget and tile still update. A foreground service runs whether or not the permission is granted. The FGS notification uses `FOREGROUND_SERVICE_IMMEDIATE`, so Android 12+ shows it without the 10 s deferral.

## Not verified
The Android SDK dependencies could not be downloaded in this sandbox, so the files have **not** been compiled. They were reviewed against the `android-35` `android.jar` signatures and the contracts listed above.
