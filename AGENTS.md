# NofAR — Agent Instructions

**Tagline:** *point, explore, discover*

NofAR is an offline-first Android AR app for hikers and travelers. Users download OSM place/peak data and Copernicus DEM tiles for circular regions, then point their phone at the horizon in **Explore** mode to see terrain-aware labels for visible peaks and settlements.

- **Platform:** Android only (min SDK 26)
- **License:** Apache 2.0
- **Architecture baseline:** [Now in Android](https://github.com/android/nowinandroid) (modular Gradle, Compose, Hilt, convention plugins)
- **No backend, no accounts, no telemetry**

---

## Repository state

The Android app scaffold does **not** exist yet. Implementation follows a phased plan in `internal/phases/`. Start with **Phase 0** unless the user directs otherwise.

| Doc | Purpose |
|-----|---------|
| `internal/Requirements.md` | Product & technical source of truth (v0.3) |
| `internal/phases/README.md` | Phase index, dependencies, cross-cutting practices |
| `internal/phases/phase-NN-*.md` | Per-phase tasks and acceptance criteria |

> `internal/` may be gitignored locally; treat it as authoritative when present.

**Python prototypes** in `scripts/` (`horizon_dem.py`, `line_dem_profile.py`, `download_dem.py`) are algorithm references — port logic to Kotlin; do not ship Python in the app.

---

## Module layout (target)

```
:app
:core:model, :core:common, :core:database, :core:data, :core:network
:core:ui, :core:designsystem, :core:testing
:feature:home, :feature:prepare, :feature:explore, :feature:settings
:build-logic:convention
```

- Feature modules depend only on `core:*`, **not** on each other.
- Unidirectional flow: **Compose UI → ViewModel → Repository → DataSource**.
- Use cases only when logic spans repositories.
- DI: Hilt modules per layer; test doubles via `@TestInstallIn`.
- Async: Kotlin Coroutines + `Flow` for streams (GPS, sensors, download progress).

---

## Build, test, and lint

Once Phase 0 is complete, use these commands:

```bash
# Build
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease

# Quality gates (must pass before merging)
./gradlew detekt lint test
./gradlew connectedCheck   # instrumented tests, when applicable
```

CI (GitHub Actions) runs `./gradlew detekt lint test assembleRelease` on PRs and pushes to `main`.

**Python scripts** (algorithm prototyping only):

```bash
cd scripts && uv sync    # or pip install from pyproject.toml
python horizon_dem.py    # see script docstrings for args
```

Do not add proprietary or analytics dependencies to the Gradle dependency tree.

---

## Hard constraints

These are non-negotiable for MVP — do not introduce alternatives without explicit user approval:

| Rule | Detail |
|------|--------|
| Offline Explore/Home | No network calls outside **Prepare** mode |
| No ARCore | Must not be a hard dependency (F-Droid / reproducible builds) |
| Privacy | No analytics, crash reporting that phones home, or tracking SDKs |
| Regions | Circular only (5–20 km radius); no polygon regions |
| DEM at runtime | Explore reads flat `.bin` rasters only — **no GeoTIFF/COG parsing** in Explore |
| Visibility | Live DEM raycast (~50–100 candidates), not precomputed horizon grids |
| Overpass | Streaming JSON parse (never load full response into memory); three-mirror failover |
| DEM cache | Global tiles, reference-counted; LRU eviction per Requirements §5.3 |
| No AI/LLM | Deterministic local computation only |

---

## Implementation guidance

### Kotlin / Android

- Kotlin 2.x, Jetpack Compose (Material 3), CameraX, Room + raw SQLite/R-Tree where needed.
- `SensorManager` rotation vector + `GeomagneticField` declination + One Euro Filter for compass smoothing.
- OSMDroid (or similar) for Prepare-mode map widget only — not for OSM data parsing.
- OkHttp for Overpass; Moshi `JsonReader` or Kotlinx.serialization streaming for ingestion.
- GeoTIFF decode **Prepare-only**; convert to flat binary elevation matrices on download.

### Performance budgets (Requirements §8)

- AR render pass ≥ 30 FPS — **must not** touch DEM/DB/raycasting on the render thread.
- Two-cadence design: high-frequency overlay render vs. low-frequency visibility pass (≤ 200 ms, at most every 2 s or 20 m movement).
- Spatial query < 50 ms for ≤ 10,000 entities per region.

### Phase workflow

1. Read the relevant `internal/phases/phase-NN-*.md` before starting work.
2. Implement only that phase's scope; defer items listed under **Out of scope**.
3. Verify all **acceptance criteria** in the phase file before considering the phase done.
4. Phases 2 and 3 can run in parallel after their prerequisites; Phase 4 needs both.

---

## Testing expectations

- **Unit tests:** pure logic (raycast, clustering, One Euro Filter, parsers).
- **Instrumented tests:** database, critical UI flows.
- No phase merges without its acceptance criteria met.
- Add or update tests for behavior you change; do not add trivial tests that only assert the obvious.

---

## Legal & attribution

- OSM data (ODbL): display "© OpenStreetMap contributors" in About/Attributions (and ideally a small Explore watermark).
- Copernicus DEM: credit "Copernicus DEM, ESA / Airbus" in the same screen.
- Identify the app with a descriptive `User-Agent` on Overpass requests per instance policy.

---

## What not to do

- Do not commit secrets (API keys, `local.properties`, credentials).
- Do not add cloud backends, user auth, social features, routing, or 3D terrain rendering.
- Do not precompute horizon grids or parse GeoTIFF at Explore time.
- Do not load full Overpass JSON responses into memory.
- Do not make feature modules depend on each other.
- Do not create git commits or open PRs unless the user explicitly asks.
- Do not edit `internal/Requirements.md` or phase docs unless the user requests spec changes.

---

## Code style

- Match [Now in Android](https://github.com/android/nowinandroid) conventions: naming, module boundaries, convention plugins, theme pattern (`NofARTheme`).
- Keep diffs minimal and scoped to the task — no drive-by refactors.
- Prefer self-explanatory code; comment only non-obvious business logic.
- Run `detekt` and `lint` before considering Android work complete.

---

## Pull requests & commits

- Branch from `main`; keep PRs focused on one phase or feature slice.
- PR title: concise description of the change (e.g. `Phase 0: NiA module scaffold and navigation shell`).
- PR body: what changed, how to verify, link to phase acceptance criteria addressed.
- Only commit when the user requests it.
