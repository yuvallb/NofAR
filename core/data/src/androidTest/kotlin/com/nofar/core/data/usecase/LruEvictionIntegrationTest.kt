package com.nofar.core.data.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nofar.core.data.repository.DefaultCoverageSetRepository
import com.nofar.core.data.repository.DefaultDemTileRepository
import com.nofar.core.database.NofARDatabase
import com.nofar.core.database.model.CoverageCellEntity
import com.nofar.core.database.model.CoverageSetEntity
import com.nofar.core.database.model.DemTileEntity
import com.nofar.core.model.DownloadStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LruEvictionIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: NofARDatabase
    private lateinit var demTileRepository: DefaultDemTileRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room.inMemoryDatabaseBuilder(context, NofARDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        demTileRepository = DefaultDemTileRepository(context, database.demTileDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun lruEviction_removesUnusedTilesUntilUnderThreshold() = runTest {
        val now = System.currentTimeMillis()
        database.demTileDao().upsert(
            DemTileEntity(
                tileId = "tile-old",
                filePath = "dem/tile-old.bin",
                width = 100,
                height = 100,
                tileLat = 32,
                tileLon = 35,
                noDataValue = -1f,
                sizeBytes = 80L * 1024 * 1024,
                refCount = 0,
                lastAccessedAt = now - 10_000
            )
        )
        database.demTileDao().upsert(
            DemTileEntity(
                tileId = "tile-new",
                filePath = "dem/tile-new.bin",
                width = 100,
                height = 100,
                tileLat = 33,
                tileLon = 36,
                noDataValue = -1f,
                sizeBytes = 60L * 1024 * 1024,
                refCount = 0,
                lastAccessedAt = now
            )
        )

        val useCase = LruEvictionUseCase(demTileRepository)
        val thresholdBytes = 100L * 1024 * 1024
        val result = useCase.execute(thresholdBytes)

        assertThat(result.tilesEvicted).isEqualTo(1)
        assertThat(result.bytesFreed).isEqualTo(80L * 1024 * 1024)
        assertThat(demTileRepository.totalCacheSizeBytes()).isAtMost(thresholdBytes)
        assertThat(demTileRepository.getTile("tile-old")).isNull()
        assertThat(demTileRepository.getTile("tile-new")).isNotNull()
    }

    @Test
    fun forceLruEviction_removesReferencedTilesAndMarksCoverageSetPartial() = runTest {
        val coverageSetId = java.util.UUID.randomUUID().toString()
        val tileId = "Copernicus_DSM_COG_30_N32_00_E035_00_DEM"
        val now = System.currentTimeMillis()
        database.coverageSetDao().upsert(
            CoverageSetEntity(
                id = coverageSetId,
                name = "Test",
                createdAt = now,
                updatedAt = now,
                downloadStatus = DownloadStatus.READY.name,
                downloadProgressPct = 100,
                osmDatasetVersion = now,
                estimatedSizeBytes = 120L * 1024 * 1024,
                entityCount = 10
            )
        )
        database.demTileDao().upsert(
            DemTileEntity(
                tileId = tileId,
                filePath = "dem/$tileId.bin",
                width = 100,
                height = 100,
                tileLat = 32,
                tileLon = 35,
                noDataValue = -1f,
                sizeBytes = 120L * 1024 * 1024,
                refCount = 1,
                lastAccessedAt = now
            )
        )
        database.coverageCellDao().insert(
            CoverageCellEntity(coverageSetId = coverageSetId, cellId = tileId)
        )

        val coverageSetRepository =
            DefaultCoverageSetRepository(database.coverageSetDao(), database.coverageCellDao())
        val useCase =
            ForceLruEvictionUseCase(
                demTileRepository = demTileRepository,
                coverageCellDao = database.coverageCellDao(),
                coverageSetRepository = coverageSetRepository
            )
        val result = useCase.execute(50L * 1024 * 1024)

        assertThat(result.tilesEvicted).isEqualTo(1)
        assertThat(demTileRepository.getTile(tileId)).isNull()
        // Geometry (coverage_cell) is retained for re-download.
        assertThat(database.coverageCellDao().getCellIdsForCoverageSet(coverageSetId))
            .containsExactly(tileId)
        val coverageSet = coverageSetRepository.getCoverageSet(java.util.UUID.fromString(coverageSetId))
        assertThat(coverageSet?.downloadStatus).isEqualTo(DownloadStatus.PARTIAL)
    }
}
