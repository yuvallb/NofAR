---
name: Country pack compression
overview: Replace GLO-30 float32 circular regions with GLO-90 int16 1° cell coverage. On-device packs from Copernicus + tiled Overpass. Existing v3 rasters are deleted on upgrade; users re-download.
todos:
  - id: t1-dem-v4-glo90
    content: DemBinaryFormat v4 int16 + GLO-90 fetch; wipe v3 rasters; mark coverage PARTIAL
    status: completed
  - id: t2-quality-report
    content: One-off 30 m vs 90 m visibility measurement script and report (does not block T3+)
    status: pending
  - id: t3-cell-coverage
    content: "Room MIGRATION_1_2: coverage_set/cell/entity; membership = cell lookup; 3×3 local unit"
    status: completed
  - id: t4-ingest-budget
    content: Per-cell Overpass without out geom; type-default footprints; byte-budget cap; peak 3×3 max fill
    status: pending
  - id: t5-packs-ux
    content: Hardcoded IL+CH+GR catalog; Simple/Expert UX; cache confirm-and-raise; string/docs sweep
    status: pending
isProject: false
---

# Country packs — task list

Locked product rules (do not re-open while implementing):

- DEM is GLO-90 int16 everywhere. GLO-30 is deleted. One file per 1° cell.
- Coverage is a ref-counted set of 1° cells. No circular membership.
- Packs are built on-device. No hosted pack artifacts.
- Explore rasters stay uncompressed mmap. No on-disk block compression.
- Local Simple-mode unit is a **3×3 cell ring** around the observer cell.
- Country catalog is a **small hardcoded list of curated cell ids**, not a bbox and not shipped polygons. First cut: **Israel**, **Switzerland**, and **Greece**. Runtime gate: offer a pack only if estimated on-disk DEM bytes ≤ 50% of the **current** cache limit.
- OSM place tags everywhere (packs and 3×3): **city, town, village, peak only**.
- On upgrade: **delete non-v4 rasters**, mark all coverage PARTIAL, user re-downloads. No v3 dual-read.
- Expert Prepare: **tap cells to add/remove**. No radius slider.
- Pack install: confirm dialog, then raise cache limit to `max(current, packDiskBytes + 200 MB)`.
- 90 m is locked. The 30-vs-90 script is a report, not a merge gate.
- Update Requirements, AGENTS.md, README, privacy, Fastlane copy in T5.

Constants to put in `AppConfig` / format code (not tunables mid-task):

| Name | Value |
|---|---|
| v4 magic | `NOFAR_DM4` |
| sampleType int16 | `1` |
| scale / offset | `1.0` / `0.0` |
| int16 no-data | `Short.MIN_VALUE` (`dem_tile.no_data_value` stores `-32768.0`) |
| quantization | round-to-nearest meters; clamp to `[-1000, 9000]` |
| `OCCLUSION_TOLERANCE_M` | `1.0` |
| `ALTITUDE_GPS_DEM_DISAGREE_METERS` | `20` |
| Explore / horizon query radius | `100_000` m, observer-centered |
| Peak DEM fill window | 3×3 max of plausible samples |
| Footprint defaults (m) | city `5000`, town `1500`, village `500`, peak `null` |
| `MAX_OSM_ENTITIES` | `50_000` **per cell** |
| `MAX_TILE_BYTES` / converter input | `16 MB` |
| Overpass gap | serialize cells; ≥ 1 s between requests; keep 3-mirror failover + backoff |
| Cellular / Wi-Fi copy | show existing cellular warning when estimated **wire** ≥ 50 MB; pack CTAs always include “Wi-Fi recommended” |
| GLO-90 disk bytes below 50°N | `1200 × 1200 × 2` ≈ 2.9 MB |
| GLO-90 wire below 50°N | ≈ 5.4 MB |
| Latitude column counts (GLO-90) | 0–50°: 1200; 50–60°: 800; 60–70°: 600; 70–80°: 400; 80–85°: 240; 85–90°: 120. Height stays 1200. Never assume square. |

Do not: gzip Explore rasters; ship an unbounded country list; one nationwide Overpass query; precomputed horizon grids; two resolutions per cell; Geofabrik PBF; `fallbackToDestructiveMigration`.

---

## T1 — DEM v4 + GLO-90 + wipe

Ship together. A v4-only reader without GLO-90 fetch would leave users unable to recover after the wipe.

