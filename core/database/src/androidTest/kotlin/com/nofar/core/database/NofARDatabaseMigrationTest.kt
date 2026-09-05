package com.nofar.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NofARDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            NofARDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory()
        )

    @Test
    fun freshInstall_createsCoverageTablesAndRTree() = runTest {
        val db = TestDatabase.inMemory(context)
        GeoEntitySpatialQuery(
            db.geoEntitySpatialDao(),
            db.geoEntityDao(),
            db.coverageEntityDao()
        ).backfillMissingRTreeEntries()
        assertThat(db.coverageSetDao().getAll()).isEmpty()
        db.close()
    }

    @Test
    fun migrate1To2_derivesCellsFromCircleGeometry() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO region (
                    id, name, center_lat, center_lon, radius_m,
                    min_lat, max_lat, min_lon, max_lon,
                    created_at, updated_at, download_status, download_progress_pct,
                    osm_dataset_version, estimated_size_bytes, entity_count, label_language
                ) VALUES (
                    'region-1', 'Galilee', 32.5, 35.5, 10000.0,
                    31.9, 32.1, 34.9, 35.1,
                    1000, 2000, 'READY', 100,
                    NULL, 0, 0, 'DEFAULT'
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO region (
                    id, name, center_lat, center_lon, radius_m,
                    min_lat, max_lat, min_lon, max_lon,
                    created_at, updated_at, download_status, download_progress_pct,
                    osm_dataset_version, estimated_size_bytes, entity_count, label_language
                ) VALUES (
                    'region-empty', 'Pending', 31.2, 34.8, 5000.0,
                    31.0, 31.4, 34.6, 35.0,
                    1000, 2000, 'NOT_DOWNLOADED', 0,
                    NULL, 0, 0, 'DEFAULT'
                )
                """.trimIndent()
            )
            // Legacy GLO-30 tile id — must NOT become a coverage_cell as-is.
            execSQL(
                """
                INSERT INTO dem_tile (
                    tile_id, file_path, width, height, tile_lat, tile_lon,
                    no_data_value, size_bytes, ref_count, last_accessed_at
                ) VALUES (
                    'Copernicus_DSM_COG_10_N32_00_E035_00_DEM',
                    'dem/legacy.bin', 3600, 3600, 32, 35,
                    -9999.0, 100, 1, 1000
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO tile_coverage (region_id, tile_id)
                VALUES ('region-1', 'Copernicus_DSM_COG_10_N32_00_E035_00_DEM')
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, NofARDatabaseMigrations.MIGRATION_1_2).apply {
            query("SELECT name FROM coverage_set WHERE id = 'region-1'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("Galilee")
            }
            query(
                """
                SELECT cell_id FROM coverage_cell
                WHERE coverage_set_id = 'region-1'
                ORDER BY cell_id
                """.trimIndent()
            ).use { cursor ->
                val cells = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    cells += cursor.getString(0)
                }
                assertThat(cells).isNotEmpty()
                assertThat(cells).contains("Copernicus_DSM_COG_30_N32_00_E035_00_DEM")
                assertThat(cells).doesNotContain("Copernicus_DSM_COG_10_N32_00_E035_00_DEM")
                cells.forEach { cellId ->
                    assertThat(cellId).contains("COG_30")
                }
            }
            query(
                """
                SELECT COUNT(*) FROM coverage_cell WHERE coverage_set_id = 'region-empty'
                """.trimIndent()
            ).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isGreaterThan(0)
            }
            query(
                """
                SELECT name FROM sqlite_master
                WHERE type = 'table' AND name IN ('region', 'tile_coverage', 'region_entity_coverage')
                """.trimIndent()
            ).use { cursor ->
                assertThat(cursor.count).isEqualTo(0)
            }
            close()
        }
    }

    @Test
    fun cellsIntersectingCircle_includesCenterCell() {
        val cells = NofARDatabaseMigrations.cellsIntersectingCircle(32.5, 35.5, 15_000.0)
        assertThat(cells).contains("Copernicus_DSM_COG_30_N32_00_E035_00_DEM")
    }

    private companion object {
        private const val TEST_DB = "migration-test"
    }
}
