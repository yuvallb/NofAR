# NofAR Database Schema

## Version history

- **1** — initial tables (`region`, `geo_entity`, `region_entity_coverage`, `dem_tile`, `tile_coverage`)
- **2** — adds `region.label_language` and `region_entity_coverage.display_name` (destructive fallback)

Room-managed tables:

- `region` — circular AOI metadata with derived bounding box columns
- `geo_entity` — global deduplicated OSM entities (`row_id` integer PK, `id` unique OSM key)
- `region_entity_coverage` — region ↔ entity junction for garbage collection
- `dem_tile` — global DEM tile metadata with reference counts
- `tile_coverage` — region ↔ tile junction for reference counting

Additional SQLite objects created in `RTreeCallback.onCreate`:

- `geo_entity_rtree` — R-Tree virtual table keyed by `geo_entity.row_id`
- `geo_entity_ai`, `geo_entity_au`, `geo_entity_ad` — triggers keeping the R-Tree in sync

Pre-release builds use `fallbackToDestructiveMigration`; proper migrations should be added before shipping schema changes.

Exported JSON snapshots are written to `schemas/` by the Room KSP processor
during `./gradlew :core:database:kspDebugKotlin`.
