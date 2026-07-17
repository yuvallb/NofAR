package com.nofar.core.visibility

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nofar.core.common.DefaultDispatchers
import com.nofar.core.data.dem.DemTileWriter
import com.nofar.core.data.repository.DefaultDemTileRepository
import com.nofar.core.data.repository.DefaultGeoEntityRepository
import com.nofar.core.database.GeoEntitySpatialQuery
import com.nofar.core.database.dao.GeoEntityUpserter
import com.nofar.core.database.model.GeoEntityEntity
import com.nofar.core.database.model.RegionEntity
import com.nofar.core.database.model.RegionEntityCoverageEntity
import com.nofar.core.database.model.TileCoverageEntity
import com.nofar.core.database.useBundledSqliteWithRTree
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.UserLocation
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PartialRegionVisibilityIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: com.nofar.core.database.NofARDatabase
    private lateinit var demTileRepository: DefaultDemTileRepository
    private lateinit var geoEntityRepository: DefaultGeoEntityRepository
    private lateinit var visibilityUseCase: VisibilityUseCase

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(context, com.nofar.core.database.NofARDatabase::class.java)
                .allowMainThreadQueries()
                .useBundledSqliteWithRTree()
                .build()
        demTileRepository = DefaultDemTileRepository(context, database.demTileDao())
        geoEntityRepository =
            DefaultGeoEntityRepository(
                geoEntityDao = database.geoEntityDao(),
                geoEntityUpserter = GeoEntityUpserter(database.geoEntityDao()),
                spatialQuery =
                GeoEntitySpatialQuery(
                    database.geoEntitySpatialDao(),
                    database.geoEntityDao(),
                    database.regionEntityCoverageDao()
                )
            )
        visibilityUseCase =
            VisibilityUseCase(
                geoEntityRepository = geoEntityRepository,
                demTileRepository = demTileRepository,
                tileCoverageDao = database.tileCoverageDao(),
                demTileDao = database.demTileDao(),
                visibilityEngine = DemRaycastVisibilityEngine(DefaultDispatchers),
                observerElevationResolver = ObserverElevationResolver()
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun partialRegion_missingDemTile_returnsResultsWithWarning() = runTest {
        val regionId = UUID.randomUUID()
        val entityId = "node/1"
        val now = Instant.now().toEpochMilli()
        database.regionDao().upsert(
            RegionEntity(
                id = regionId.toString(),
                name = "Partial",
                centerLat = 32.0,
                centerLon = 35.0,
                radiusM = 10_000.0,
                minLat = 31.9,
                maxLat = 32.1,
                minLon = 34.9,
                maxLon = 35.1,
                createdAt = now,
                updatedAt = now,
                downloadStatus = DownloadStatus.PARTIAL.name,
                downloadProgressPct = 50,
                osmDatasetVersion = null,
                estimatedSizeBytes = 0,
                entityCount = 1
            )
        )
        GeoEntityUpserter(database.geoEntityDao()).upsert(
            GeoEntityEntity(
                id = entityId,
                osmType = "NODE",
                name = "Nearby Peak",
                type = GeoEntityType.PEAK.name,
                lat = 32.05,
                lon = 35.05,
                elevation = 500.0,
                elevationSource = "OSM_TAG",
                lastSeenAt = now
            )
        )
        database.regionEntityCoverageDao().insert(
            RegionEntityCoverageEntity(
                regionId = regionId.toString(),
                entityId = entityId,
                displayName = "Test Peak"
            )
        )
        database.tileCoverageDao().insert(
            TileCoverageEntity(
                regionId = regionId.toString(),
                tileId = "Copernicus_DSM_COG_10_N32_00_E035_00_DEM"
            )
        )

        val result =
            visibilityUseCase.computeForRegion(
                region = sampleRegion(regionId, DownloadStatus.PARTIAL, now),
                location =
                UserLocation(
                    latitude = 32.0,
                    longitude = 35.0,
                    altitudeMeters = 100.0,
                    accuracyMeters = 5f,
                    timestampMillis = now
                )
            )

        assertThat(result.warnings).contains(VisibilityWarning.DEM_TILE_MISSING)
    }

    @Test
    fun readyRegion_withDemTile_computesVisibilityWithoutCrash() = runTest {
        val regionId = UUID.randomUUID()
        val entityId = "node/2"
        val now = Instant.now().toEpochMilli()
        val tileId = "Copernicus_DSM_COG_10_N32_00_E035_00_DEM"
        database.regionDao().upsert(
            RegionEntity(
                id = regionId.toString(),
                name = "Ready",
                centerLat = 32.0,
                centerLon = 35.0,
                radiusM = 10_000.0,
                minLat = 31.9,
                maxLat = 32.1,
                minLon = 34.9,
                maxLon = 35.1,
                createdAt = now,
                updatedAt = now,
                downloadStatus = DownloadStatus.READY.name,
                downloadProgressPct = 100,
                osmDatasetVersion = null,
                estimatedSizeBytes = 0,
                entityCount = 1
            )
        )
        GeoEntityUpserter(database.geoEntityDao()).upsert(
            GeoEntityEntity(
                id = entityId,
                osmType = "NODE",
                name = "Visible Peak",
                type = GeoEntityType.PEAK.name,
                lat = 32.05,
                lon = 35.05,
                elevation = 500.0,
                elevationSource = "OSM_TAG",
                lastSeenAt = now
            )
        )
        database.regionEntityCoverageDao().insert(
            RegionEntityCoverageEntity(
                regionId = regionId.toString(),
                entityId = entityId,
                displayName = "Test Peak"
            )
        )
        database.tileCoverageDao().insert(TileCoverageEntity(regionId = regionId.toString(), tileId = tileId))

        val demFile = demTileRepository.demFile(tileId)
        val elevations = FloatArray(100 * 100) { 100f }
        DemTileWriter(tileLat = 32, tileLon = 35).write(demFile, 100, 100, elevations)
        demTileRepository.registerTile(
            com.nofar.core.model.DemTile(
                tileId = tileId,
                filePath = demTileRepository.demFilePath(tileId),
                width = 100,
                height = 100,
                tileLat = 32,
                tileLon = 35,
                noDataValue = -9999f,
                sizeBytes = demFile.length(),
                refCount = 1,
                lastAccessedAt = Instant.ofEpochMilli(now)
            )
        )

        val result =
            visibilityUseCase.computeForRegion(
                region = sampleRegion(regionId, DownloadStatus.READY, now),
                location =
                UserLocation(
                    latitude = 32.0,
                    longitude = 35.0,
                    altitudeMeters = 100.0,
                    accuracyMeters = 5f,
                    timestampMillis = now
                )
            )

        assertThat(result.entities).isNotEmpty()
        assertThat(result.computationTimeMs).isAtLeast(0L)
    }

    private fun sampleRegion(id: UUID, status: DownloadStatus, nowMillis: Long): com.nofar.core.model.Region {
        val instant = Instant.ofEpochMilli(nowMillis)
        return com.nofar.core.model.Region(
            id = id,
            name = "Test",
            centerLat = 32.0,
            centerLon = 35.0,
            radiusM = 10_000.0,
            minLat = 31.9,
            maxLat = 32.1,
            minLon = 34.9,
            maxLon = 35.1,
            createdAt = instant,
            updatedAt = instant,
            downloadStatus = status,
            downloadProgressPct = if (status == DownloadStatus.READY) 100 else 50,
            osmDatasetVersion = null,
            estimatedSizeBytes = 0,
            entityCount = 1
        )
    }
}
