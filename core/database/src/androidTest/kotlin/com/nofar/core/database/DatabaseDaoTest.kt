package com.nofar.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nofar.core.database.model.DemTileEntity
import com.nofar.core.database.model.GeoEntityEntity
import com.nofar.core.database.model.RegionEntity
import com.nofar.core.database.model.RegionEntityCoverageEntity
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.ResolutionLevel
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegionDaoTest {
    private lateinit var fixtures: DatabaseTestFixtures

    @Before
    fun setUp() {
        fixtures = DatabaseTestFixtures(TestDatabase.inMemory())
    }

    @After
    fun tearDown() {
        fixtures.database.close()
    }

    @Test
    fun upsertAndReadRegion() = runTest {
        val region = sampleRegion(name = "Galilee")
        fixtures.regionDao.upsert(region)
        val loaded = fixtures.regionDao.getById(region.id)
        assertThat(loaded?.name).isEqualTo("Galilee")
    }

    @Test
    fun listRegions_sortedByUpdatedAtDesc() = runTest {
        val older =
            sampleRegion(
                id = UUID.randomUUID().toString(),
                name = "Older",
                updatedAt = 1_000L
            )
        val newer =
            sampleRegion(
                id = UUID.randomUUID().toString(),
                name = "Newer",
                updatedAt = 2_000L
            )
        fixtures.regionDao.upsert(older)
        fixtures.regionDao.upsert(newer)
        assertThat(fixtures.regionDao.getAll().map { it.name }).containsExactly("Newer", "Older").inOrder()
    }

    private fun sampleRegion(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test",
        updatedAt: Long = System.currentTimeMillis()
    ): RegionEntity = RegionEntity(
        id = id,
        name = name,
        centerLat = 32.0,
        centerLon = 35.0,
        radiusM = 10_000.0,
        minLat = 31.9,
        maxLat = 32.1,
        minLon = 34.9,
        maxLon = 35.1,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        downloadStatus = DownloadStatus.READY.name,
        downloadProgressPct = 100,
        osmDatasetVersion = null,
        estimatedSizeBytes = 0,
        entityCount = 0
    )
}

@RunWith(AndroidJUnit4::class)
class GeoEntityDaoTest {
    private lateinit var fixtures: DatabaseTestFixtures

    @Before
    fun setUp() {
        fixtures = DatabaseTestFixtures(TestDatabase.inMemory())
    }

    @After
    fun tearDown() {
        fixtures.database.close()
    }

    @Test
    fun upsert_sameOsmIdTwice_singleRow() = runTest {
        val entity = sampleEntity(id = "node/42")
        fixtures.geoEntityUpserter.upsert(entity)
        fixtures.geoEntityUpserter.upsert(entity.copy(name = "Updated"))
        assertThat(fixtures.geoEntityDao.getByOsmId("node/42")?.name).isEqualTo("Updated")
    }

    @Test
    fun rTreeQuery_returnsEntitiesWithinRadius() = runTest {
        repeat(20) { index ->
            fixtures.geoEntityUpserter.upsert(
                sampleEntity(
                    id = "node/$index",
                    lat = 32.0 + index * 0.001,
                    lon = 35.0 + index * 0.001,
                    type = GeoEntityType.CITY.name
                )
            )
        }
        val results =
            fixtures.spatialQuery.queryWithinRadius(
                lat = 32.005,
                lon = 35.005,
                radiusM = 2_000.0,
                resolutionLevel = ResolutionLevel.Medium
            )
        assertThat(results).isNotEmpty()
        assertThat(results.all { it.type == GeoEntityType.CITY.name }).isTrue()
    }

    @Test
    fun rTreeBackfill_restoresMissingSpatialIndexRows() = runTest {
        fixtures.geoEntityUpserter.upsert(
            sampleEntity(
                id = "node/backfill",
                lat = 32.01,
                lon = 35.01,
                type = GeoEntityType.TOWN.name
            )
        )
        fixtures.database.openHelper.writableDatabase.execSQL("DELETE FROM geo_entity_rtree")
        RTreeCallback.backfillMissingEntriesSafely(fixtures.database.openHelper.writableDatabase)

        val results =
            fixtures.spatialQuery.queryWithinRadius(
                lat = 32.01,
                lon = 35.01,
                radiusM = 2_000.0,
                resolutionLevel = ResolutionLevel.Medium
            )

        assertThat(results.map { it.id }).contains("node/backfill")
    }