1. Extend `DemBinaryFormat` to v4: magic `NOFAR_DM4`; header fields `width`, `height`, `originLat`, `originLon`, `sampleType`, `scale`, `offset`, `noData`. Writer writes int16 meters only. Reader maps `Short` and converts with scale/offset. Reject any other magic.
2. Quantize in `DemTileWriter` (converter stays float32). Round-to-nearest; store `Short.MIN_VALUE` for no-data / non-finite / out of range.
3. Fix pixel registration in `DemTileReader`: nearest `floor(frac * width/height)` with `frac` in `[0,1)` over the 1° cell; bilinear uses pixel centers `frac * dim - 0.5`. Stop dividing by `width - 1`.
4. Set `TerrainRayMarcher.OCCLUSION_TOLERANCE_M = 1.0`.
5. Point `DemTileFetcher` at `https://copernicus-dem-90m.s3.eu-central-1.amazonaws.com`. Change `DemTileId` to `Copernicus_DSM_COG_30_{N|S}{lat}_00_{E|W}{lon}_00_DEM`. Delete GLO-30 / `COG_10` code and tests.
6. Drop `MAX_TILE_BYTES` and `GeoTiffConverter.MAX_INPUT_BYTES` to 16 MB. Update `PrepareEstimator` to the latitude table above (wire ≈ 5.4 MB and disk ≈ 2.9 MB at 1200×1200; scale by `width/1200`).
7. **Wipe:** on first process start after this ships, delete every DEM `.bin` whose magic is not `NOFAR_DM4`, clear `dem_tile` rows for those files, mark every region/coverage `PARTIAL`, set a one-shot prefs flag so this does not repeat. Show a blocking in-app message: map data must be downloaded again (needs network).
8. Tests: writer/reader round-trip int16; no-data sentinel; polar non-square dimensions; pixel-registration unit tests at cell edges; fetcher URL/id tests; wipe idempotence.

`./gradlew spotlessCheck detekt lint test`

---

## T2 — 30 m vs 90 m report (parallel with T3, not a gate)

1. Add an uncommitted/one-off script under `scripts/` that fetches the same cell(s) from both Copernicus buckets, converts with the production converter/writer, and runs the production raycaster on ≥ 8 viewpoints (flat, coastal, ridge, including Tel Azeka) with each viewpoint’s real candidate set.
2. Print: visibility flip rate 30 m vs 90 m; mean abs horizon-elevation-angle delta per azimuth; observer GPS−DEM error distribution.
3. Do not commit rasters. Do not block T3–T5 on the numbers. File the printed report in the PR description or a gist; do not add a pass/fail CI job.

---

## T3 — Cell coverage model + Room 1→2

1. Schema v2 (explicit `MIGRATION_1_2` in `NofARDatabaseMigrations.ALL` **before** bumping `@Database(version = 2)`):
   - `coverage_set`: id, name, download_status, progress, osm_dataset_version, label_language, timestamps, estimated_size_bytes, entity_count. **No** center, radius, or bbox columns.
   - `coverage_cell`: `(coverage_set_id, cell_id)` cascade delete; increments `dem_tile.ref_count` the way `tile_coverage` / `acquireTileForRegion` does today.
   - `coverage_entity`: former `region_entity_coverage` (set id, entity id, display_name).
   - Keep `dem_tile` and `geo_entity`. Do **not** recreate `geo_entity_rtree` / `_ai` / `_au` / `_ad`.
2. Migrate each old `region` to a `coverage_set` named as today. Cell list = intersecting 1° cells of the old collection disk (`radiusM + DATA_COLLECTION_RADIUS_PADDING_M`). Copy `tile_coverage` / `region_entity_coverage` onto the new junction tables, then drop `region`, `tile_coverage`, `region_entity_coverage`.
3. Export `core/database/schemas/com.nofar.core.database.NofARDatabase/2.json`. Update `core/database/schemas/README.md`. Add an instrumented `MigrationTestHelper` test for 1→2.
4. Membership: `floor(lat), floor(lon)` → cell present and file readable. Delete `RegionBounds.containsPoint` as membership, `ContributingRegions`, `CONTRIBUTING_REGION_MAX_DISTANCE_M`, `DATA_COLLECTION_RADIUS_PADDING_M`, `REGION_RADIUS_MIN_KM/MAX_KM`, `SIMPLE_MODE_DEFAULT_RADIUS_M`. Keep haversine as geometry math where still needed (distance labels, movement thresholds).
5. Explore entity query: R-Tree bbox around the observer with radius **100 km**; drop the circular post-filter. Do **not** query the union of all resident cells. Horizon max distance is `min(100 km, distance to first missing DEM sample)`. Missing DEM remains a blocker (`HorizonProfileComputer` / `TerrainRayMarcher` fail-closed).
6. Local download unit: 3×3 cells around `floor(observer lat/lon)`, including the observer cell. Same object type as a country pack (`coverage_set` + `coverage_cell`).
7. Expert Prepare: map tap toggles a cell in the draft set; no radius slider. Reject save/download when estimated on-disk DEM bytes exceed 50% of the current cache limit (same gate as packs).
8. Replace `MAX_DEM_TILES_PER_REGION = 64` with the byte-budget check (latitude table × cell list).
9. Domain/UI types: `Region` → `CoverageSet` (or equivalent) through repositories, Home, Prepare, Explore. `InsideRegionUseCase` → cell membership. Exit grace (2 minutes) keys off cell presence.
10. Tests: membership at cell edges; 3×3 construction; migration of a v1 circle to cells; byte-budget reject; spatial query is observer-centered.

