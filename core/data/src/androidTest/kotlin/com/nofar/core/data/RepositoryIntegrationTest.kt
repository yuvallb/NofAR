package com.nofar.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.nofar.core.data.dem.DemTileWriter
import com.nofar.core.data.usecase.CoverageSetDeletionUseCase
import com.nofar.core.database.NofARDatabase
import com.nofar.core.database.dao.GeoEntityUpserter
import com.nofar.core.database.model.CoverageCellEntity
import com.nofar.core.database.model.CoverageEntityEntity
import com.nofar.core.database.model.CoverageSetEntity
import com.nofar.core.database.model.DemTileEntity
import com.nofar.core.database.model.GeoEntityEntity
import com.nofar.core.database.useBundledSqliteWithRTree
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
import java.time.Instant
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
class CoverageSetRepositoryIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: NofARDatabase
    private lateinit var coverageSetRepository: DefaultCoverageSetRepository

    @Before
    fun setUp() {
        database = inMemoryDatabase(context)
        coverageSetRepository =
            DefaultCoverageSetRepository(database.coverageSetDao(), database.coverageCellDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createCoverageSet_readBackViaFlow() = runTest {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val coverageSet =
            CoverageSet(
                id = id,
                name = "Test Coverage",
                createdAt = now,
                updatedAt = now,
                downloadStatus = DownloadStatus.READY,
                downloadProgressPct = 100,
                osmDatasetVersion = null,
                estimatedSizeBytes = 0,
                entityCount = 0
            )
        coverageSetRepository.createCoverageSet(coverageSet)
        val sets = coverageSetRepository.observeAllCoverageSets().first()
        assertThat(sets.single().id).isEqualTo(id)
    }
}

@RunWith(AndroidJUnit4::class)
class CoverageSetDeletionUseCaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: NofARDatabase
    private lateinit var demTileRepository: DefaultDemTileRepository
    private lateinit var useCase: CoverageSetDeletionUseCase

    @Before
    fun setUp() {
        database = inMemoryDatabase(context)
        demTileRepository = DefaultDemTileRepository(context, database.demTileDao())
        useCase =
            CoverageSetDeletionUseCase(
                coverageSetRepository =
                DefaultCoverageSetRepository(
                    database.coverageSetDao(),
                    database.coverageCellDao()
                ),
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
    fun deleteCoverageSet_evictsDemTileWhenRefCountZero() = runTest {
        val coverageSetId = UUID.randomUUID()
        val tileId = "Copernicus_DSM_COG_30_N32_00_E035_00_DEM"
        database.coverageSetDao().upsert(
            CoverageSetEntity(
                id = coverageSetId.toString(),
                name = "Delete Me",
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
                elevation = 100,
                elevationSource = "OSM_TAG",
                lastSeenAt = 0
            )
        )
        database.coverageEntityDao().insert(
            CoverageEntityEntity(coverageSetId.toString(), "node/1", displayName = "Entity")
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
        assertThat(demFile.exists()).isTrue()

        useCase.execute(coverageSetId)

        assertThat(demFile.exists()).isFalse()
        assertThat(database.demTileDao().getById(tileId)).isNull()
        assertThat(database.geoEntityDao().getByOsmId("node/1")).isNull()
    }
}
