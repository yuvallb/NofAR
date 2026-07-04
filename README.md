# NofAR

**Point, explore, discover.**

NofAR is an offline-first Android app for hikers and travelers. Download map data for a circular area, then point your phone at the horizon to see terrain-aware labels for visible peaks, cities, towns, and villages — filtered by real line-of-sight over the landscape.

No accounts. No backend. No tracking.

| | |
|---|---|
| **Platform** | Android 8.0+ (API 26) |
| **License** | [Apache 2.0](LICENSE) |
| **Status** | Phase 0 — navigable app shell (Home / Prepare / Explore / Settings) |

---

## Features

- **Offline Explore** — Once a region is downloaded, Explore and Home modes work without network access.
- **Terrain-aware visibility** — Labels are hidden when mountains or ridges block the view, using live DEM raycasting (not precomputed horizon grids).
- **Circular regions (5–20 km)** — Define an area on a map, download OpenStreetMap places/peaks and Copernicus elevation tiles, and go.
- **Three modes**
  - **Home** — Manage saved regions, see download status, enter Explore when you are inside a ready region.
  - **Prepare** — Draw a region on a map and download OSM + DEM data (internet required; resumable downloads).
  - **Explore** — Full-screen camera view with compass-corrected AR labels for what you can actually see.
- **Privacy by design** — GPS-only location (no network location), no analytics, no crash reporting that phones home.

---

## How it works

```
Prepare   Draw circle → Overpass (places + peaks) + Copernicus DEM tiles
          → convert elevation to flat binary rasters → store locally → READY

Explore   GPS + smoothed compass → spatial query → live terrain raycast
          → project visible labels onto camera overlay (≥ 30 FPS)

Home      Region list + “you are here” detection → Prepare or Explore
```

During Explore, visibility and rendering run on two cadences: a low-frequency pass (raycasting, ≤ every 2 s or 20 m movement) and a high-frequency AR overlay (sensor-smoothed reprojection only — no DEM work on the render thread).

---

## Data sources

| Source | Use | License / attribution |
|--------|-----|------------------------|
| [OpenStreetMap](https://www.openstreetmap.org/) via [Overpass API](https://wiki.openstreetmap.org/wiki/Overpass_API) | Places (`place=*`) and peaks (`natural=peak`) | [ODbL](https://www.openstreetmap.org/copyright) — *© OpenStreetMap contributors* |
| [Copernicus DEM GLO-30](https://spacedata.copernicus.eu/) | 30 m elevation for terrain occlusion | *Copernicus DEM, ESA / Airbus* |

Overpass requests use a descriptive `User-Agent` and tiered public mirror failover. DEM tiles are cached globally and shared across overlapping regions.

---

## Project status

The Android app uses a modular [Now in Android](https://github.com/android/nowinandroid)-style Gradle layout. Phase 0 provides the project scaffold, design system baseline, navigation shell, and CI — feature logic arrives in later phases.

What exists today:

- **Multi-module Android app** (`:app`, `:core:*`, `:feature:*`, `:build-logic:convention`)
- **[Apache 2.0](LICENSE)** license
- **Python algorithm prototypes** in [`scripts/`](scripts/) — reference implementations for DEM download, terrain profiles, and horizon visibility (ported to Kotlin for the app; not shipped in the APK)

---

## Getting started

### Android app

Requirements: **JDK 26** (Gradle runtime; Android modules compile to JVM 17 bytecode), Android SDK, a device or emulator on API 26+.

**Android SDK (one-time):** Install [Android Studio](https://developer.android.com/studio) (or the command-line SDK tools). The default SDK path on macOS is `~/Library/Android/sdk`. Point Gradle at it:

```bash
cp local.properties.example local.properties
# Edit sdk.dir= if your SDK lives elsewhere (Android Studio → Settings → Android SDK → Android SDK Location)
```

```bash
./gradlew :app:assembleDebug
./gradlew spotlessCheck detekt lint test
```

Release builds:

```bash
./gradlew :app:assembleRelease
```

CI will run `spotlessCheck`, `detekt`, `lint`, `test`, `assembleDebug`, and `assembleRelease` on pull requests.

### Python prototypes (available now)

Use these to experiment with visibility logic and DEM handling before or alongside Android work:

```bash
cd scripts
uv sync   # or: pip install -e .
python horizon_dem.py --help
python line_dem_profile.py --help
python download_dem.py --help
```

Downloaded DEM GeoTIFFs are cached under `scripts/dem_cache/` (gitignored).

---

## Architecture (target)

The app follows the modular layout of [Now in Android](https://github.com/android/nowinandroid):

```
:app
:core:model, :core:common, :core:database, :core:data, :core:network
:core:ui, :core:designsystem, :core:testing
:feature:home, :feature:prepare, :feature:explore, :feature:settings
:build-logic:convention
```

- **UI:** Jetpack Compose (Material 3), CameraX for the Explore camera feed
- **Persistence:** SQLite with R-Tree spatial index; DEM stored as flat `.bin` rasters (GeoTIFF decode is Prepare-only)
- **Sensors:** Rotation vector + `GeomagneticField` declination + One Euro Filter smoothing
- **DI / async:** Hilt, Kotlin Coroutines, `Flow`
- **No ARCore dependency** — keeps builds reproducible and F-Droid-friendly

Feature modules depend only on `core:*`, not on each other. Unidirectional flow: **Compose → ViewModel → Repository → DataSource**.

---

## What NofAR is not (MVP)

- No user accounts or cloud sync
- No offline routing or turn-by-turn navigation
- No 3D terrain mesh — elevation is used for occlusion only
- No polygon regions (circles only)
- No road data
- No AI / LLM features
- No analytics or telemetry

---

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for branch naming, PR checklist, and code style. Keep changes aligned with the existing architecture and privacy constraints (no proprietary analytics, no network in Explore/Home).

For agent-assisted development in this repo, see [AGENTS.md](AGENTS.md).

---

## License

Copyright 2026 NofAR contributors

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).

OpenStreetMap data used by the app is © OpenStreetMap contributors, available under the Open Database License. Copernicus DEM requires the attribution above when displaying derived products.
