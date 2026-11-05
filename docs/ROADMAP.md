# ParkPin Roadmap

## Phase 0 — Foundation (this session)
- [ ] Gradle project, version catalog, offline-capable build toolchain
- [ ] Pure-Kotlin domain (GeoMath, ArrivalDetector, AccuracyFilter) + JVM unit tests
- [ ] Room DB, repositories, DataStore settings
- [ ] Location providers (Fused + platform fallback), compass sensor

## Phase 1 — MVP "Drop pin, walk back"
- [ ] Home: one-tap "I parked here" (best-of-N GPS fix, accuracy shown)
- [ ] Guide screen: compass arrow, distance, walking ETA, cardinal direction, arrival haptic
- [ ] Map screen (MapLibre + OpenFreeMap): car + user markers, straight-line path, cached tiles offline
- [ ] History: list, re-guide, delete
- [ ] Runtime permission flow (fine/coarse, camera, notifications on 33+)

## Phase 2 — "Real-World Magic" AR
- [ ] AR guide: CameraX preview + floating pin anchored by bearing/pitch, distance label
- [ ] Off-screen indicator (edge arrow when car is behind you)
- [ ] Compass calibration prompt (figure-8) when sensor accuracy low

## Phase 3 — Context & convenience (suggestions)
- [ ] Spot details: note ("P2, row C"), floor label, photo of the pillar sign
- [ ] Parking meter timer + reminder notification (WorkManager), "time left" chip
- [ ] Quick Settings tile + home-screen widget: save spot without opening app
- [ ] Share spot as `geo:` URI / text for family
- [ ] Auto-save on car Bluetooth disconnect (user picks car BT device)
- [ ] Activity Recognition: detect IN_VEHICLE → WALKING transition, suggest save

## Phase 4 — Hard environments
- [ ] Underground garages: save last good outdoor fix + barometer altitude delta for floor estimate
- [ ] Dead-reckoning (step counter + heading) when GPS lost indoors
- [ ] ARCore Geospatial API (VPS) for centimeter-ish anchoring where Street View coverage exists (needs network — opt-in)
- [x] Offline map region download (MapLibre `OfflineManager`, ~1 km box around the spot, z14-17, OpenFreeMap; never tile.openstreetmap.org)

## Phase 5 — Toolbox expansion ("Real-World Magic")
- [ ] Generic "pin anything" (bike, tent, meeting point)
- [ ] Wear OS companion: wrist arrow
- [ ] Android Auto: "Save parking" on disconnect
- [ ] Other AR tools: AR ruler, level, star-finder sharing the sensor/AR core

## Quality bar
- Works in airplane mode end to end (except tile download)
- Battery: guidance updates only while screen visible; no background location by default
- Privacy: data never leaves device; no analytics; export/delete all
- Accessibility: TalkBack announces distance + clock direction ("car at 2 o'clock, 40 m")