`./gradlew spotlessCheck detekt lint test`

---

## T4 — Ingest, footprints, peaks, cache math

1. `OverpassQueryBuilder`: bbox = the 1° cell (no circle helper). Drop `.boundaries out geom` and the boundary recurse. Place regex `city|town|village` plus `natural=peak`. Timeout/User-Agent/mirrors unchanged.
2. Orchestrator: one Overpass request per cell, sequential, ≥ 1 s apart, existing backoff/failover. Resume by skipping cells that already have OSM ingest completed. Stop a cell at `MAX_OSM_ENTITIES` (50k). Dedup `geo_entity` by OSM id as today.
3. `footprint_radius_m`: write the type-default table (city 5000 / town 1500 / village 500 / peak null). Delete gyration/boundary footprint code.
4. Auto-name: country pack → catalog name. Otherwise nearest city/town/village in the cell set via existing name resolver; fallback `"Downloaded maps"`.
5. `MissingEntityElevationFiller`: for `natural=peak` without OSM `ele`, fill from the max of a 3×3 window of plausible DEM samples; other types stay single-sample. OSM `ele` still wins. Set `ALTITUDE_GPS_DEM_DISAGREE_METERS = 20`.
6. Estimator and download UI: per-cell wire/disk from the latitude table; OSM estimate from cell area with the current bytes/km² constant.
7. Tests: query string has no `out geom` and no hamlet/locality/isolated_dwelling; per-cell cap; footprint defaults; peak window-max vs single sample; sequential request order.

`./gradlew spotlessCheck detekt lint test`

---

## T5 — Catalog, UX, cache confirm, docs

1. Add `CountryPackCatalog` with curated cell ids (not bounding boxes) for **Israel**, **Switzerland**, and **Greece**. Adding a country later is a data change to this object plus the 50% gate. Do not include France (fails the default 500 MB cache gate). Greece is land-containing cells only: mainland, Peloponnese, Crete, Ionian, Aegean, Rhodes, and Kastellorizo (`N36 E29`) — do not fill empty sea between Rhodes and Kastellorizo. Expect ~50 cells / ~150 MB disk / ~270 MB wire; still under the default 250 MB offer gate. Overpass stays one serialized request per cell (longer than IL/CH).
2. Offer a catalog pack iff the observer cell is in that pack’s set **and** estimated on-disk DEM ≤ 50% of the current cache limit.
3. Simple mode, no coverage yet:
   - If a pack is offered: **Download maps for \<name\> (~N MB, Wi-Fi recommended)**
   - Else: **Download maps for this area (~26 MB)** for the 3×3 ring (show the real estimate).
4. Simple Home: one union coverage overlay on the map; no named region list; no “region” copy.
5. Expert Home: list of coverage sets (name, status, size, cell count). No center/radius metadata.
6. Leaving coverage: “You're leaving downloaded maps” + offer to download adjacent cells. Never “outside region X”. Keep the 2-minute grace period, keyed to cell presence.
7. Pack / large-set install: dialog states the new DEM cache limit (`packDisk + 200 MB` if larger than current). Proceed only on confirm; then persist the new limit and start download.
8. Retire strings: `explore_outside_region_title`, `explore_outside_region_message`, `explore_grace_expired_message`, `explore_partial_region_warning`, `explore_region_data_missing`, location-permission rationale that says “region”, hardcoded `+ ADD REGION`, `LOCAL REGIONS`, `Prepare Region`, `Region name`, and `NofARRegionCard` center/radius line. Replace with maps/coverage wording.
9. Docs in the same change: `internal/Requirements.md` (GLO-90, cell coverage, 3×3, place tags, no circles), `AGENTS.md` hard-constraint table, `README.md`, `docs/privacy.md`, `fastlane/metadata/android/en-US/full_description.txt` (and changelog for the next versionCode). Attribution stays OSM + “Copernicus DEM, ESA / Airbus”.
10. Tests: catalog offer/non-offer; 50% gate; cache-limit confirm updates prefs; Simple CTA branch; grace keyed to cells.

`./gradlew spotlessCheck detekt lint test`

---

## Sequence

```
T1 (v4 + GLO-90 + wipe) ──► T3 (cells + migration) ──► T4 (ingest) ──► T5 (packs + UX + docs)
         │
         └── T2 (report, parallel, non-blocking)
```

Prefer releasing T1–T5 together so users re-download once. If T1 must ship alone, the wipe still runs; T3 then migrates PARTIAL circles to cell sets without deleting v4 files.

Done when: a fresh install can download the Israel, Switzerland, or Greece pack or a 3×3 ring, Explore membership is cell lookup, quality gate is green, and the listed docs match the shipped behavior.
