package com.nofar.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object NofARDatabaseMigrations {
    /**
     * Schema 1 and 2 share the same entity layout; only the Room identity hash / version bumped.
     * Keep an explicit no-op migration so upgrades never hit destructive fallback.
     */
    val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No structural changes between schema exports 1 and 2.
            }
        }

    /** Adds optional place footprint radius used by Explore area labels. */
    val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `geo_entity` ADD COLUMN `footprint_radius_m` REAL"
                )
            }
        }

    /**
     * Persist entity elevations as whole meters (INTEGER). DEM samples and OSM `ele` are rounded.
     */
    val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TRIGGER IF EXISTS geo_entity_ai")
                db.execSQL("DROP TRIGGER IF EXISTS geo_entity_au")
                db.execSQL("DROP TRIGGER IF EXISTS geo_entity_ad")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `geo_entity_new` (
                        `row_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `id` TEXT NOT NULL,
                        `osm_type` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `lat` REAL NOT NULL,
                        `lon` REAL NOT NULL,
                        `elevation` INTEGER,
                        `elevation_source` TEXT,
                        `last_seen_at` INTEGER NOT NULL,
                        `footprint_radius_m` REAL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `geo_entity_new` (
                        `row_id`, `id`, `osm_type`, `name`, `type`, `lat`, `lon`,
                        `elevation`, `elevation_source`, `last_seen_at`, `footprint_radius_m`
                    )
                    SELECT
                        `row_id`, `id`, `osm_type`, `name`, `type`, `lat`, `lon`,
                        CASE
                            WHEN `elevation` IS NULL THEN NULL
                            ELSE CAST(ROUND(`elevation`) AS INTEGER)
                        END,
                        `elevation_source`, `last_seen_at`, `footprint_radius_m`
                    FROM `geo_entity`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `geo_entity`")
                db.execSQL("ALTER TABLE `geo_entity_new` RENAME TO `geo_entity`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_geo_entity_id` ON `geo_entity` (`id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_geo_entity_type` ON `geo_entity` (`type`)"
                )
                RTreeCallback.createRTree(db)
            }
        }
}
