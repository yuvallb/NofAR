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
                demTileDao = database.demTileDao()
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
    }
}
