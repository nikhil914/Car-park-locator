# ParkPin Privacy Policy

**Effective date:** 5 November 2026
**App:** ParkPin — Where Did I Park? (package `com.realworldmagic.parkpin`), part of the Real-World Magic AR Toolbox
**Developer:** nikhil914 (<https://github.com/nikhil914/car-park-locator>)

## Summary

- ParkPin does **not** collect, transmit, sell or share your personal data.
- Your parking spots, notes and photos are stored **only on your device**.
- There are **no accounts, no ads, no analytics and no crash-reporting SDKs**.
- The only network traffic is optional map tile downloads from OpenStreetMap tile servers.

## Data the app handles

All of the data below stays on your device in the app's private storage. The developer has no server and cannot access it.

| Data | Purpose | Where it is stored | Shared? |
|---|---|---|---|
| Precise or approximate location | Save the parking spot; compute distance and direction back to it | On-device database (Room) | No |
| Parking spot details: time, accuracy, altitude, note, floor label, meter expiry | Show and guide you to your spot; history; meter reminder | On-device database | No |
| Photos you take of your spot | Help you recognise the spot (e.g. a pillar sign) | App-private storage on your device | No |
| Settings (units, reminder lead time) | Remember your preferences | On-device DataStore | No |
| Cached map tiles | Show the map offline | App cache on your device | No |

## Location

- Location is used **only while the app is open and in use** (foreground).
- ParkPin does **not** request background location access and does not track you when the app is closed.
- GPS works without an internet connection. Location is never sent to the developer or any third party by ParkPin.
- On devices with Google Play Services, ParkPin uses the Fused Location Provider. Google may process location data according to your device's Google Location Accuracy settings and [Google's privacy policy](https://policies.google.com/privacy); ParkPin does not control this. On other devices, ParkPin uses the system GPS provider.

## Camera and photos

- The camera is used **only** to show a live preview behind the AR guide arrow, and to take a photo of your parking spot when you choose to.
- AR preview frames are processed on the device in real time and are **never recorded, stored or uploaded**.
- Photos you take are stored only in the app's private storage on your device. They are not added to your gallery unless you export them yourself.

## Notifications

If you set a parking-meter timer, ParkPin schedules a local reminder notification on your device. No push service or server is involved.

## Network access and third parties

ParkPin requests map tiles from **OpenStreetMap tile servers** when the map screen is open and the device is online. Like any web request, this reveals your **IP address**, the requested tile coordinates (which show the approximate area being viewed) and a user-agent string to the tile server operator. This is handled under the [OSMF Privacy Policy](https://osmfoundation.org/wiki/Privacy_Policy). ParkPin sends no other identifiers with these requests.

You can avoid this entirely by not opening the map, or by using the app in airplane mode. All other features work offline.

ParkPin contains no advertising, analytics, tracking or crash-reporting libraries.

## Data retention and deletion

- Data stays on your device until you delete it.
- You can delete individual spots in **History**, or clear all data in **Settings**.
- Uninstalling the app, or **Android Settings > Apps > ParkPin > Storage > Clear storage**, removes all app data, including photos and cached tiles.
- Android may include app data in your personal device backup (e.g. Google Drive backup) if you have enabled backups. This is controlled by your device settings, not by ParkPin.

## Children

ParkPin is not directed at children under 13 and collects no data from anyone.

## Security

Data is kept in Android's app-private storage, which other apps cannot read. Protect your device with a screen lock to protect your saved spots.

## Your rights

Because ParkPin does not collect personal data, the developer holds no data about you to access, correct or delete. You keep full control of the data on your device as described above.

## Changes to this policy

Changes will be published in this file in the project repository, with a new effective date. Significant changes will also be noted in the release notes.

## Contact

Questions: open an issue at <https://github.com/nikhil914/car-park-locator/issues>.
