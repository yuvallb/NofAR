package com.nofar.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nofar.core.data.dem.DemTileWriter
import com.nofar.core.data.usecase.RegionDeletionUseCase
import com.nofar.core.database.NofARDatabase
import com.nofar.core.database.dao.GeoEntityUpserter
import com.nofar.core.database.model.DemTileEntity
import com.nofar.core.database.model.GeoEntityEntity
import com.nofar.core.database.model.RegionEntity
import com.nofar.core.database.model.RegionEntityCoverageEntity
import com.nofar.core.database.model.TileCoverageEntity
import com.nofar.core.database.useBundledSqliteWithRTree
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.RegionBounds
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private fun inMemoryDatabase(context: Context): NofARDatabase =
    Room.inMemoryDatabaseBuilder(context, NofARDatabase::class.java)
        .allowMainThreadQueries()
        .useBundledSqliteWithRTree()
        .build()

@RunWith(AndroidJUnit4::class)
class RegionRepositoryIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: NofARDatabase
    private lateinit var regionRepository: DefaultRegionRepository

    @Before
    fun setUp() {
        database = inMemoryDatabase(context)
        regionRepository = DefaultRegionRepository(database.regionDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createRegion_readBackViaFlow() = runTest {
        val id = UUID.randomUUID()
        val now = java.time.Instant.now()
        val box = RegionBounds.boundingBox(32.0, 35.0, 10_000.0)
        val region =
            com.nofar.core.model.Region(
                id = id,
                name = "Test Region",
                centerLat = 32.0,
                centerLon = 35.0,
                radiusM = 10_000.0,
                minLat = box.minLat,
                maxLat = box.maxLat,
                minLon = box.minLon,
                maxLon = box.maxLon,
                createdAt = now,
                updatedAt = now,
                downloadStatus = DownloadStatus.READY,
                downloadProgressPct = 100,
                osmDatasetVersion = null,
                estimatedSizeBytes = 0,
                entityCount = 0
            )
        regionRepository.createRegion(region)
        val regions = regionRepository.observeAllRegions().first()
        assertThat(regions.single().id).isEqualTo(id)
    }
}

@RunWith(AndroidJUnit4::class)
class RegionDeletionUseCaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: NofARDatabase
    private lateinit var demTileRepository: DefaultDemTileRepository
    private lateinit var useCase: RegionDeletionUseCase

    @Before
    fun setUp() {
        database = inMemoryDatabase(context)
        demTileRepository = DefaultDemTileRepository(context, database.demTileDao())
        useCase =
            RegionDeletionUseCase(
                regionDao = database.regionDao(),
                regionEntityCoverageDao = database.regionEntityCoverageDao(),
                geoEntityDao = database.geoEntityDao(),
                tileCoverageDao = database.tileCoverageDao(),
                demTileRepository = demTileRepository
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deleteRegion_evictsDemTileWhenRefCountZero() = runTest {
        val regionId = UUID.randomUUID()
        val tileId = "Copernicus_DSM_COG_10_N32_00_E035_00_DEM"
        database.regionDao().upsert(
            RegionEntity(
                id = regionId.toString(),
                name = "Delete Me",
                centerLat = 32.0,
                centerLon = 35.0,
                radiusM = 10_000.0,
                minLat = 31.9,
                maxLat = 32.1,
                minLon = 34.9,
                maxLon = 35.1,
                createdAt = 0,
                updatedAt = 0,
                downloadStatus = DownloadStatus.READY.name,
                downloadProgressPct = 100,
                osmDatasetVersion = null,
                estimatedSizeBytes = 0,
                entityCount = 0
            )
        )
        GeoEntityUpserter(database.geoEntityDao()).upsert(
            GeoEntityEntity(
                id = "node/1",
                osmType = "NODE",
                name = "Peak",
                type = "PEAK",
                lat = 32.0,
                lon = 35.0,
                elevation = 100.0,
                elevationSource = "OSM_TAG",
                lastSeenAt = 0
            )
        )
        database.regionEntityCoverageDao().insert(RegionEntityCoverageEntity(regionId.toString(), "node/1"))
        database.demTileDao().upsert(
            DemTileEntity(
                tileId = tileId,
                filePath = demTileRepository.demFilePath(tileId),
                width = 4,
                height = 4,
                tileLat = 32,
                tileLon = 35,
                noDataValue = -9999f,
                sizeBytes = 100,
                refCount = 1,
                lastAccessedAt = 0
            )
        )
        database.tileCoverageDao().insert(TileCoverageEntity(regionId.toString(), tileId))

        val demFile = demTileRepository.demFile(tileId)
        DemTileWriter(tileLat = 32, tileLon = 35).write(
            demFile,
            width = 4,
            height = 4,
            elevations = FloatArray(16) { 100f }
        )
        assertThat(demFile.exists()).isTrue()

        useCase.execute(regionId)

        assertThat(demFile.exists()).isFalse()
        assertThat(database.demTileDao().getById(tileId)).isNull()
        assertThat(database.geoEntityDao().getByOsmId("node/1")).isNull()
    }
}
