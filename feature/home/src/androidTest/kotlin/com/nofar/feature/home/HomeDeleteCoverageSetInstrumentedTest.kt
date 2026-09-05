package com.nofar.feature.home

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nofar.core.data.dem.DemTileWriter
import com.nofar.core.data.repository.DefaultCoverageSetRepository
import com.nofar.core.data.repository.DefaultDemTileRepository
import com.nofar.core.data.usecase.CoverageSetDeletionUseCase
import com.nofar.core.database.NofARDatabase
import com.nofar.core.database.dao.GeoEntityUpserter
import com.nofar.core.database.model.CoverageCellEntity
import com.nofar.core.database.model.CoverageEntityEntity
import com.nofar.core.database.model.CoverageSetEntity
import com.nofar.core.database.model.DemTileEntity
import com.nofar.core.database.model.GeoEntityEntity
import com.nofar.core.database.useBundledSqliteWithRTree
import com.nofar.core.model.CellMembership
import com.nofar.core.model.DownloadStatus
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeDeleteCoverageSetInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: NofARDatabase
    private lateinit var coverageSetRepository: DefaultCoverageSetRepository
    private lateinit var demTileRepository: DefaultDemTileRepository
    private lateinit var deletionUseCase: CoverageSetDeletionUseCase

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(context, NofARDatabase::class.java)
                .allowMainThreadQueries()
                .useBundledSqliteWithRTree()
                .build()
        coverageSetRepository =
            DefaultCoverageSetRepository(database.coverageSetDao(), database.coverageCellDao())
        demTileRepository = DefaultDemTileRepository(context, database.demTileDao())
        deletionUseCase =
            CoverageSetDeletionUseCase(
                coverageSetRepository = coverageSetRepository,
                coverageEntityDao = database.coverageEntityDao(),
                geoEntityDao = database.geoEntityDao(),
                coverageCellDao = database.coverageCellDao(),
                demTileRepository = demTileRepository
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deleteCoverageSet_removesFromListAndRunsGarbageCollection() = runTest {
        val coverageSetId = UUID.randomUUID()
        val tileId = "Copernicus_DSM_COG_30_N32_00_E035_00_DEM"
        database.coverageSetDao().upsert(sampleCoverageSet(coverageSetId))
        GeoEntityUpserter(database.geoEntityDao()).upsert(
            GeoEntityEntity(
                id = "node/42",
                osmType = "NODE",
                name = "Peak",
                type = "PEAK",
                lat = 32.0,
                lon = 35.0,
                elevation = 100,
                elevationSource = "OSM_TAG",
                lastSeenAt = 0
            )
        )
        database.coverageEntityDao().insert(
            CoverageEntityEntity(coverageSetId.toString(), "node/42", displayName = "Peak")
        )
        database.demTileDao().upsert(
            DemTileEntity(
                tileId = tileId,
                filePath = demTileRepository.demFilePath(tileId),
                width = 4,
                height = 4,
                tileLat = 32,
                tileLon = 35,
                noDataValue = -32768f,
                sizeBytes = 100,
                refCount = 1,
                lastAccessedAt = 0
            )
        )
        database.coverageCellDao().insert(CoverageCellEntity(coverageSetId.toString(), tileId))
        val demFile = demTileRepository.demFile(tileId)
        DemTileWriter(tileLat = 32, tileLon = 35).write(
            demFile,
            width = 4,
            height = 4,
            elevations = FloatArray(16) { 100f }
        )

        assertThat(coverageSetRepository.observeAllCoverageSets().first()).hasSize(1)

        deletionUseCase.execute(coverageSetId)

        assertThat(coverageSetRepository.observeAllCoverageSets().first()).isEmpty()
        assertThat(database.geoEntityDao().getByOsmId("node/42")).isNull()
        assertThat(database.demTileDao().getById(tileId)).isNull()
        assertThat(demFile.exists()).isFalse()
    }

    @Test
    fun deleteCoverageSet_keepsSharedDemTileWhenRefCountRemains() = runTest {
        val coverageSetA = UUID.randomUUID()
        val coverageSetB = UUID.randomUUID()
        val tileId = "Copernicus_DSM_COG_30_N32_00_E035_00_DEM"
        listOf(coverageSetA, coverageSetB).forEach { id ->
            database.coverageSetDao().upsert(sampleCoverageSet(id, name = "Shared $id"))
            database.coverageCellDao().insert(CoverageCellEntity(id.toString(), tileId))
        }
        database.demTileDao().upsert(
            DemTileEntity(
                tileId = tileId,
                filePath = demTileRepository.demFilePath(tileId),
                width = 4,
                height = 4,
                tileLat = 32,
                tileLon = 35,
                noDataValue = -32768f,
                sizeBytes = 100,
                refCount = 2,
                lastAccessedAt = 0
            )
        )
        val demFile = demTileRepository.demFile(tileId)
        DemTileWriter(tileLat = 32, tileLon = 35).write(
            demFile,
            width = 4,
            height = 4,
            elevations = FloatArray(16) { 100f }
        )

        deletionUseCase.execute(coverageSetA)

        assertThat(database.demTileDao().getById(tileId)?.refCount).isEqualTo(1)
        assertThat(demFile.exists()).isTrue()
        assertThat(coverageSetRepository.observeAllCoverageSets().first()).hasSize(1)
    }

    @Test
    fun coverageSetsContainingPoint_usesCellMembership() = runTest {
        val coverageSetId = UUID.randomUUID()
        val tileId = CellMembership.cellIdForPoint(32.5, 35.5)
        database.coverageSetDao().upsert(sampleCoverageSet(coverageSetId))
        database.coverageCellDao().insert(CoverageCellEntity(coverageSetId.toString(), tileId))

        val inside = coverageSetRepository.coverageSetsContainingPoint(32.5, 35.5)
        assertThat(inside).hasSize(1)

        val outside = coverageSetRepository.coverageSetsContainingPoint(40.0, 10.0)
        assertThat(outside).isEmpty()
    }

    private fun sampleCoverageSet(id: UUID, name: String = "Home Delete"): CoverageSetEntity = CoverageSetEntity(
        id = id.toString(),
        name = name,
        createdAt = 0,
        updatedAt = 0,
        downloadStatus = DownloadStatus.READY.name,
        downloadProgressPct = 100,
        osmDatasetVersion = null,
        estimatedSizeBytes = 0,
        entityCount = 1
    )
}
