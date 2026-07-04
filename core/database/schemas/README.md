# NofAR Database Schema

## Version 1 (initial)

Room-managed tables:

- `region` — circular AOI metadata with derived bounding box columns
- `geo_entity` — global deduplicated OSM entities (`row_id` integer PK, `id` unique OSM key)
- `region_entity_coverage` — region ↔ entity junction for garbage collection
- `dem_tile` — global DEM tile metadata with reference counts
- `tile_coverage` — region ↔ tile junction for reference counting

Additional SQLite objects created in `RTreeCallback.onCreate`:

- `geo_entity_rtree` — R-Tree virtual table keyed by `geo_entity.row_id`
- `geo_entity_ai`, `geo_entity_au`, `geo_entity_ad` — triggers keeping the R-Tree in sync

Fresh installs start at version 1. Future migrations should increment
`NofARDatabase.version` and add matching classes under `migration/`.

Exported JSON snapshots are written to `schemas/` by the Room KSP processor
during `./gradlew :core:database:kspDebugKotlin`.
