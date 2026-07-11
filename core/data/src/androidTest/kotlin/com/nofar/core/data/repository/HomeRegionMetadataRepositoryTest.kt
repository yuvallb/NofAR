package com.nofar.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nofar.core.database.NofARDatabase
import com.nofar.core.database.model.DemTileEntity
import com.nofar.core.database.model.TileCoverageEntity
import com.nofar.core.database.useBundledSqliteWithRTree
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeRegionMetadataRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: NofARDatabase
    private lateinit var repository: HomeRegionMetadataRepository

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(context, NofARDatabase::class.java)
                .allowMainThreadQueries()
                .useBundledSqliteWithRTree()
                .build()
        repository =
            HomeRegionMetadataRepository(
                tileCoverageDao = database.tileCoverageDao(),
                demTileDao = database.demTileDao(),
                regionEntityCoverageDao = database.regionEntityCoverageDao()
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun getMetadata_sumsDemSizesAndPicksLatestTimestamp() = runTest {
        val regionId = UUID.randomUUID()
        val older = Instant.parse("2024-01-01T00:00:00Z")
        val newer = Instant.parse("2025-06-01T00:00:00Z")
        database.tileCoverageDao().insertAll(
            listOf(
                TileCoverageEntity(regionId.toString(), "tile-a"),
                TileCoverageEntity(regionId.toString(), "tile-b")
            )
        )
        database.demTileDao().upsert(
            DemTileEntity(
                tileId = "tile-a",
                filePath = "/tmp/tile-a",
                width = 4,
                height = 4,
                tileLat = 32,
                tileLon = 35,
                noDataValue = -9999f,
                sizeBytes = 100,
                refCount = 1,
                lastAccessedAt = older.toEpochMilli()
            )
        )
        database.demTileDao().upsert(
            DemTileEntity(
                tileId = "tile-b",
                filePath = "/tmp/tile-b",
                width = 4,
                height = 4,
                tileLat = 32,
                tileLon = 36,
                noDataValue = -9999f,
                sizeBytes = 250,
                refCount = 1,
                lastAccessedAt = newer.toEpochMilli()
            )
        )

        val metadata = repository.getMetadata(regionId)

        assertThat(metadata.demSizeBytes).isEqualTo(350L)
        assertThat(metadata.latestDemTimestamp).isEqualTo(newer)
        assertThat(metadata.liveEntityCount).isEqualTo(0)
    }

    @Test
    fun getMetadata_withoutTileCoverage_fallsBackToIntersectingDemTiles() = runTest {
        val regionId = UUID.randomUUID()
        val timestamp = Instant.parse("2025-06-01T00:00:00Z")
        database.demTileDao().upsert(
            DemTileEntity(
                tileId = "Copernicus_DSM_COG_10_N32_00_E035_00_DEM",
                filePath = "/tmp/tile-a",
                width = 4,
                height = 4,
                tileLat = 32,
                tileLon = 35,
                noDataValue = -9999f,
                sizeBytes = 180,
                refCount = 1,
                lastAccessedAt = timestamp.toEpochMilli()
            )
        )
        val region =
            com.nofar.core.model.Region(
                id = regionId,
                name = "Fallback",
                centerLat = 32.5,
                centerLon = 35.5,
                radiusM = 10_000.0,
                minLat = 32.4,
                maxLat = 32.6,
                minLon = 35.4,
                maxLon = 35.6,
                createdAt = timestamp,
                updatedAt = timestamp,
                downloadStatus = com.nofar.core.model.DownloadStatus.READY,
                downloadProgressPct = 100,
                osmDatasetVersion = timestamp,
                estimatedSizeBytes = 0,
                entityCount = 0
            )

        val metadata = repository.getMetadata(regionId, region)

        assertThat(metadata.demSizeBytes).isEqualTo(180L)
        assertThat(metadata.latestDemTimestamp).isEqualTo(timestamp)
    }
}
