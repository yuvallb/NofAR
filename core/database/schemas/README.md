# NofAR Database Schema

## Version history

- **1** — public baseline (`region`, `geo_entity`, `region_entity_coverage`, `dem_tile`,
  `tile_coverage`). Includes `region.label_language`, `region_entity_coverage.display_name`,
  `geo_entity.footprint_radius_m`, and `geo_entity.elevation` as INTEGER meters.

- **2** — cell-based coverage (`coverage_set`, `coverage_cell`, `coverage_entity`). Migrates each
  v1 circular `region` to a `coverage_set` with 1° cells intersecting the old collection disk
  (`radiusM + DATA_COLLECTION_RADIUS_PADDING_M`). Drops `region`, `tile_coverage`, and
  `region_entity_coverage`. Keeps `geo_entity`, `dem_tile`, and the R-Tree objects unchanged.

Pre-publish schema history was flattened into v1. Production `DatabaseModule` registers
`NofARDatabaseMigrations.ALL` and does **not** call `fallbackToDestructiveMigration`.
Add a new `Migration` before bumping `@Database(version = …)`.

Room-managed tables (v2):

- `coverage_set` — named coverage download metadata (no center/radius/bbox columns)
- `coverage_cell` — coverage set ↔ 1° cell junction (geometry); app code maintains `dem_tile.ref_count`
- `coverage_entity` — coverage set ↔ entity junction (`ON DELETE CASCADE` from coverage_set and geo_entity)
- `geo_entity` — global deduplicated OSM entities (`row_id` integer PK, `id` unique OSM key)
- `dem_tile` — global DEM tile metadata with reference counts

Additional SQLite objects created in `RTreeCallback.onCreate` / migrations:

- `geo_entity_rtree` — R-Tree virtual table keyed by `geo_entity.row_id`
- `geo_entity_ai`, `geo_entity_au`, `geo_entity_ad` — triggers keeping the R-Tree in sync

Exported JSON snapshots are written to `schemas/` by the Room KSP processor
during `./gradlew :core:database:kspDebugKotlin`.
