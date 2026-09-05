@file:Suppress("LongMethod")

package com.nofar.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlin.math.cos
import kotlin.math.floor

/**
 * Explicit Room migrations from the public baseline ([NofARDatabase] version 1).
 *
 * Add a new [Migration] here before bumping `@Database(version = …)`.
 * Do not use destructive fallback on the production builder.
 */
object NofARDatabaseMigrations {
    /** Matches historical [AppConfig.DATA_COLLECTION_RADIUS_PADDING_M] used for v1 circle → cells. */
    private const val DATA_COLLECTION_RADIUS_PADDING_M = 5_000.0

    val MIGRATION_1_2: Migration =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS coverage_set (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        download_status TEXT NOT NULL,
                        download_progress_pct INTEGER NOT NULL,
                        osm_dataset_version INTEGER,
                        estimated_size_bytes INTEGER NOT NULL,
                        entity_count INTEGER NOT NULL,
                        label_language TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_coverage_set_updated_at ON coverage_set(updated_at)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS coverage_cell (
                        coverage_set_id TEXT NOT NULL,
                        cell_id TEXT NOT NULL,
                        PRIMARY KEY(coverage_set_id, cell_id),
                        FOREIGN KEY(coverage_set_id) REFERENCES coverage_set(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_coverage_cell_cell_id ON coverage_cell(cell_id)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS coverage_entity (
                        coverage_set_id TEXT NOT NULL,
                        entity_id TEXT NOT NULL,
                        display_name TEXT NOT NULL,
                        PRIMARY KEY(coverage_set_id, entity_id),
                        FOREIGN KEY(coverage_set_id) REFERENCES coverage_set(id) ON DELETE CASCADE,
                        FOREIGN KEY(entity_id) REFERENCES geo_entity(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_coverage_entity_entity_id ON coverage_entity(entity_id)")

                db.execSQL(
                    """
                    INSERT INTO coverage_set (
                        id, name, created_at, updated_at, download_status, download_progress_pct,
                        osm_dataset_version, estimated_size_bytes, entity_count, label_language
                    )
                    SELECT
                        id, name, created_at, updated_at, download_status, download_progress_pct,
                        osm_dataset_version, estimated_size_bytes, entity_count, label_language
                    FROM region
                    """.trimIndent()
                )

                // Derive 1° cells from the old circular collection disk, not from tile_coverage
                // (which held GLO-30 COG_10 ids and could be empty for undownloaded regions).
                db.query(
                    """
                    SELECT id, center_lat, center_lon, radius_m FROM region
                    """.trimIndent()
                ).use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow("id")
                    val latIndex = cursor.getColumnIndexOrThrow("center_lat")
                    val lonIndex = cursor.getColumnIndexOrThrow("center_lon")
                    val radiusIndex = cursor.getColumnIndexOrThrow("radius_m")
                    while (cursor.moveToNext()) {
                        val regionId = cursor.getString(idIndex)
                        val centerLat = cursor.getDouble(latIndex)
                        val centerLon = cursor.getDouble(lonIndex)
                        val radiusM = cursor.getDouble(radiusIndex) + DATA_COLLECTION_RADIUS_PADDING_M
                        for (cellId in cellsIntersectingCircle(centerLat, centerLon, radiusM)) {
                            db.execSQL(
                                """
                                INSERT OR IGNORE INTO coverage_cell (coverage_set_id, cell_id)
                                VALUES (?, ?)
                                """.trimIndent(),
                                arrayOf(regionId, cellId)
                            )
                        }
                    }
                }

                db.execSQL(
                    """
                    INSERT OR IGNORE INTO coverage_entity (coverage_set_id, entity_id, display_name)
                    SELECT region_id, entity_id, display_name FROM region_entity_coverage
                    WHERE entity_id IN (SELECT id FROM geo_entity)
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE IF EXISTS region_entity_coverage")
                db.execSQL("DROP TABLE IF EXISTS tile_coverage")
                db.execSQL("DROP TABLE IF EXISTS region")
            }
        }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)

    /**
     * 1° GLO-90 cell ids intersecting the collection disk around [centerLat]/[centerLon].
     * Used only during v1→v2 migration; mirrors GeoMathBounds + DemTileId.intersectingTiles.
     */
    internal fun cellsIntersectingCircle(centerLat: Double, centerLon: Double, radiusM: Double): List<String> {
        val deltaLat = radiusM / 111_320.0
        val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(1e-6)
        val deltaLon = radiusM / (111_320.0 * cosLat)
        val minLat = floor(centerLat - deltaLat).toInt()
        val maxLat = floor(centerLat + deltaLat).toInt()
        val minLon = floor(centerLon - deltaLon).toInt()
        val maxLon = floor(centerLon + deltaLon).toInt()
        val cells = mutableListOf<String>()
        for (lat in minLat..maxLat) {
            if (lat < -90 || lat > 89) continue
            for (rawLon in minLon..maxLon) {
                val lon = ((rawLon + 180) % 360 + 360) % 360 - 180
                if (lon == -180 && rawLon > minLon) continue
                cells += glo90CellId(lat, lon)
            }
        }
        return cells.distinct()
    }

    private fun glo90CellId(lat: Int, lon: Int): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "W"
        return "Copernicus_DSM_COG_30_${ns}${kotlin.math.abs(lat).toString().padStart(2, '0')}" +
            "_00_${ew}${kotlin.math.abs(lon).toString().padStart(3, '0')}_00_DEM"
    }
}