    @Test
    fun regionQuery_withoutCoverage_fallsBackToEntitiesInRegionBounds() = runTest {
        fixtures.geoEntityUpserter.upsert(
            sampleEntity(
                id = "node/fallback",
                lat = 32.02,
                lon = 35.02,
                type = GeoEntityType.VILLAGE.name
            )
        )
        val regionId = UUID.randomUUID().toString()
        val results =
            fixtures.spatialQuery.queryWithinRadiusForRegion(
                regionId = regionId,
                regionCenterLat = 32.0,
                regionCenterLon = 35.0,
                regionRadiusM = 10_000.0,
                lat = 32.0,
                lon = 35.0,
                radiusM = 10_000.0,
                resolutionLevel = ResolutionLevel.Medium
            )

        assertThat(results.map { it.id }).contains("node/fallback")
    }

    @Test
    fun deleteEntitiesExclusiveToRegion_keepsSharedEntities() = runTest {
        val regionA = UUID.randomUUID().toString()
        val regionB = UUID.randomUUID().toString()
        fixtures.geoEntityUpserter.upsert(sampleEntity(id = "node/shared"))
        fixtures.geoEntityUpserter.upsert(sampleEntity(id = "node/only-a"))
        fixtures.coverageDao.insert(RegionEntityCoverageEntity(regionA, "node/shared"))
        fixtures.coverageDao.insert(RegionEntityCoverageEntity(regionA, "node/only-a"))
        fixtures.coverageDao.insert(RegionEntityCoverageEntity(regionB, "node/shared"))

        fixtures.geoEntityDao.deleteEntitiesExclusiveToRegion(regionA)
        fixtures.coverageDao.deleteForRegion(regionA)

        assertThat(fixtures.geoEntityDao.getByOsmId("node/shared")).isNotNull()
        assertThat(fixtures.geoEntityDao.getByOsmId("node/only-a")).isNull()
    }

    private fun sampleEntity(
        id: String,
        lat: Double = 32.0,
        lon: Double = 35.0,
        type: String = GeoEntityType.PEAK.name
    ): GeoEntityEntity = GeoEntityEntity(
        id = id,
        osmType = "NODE",
        name = "Entity $id",
        type = type,
        lat = lat,
        lon = lon,
        elevation = 100.0,
        elevationSource = "OSM_TAG",
        lastSeenAt = System.currentTimeMillis()
    )
}

@RunWith(AndroidJUnit4::class)
class DemTileDaoTest {
    private lateinit var fixtures: DatabaseTestFixtures

    @Before
    fun setUp() {
        fixtures = DatabaseTestFixtures(TestDatabase.inMemory())
    }

    @After
    fun tearDown() {
        fixtures.database.close()
    }

    @Test
    fun refCountIncrementAndDecrement() = runTest {
        val tileId = "Copernicus_DSM_COG_10_N32_00_E035_00_DEM"
        fixtures.demTileDao.upsert(
            DemTileEntity(
                tileId = tileId,
                filePath = "dem/$tileId.bin",
                width = 3600,
                height = 3600,
                tileLat = 32,
                tileLon = 35,
                noDataValue = -9999f,
                sizeBytes = 1000,
                refCount = 1,
                lastAccessedAt = System.currentTimeMillis()
            )
        )
        fixtures.demTileDao.incrementRefCount(tileId)
        assertThat(fixtures.demTileDao.getById(tileId)?.refCount).isEqualTo(2)
        fixtures.demTileDao.decrementRefCount(tileId, System.currentTimeMillis())
        assertThat(fixtures.demTileDao.getById(tileId)?.refCount).isEqualTo(1)
    }
}
