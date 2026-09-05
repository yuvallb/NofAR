package com.nofar.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nofar.core.database.NofARDatabase
import com.nofar.core.database.model.CoverageCellEntity
import com.nofar.core.database.model.DemTileEntity
import com.nofar.core.database.useBundledSqliteWithRTree
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCoverageSetMetadataRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: NofARDatabase
    private lateinit var repository: HomeCoverageSetMetadataRepository

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(context, NofARDatabase::class.java)
                .allowMainThreadQueries()
                .useBundledSqliteWithRTree()
                .build()
        repository =
            HomeCoverageSetMetadataRepository(
                coverageCellDao = database.coverageCellDao(),
                demTileDao = database.demTileDao(),
                coverageEntityDao = database.coverageEntityDao()
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun getMetadata_sumsDemSizesAndPicksLatestTimestamp() = runTest {
        val coverageSetId = UUID.randomUUID()
        val older = Instant.parse("2024-01-01T00:00:00Z")
        val newer = Instant.parse("2025-06-01T00:00:00Z")
        val cellA = "Copernicus_DSM_COG_30_N32_00_E035_00_DEM"
        val cellB = "Copernicus_DSM_COG_30_N32_00_E036_00_DEM"
        database.coverageCellDao().insertAll(
            listOf(
                CoverageCellEntity(coverageSetId.toString(), cellA),
                CoverageCellEntity(coverageSetId.toString(), cellB)
            )
        )
        database.demTileDao().upsert(
            DemTileEntity(
                tileId = cellA,
                filePath = "dem/$cellA.bin",
                width = 4,
                height = 4,
                tileLat = 32,
                tileLon = 35,
                noDataValue = -32768f,
                sizeBytes = 100,
                refCount = 1,
                lastAccessedAt = older.toEpochMilli()
            )
        )
        database.demTileDao().upsert(
            DemTileEntity(
                tileId = cellB,
                filePath = "dem/$cellB.bin",
                width = 4,
                height = 4,
                tileLat = 32,
                tileLon = 36,
                noDataValue = -32768f,
                sizeBytes = 250,
                refCount = 1,
                lastAccessedAt = newer.toEpochMilli()
            )
        )

        val metadata =
            repository.getMetadata(
                coverageSetId = coverageSetId,
                cellIds = listOf(cellA, cellB)
            )

        assertThat(metadata.demSizeBytes).isEqualTo(350L)
        assertThat(metadata.latestDemTimestamp).isEqualTo(newer)
        assertThat(metadata.liveEntityCount).isEqualTo(0)
        assertThat(metadata.tileCount).isEqualTo(2)
    }

    @Test
    fun getMetadata_withoutCells_returnsZeroDemSize() = runTest {
        val coverageSetId = UUID.randomUUID()

        val metadata = repository.getMetadata(coverageSetId, cellIds = emptyList())

        assertThat(metadata.demSizeBytes).isEqualTo(0L)
        assertThat(metadata.latestDemTimestamp).isNull()
        assertThat(metadata.tileCount).isEqualTo(0)
    }
}
