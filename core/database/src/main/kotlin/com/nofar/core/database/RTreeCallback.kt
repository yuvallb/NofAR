package com.nofar.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * SQLite R-Tree virtual table for [GeoEntityEntity] spatial queries.
 * Uses integer [GeoEntityEntity.row_id] because R-Tree requires an integer primary key.
 */
class RTreeCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        ensureRTree(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        ensureRTree(db)
    }

    internal companion object {
        fun ensureRTree(db: SupportSQLiteDatabase) {
            createRTree(db)
            backfillMissingEntries(db)
        }

        fun createRTree(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS geo_entity_rtree USING rtree(
                    row_id,
                    min_lat, max_lat,
                    min_lon, max_lon
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS geo_entity_ai AFTER INSERT ON geo_entity BEGIN
                    INSERT INTO geo_entity_rtree(row_id, min_lat, max_lat, min_lon, max_lon)
                    VALUES (NEW.row_id, NEW.lat, NEW.lat, NEW.lon, NEW.lon);
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS geo_entity_au AFTER UPDATE ON geo_entity BEGIN
                    UPDATE geo_entity_rtree SET
                        min_lat = NEW.lat,
                        max_lat = NEW.lat,
                        min_lon = NEW.lon,
                        max_lon = NEW.lon
                    WHERE row_id = NEW.row_id;
                END
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS geo_entity_ad AFTER DELETE ON geo_entity BEGIN
                    DELETE FROM geo_entity_rtree WHERE row_id = OLD.row_id;
                END
                """.trimIndent()
            )
        }

        private fun backfillMissingEntries(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                INSERT INTO geo_entity_rtree(row_id, min_lat, max_lat, min_lon, max_lon)
                SELECT g.row_id, g.lat, g.lat, g.lon, g.lon
                FROM geo_entity AS g
                WHERE g.row_id NOT IN (SELECT row_id FROM geo_entity_rtree)
                """.trimIndent()
            )
        }
    }
}
