package com.nofar.core.data.prepare

import com.google.common.truth.Truth.assertThat
import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.data.dem.DemTileWriter
import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.data.repository.GeoEntityRepository
import com.nofar.core.model.DemTile
import com.nofar.core.model.DemTileId
import com.nofar.core.model.ElevationSource
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.OsmType
import com.nofar.core.model.ResolutionLevel
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MissingEntityElevationFillerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun fill_samplesDemForEntitiesMissingElevation_andSkipsOsmTagged() = runTest {
        val tileLat = 32
        val tileLon = 35
        val demElevation = 247f
        val bin = writeFlatTile(tileLat, tileLon, demElevation)
        val tileId = DemTileId.fromCoordinates(tileLat, tileLon)

        val withoutEle =
            sampleEntity(
                id = "node/1",
                lat = 32.5,
                lon = 35.5,
                elevation = null,
                elevationSource = null
            )
        val withOsmEle =
            sampleEntity(
                id = "node/2",
                lat = 32.4,
                lon = 35.4,
                elevation = 900,
                elevationSource = ElevationSource.OSM_TAG
            )
        val staleDem =
            sampleEntity(
                id = "node/3",
                lat = 32.3,
                lon = 35.3,
                elevation = 1,
                elevationSource = ElevationSource.DEM_SAMPLE
            )
        val entities =
            mutableMapOf(
                withoutEle.id to withoutEle,
                withOsmEle.id to withOsmEle,
                staleDem.id to staleDem
            )
        val geoRepo = FakeGeoEntityRepository(entities)
        val demRepo = FakeDemTileRepository(mapOf(tileId to bin))
        val filler = MissingEntityElevationFiller(geoRepo, demRepo)

        val result =
            filler.fill(
                listOf(withoutEle.id, withOsmEle.id, staleDem.id),
                refreshDemSamples = true
            )

        assertThat(result.attempted).isEqualTo(2)
        assertThat(result.filled).isEqualTo(2)
        assertThat(result.skippedExisting).isEqualTo(1)
        assertThat(result.failed).isEqualTo(0)

        val filled = entities.getValue(withoutEle.id)
        assertThat(filled.elevation).isEqualTo(demElevation.roundToInt())
        assertThat(filled.elevationSource).isEqualTo(ElevationSource.DEM_SAMPLE)
        assertThat(entities.getValue(withOsmEle.id).elevation).isEqualTo(900)
        assertThat(entities.getValue(withOsmEle.id).elevationSource).isEqualTo(ElevationSource.OSM_TAG)
        assertThat(entities.getValue(staleDem.id).elevation).isEqualTo(demElevation.roundToInt())
        assertThat(entities.getValue(staleDem.id).elevationSource).isEqualTo(ElevationSource.DEM_SAMPLE)
    }

    @Test
    fun fill_withoutRefresh_leavesExistingDemSampleUntouched() = runTest {
        val tileLat = 32
        val tileLon = 35
        val bin = writeFlatTile(tileLat, tileLon, elevationM = 247f)
        val tileId = DemTileId.fromCoordinates(tileLat, tileLon)
        val staleDem =
            sampleEntity(
                id = "node/stale",
                lat = 32.3,
                lon = 35.3,
                elevation = 1,
                elevationSource = ElevationSource.DEM_SAMPLE
            )
        val entities = mutableMapOf(staleDem.id to staleDem)
        val filler =
            MissingEntityElevationFiller(
                FakeGeoEntityRepository(entities),
                FakeDemTileRepository(mapOf(tileId to bin))
            )

        val result = filler.fill(listOf(staleDem.id), refreshDemSamples = false)

        assertThat(result.attempted).isEqualTo(0)
        assertThat(result.skippedExisting).isEqualTo(1)
        assertThat(entities.getValue(staleDem.id).elevation).isEqualTo(1)
    }

    @Test
    fun fill_countsFailureWhenDemTileMissing() = runTest {
        val entity =
            sampleEntity(
                id = "node/3",
                lat = 32.5,
                lon = 35.5,
                elevation = null,
                elevationSource = null
            )
        val entities = mutableMapOf(entity.id to entity)
        val demRepo = FakeDemTileRepository(emptyMap())
        val filler =
            MissingEntityElevationFiller(
                FakeGeoEntityRepository(entities),
                demRepo
            )

        val result = filler.fill(listOf(entity.id))

        assertThat(result.attempted).isEqualTo(1)
        assertThat(result.filled).isEqualTo(0)
        assertThat(result.failed).isEqualTo(1)
        assertThat(entities.getValue(entity.id).elevation).isNull()
        assertThat(demRepo.openAttempts.values.sum()).isEqualTo(1)
    }

    @Test
    fun fill_missingTile_opensReaderOncePerTile() = runTest {
        val first =
            sampleEntity(
                id = "node/a",
                lat = 32.4,
                lon = 35.4,
                elevation = null,
                elevationSource = null
            )
        val second =
            sampleEntity(
                id = "node/b",
                lat = 32.6,
                lon = 35.6,
                elevation = null,
                elevationSource = null
            )
        val entities = mutableMapOf(first.id to first, second.id to second)
        val demRepo = FakeDemTileRepository(emptyMap())
        val filler =
            MissingEntityElevationFiller(
                FakeGeoEntityRepository(entities),
                demRepo
            )

        val result = filler.fill(listOf(first.id, second.id))

        assertThat(result.attempted).isEqualTo(2)
        assertThat(result.failed).isEqualTo(2)
        assertThat(demRepo.openAttempts.values.sum()).isEqualTo(1)
    }

    private fun writeFlatTile(tileLat: Int, tileLon: Int, elevationM: Float): File {
        val width = 32
        val height = 32
        val elevations = FloatArray(width * height) { elevationM }
        val file = tempFolder.newFile("tile_${tileLat}_$tileLon.bin")
        DemTileWriter(tileLat = tileLat, tileLon = tileLon).write(file, width, height, elevations)
        return file
    }

    private fun sampleEntity(
        id: String,
        lat: Double,
        lon: Double,
        elevation: Int?,
        elevationSource: ElevationSource?
    ): GeoEntity = GeoEntity(
        id = id,
        osmType = OsmType.NODE,
        name = "Entity $id",
        type = GeoEntityType.TOWN,
        lat = lat,
        lon = lon,
        elevation = elevation,
        elevationSource = elevationSource,
        lastSeenAt = Instant.parse("2026-01-01T00:00:00Z")
    )
}

private class FakeGeoEntityRepository(private val store: MutableMap<String, GeoEntity>) : GeoEntityRepository {
    override suspend fun getById(id: String): GeoEntity? = store[id]

    override suspend fun upsert(entity: GeoEntity): String {
        store[entity.id] = entity
        return entity.id
    }

    override suspend fun upsertFromStream(entities: Sequence<GeoEntity>) {
        entities.forEach { upsert(it) }
    }

    override suspend fun queryWithinRadius(
        lat: Double,
        lon: Double,
        radiusM: Double,
        resolutionLevel: ResolutionLevel
    ): List<GeoEntity> = store.values.toList()

    override suspend fun queryWithinRadiusForCoverageSet(
        coverageSetId: UUID,
        lat: Double,
        lon: Double,
        radiusM: Double,
        resolutionLevel: ResolutionLevel
    ): List<GeoEntity> = store.values.toList()

    override suspend fun garbageCollectOrphans(): Int = 0
}

private class FakeDemTileRepository(
    private val binsByTileId: Map<String, File>,
    val openAttempts: MutableMap<String, Int> = mutableMapOf()
) : DemTileRepository {
    override suspend fun registerTile(tile: DemTile) = Unit

    override suspend fun getTile(tileId: String): DemTile? = null

    override fun isBinReadable(tileId: String): Boolean = binsByTileId.containsKey(tileId)

    override suspend fun ensureRegisteredFromBin(tileId: String): Boolean = isBinReadable(tileId)

    override fun openReader(tileId: String): DemTileReader? {
        openAttempts[tileId] = (openAttempts[tileId] ?: 0) + 1
        return binsByTileId[tileId]?.let(DemTileReader::open)
    }

    override suspend fun incrementRefCount(tileId: String) = Unit

    override suspend fun decrementRefCount(tileId: String) = Unit

    override suspend fun totalCacheSizeBytes(): Long = 0L

    override suspend fun evictTile(tileId: String): Boolean = false

    override suspend fun getUnusedTiles(): List<DemTile> = emptyList()

    override suspend fun getLruUnusedCandidates(): List<DemTile> = emptyList()

    override suspend fun getAllLruCandidates(): List<DemTile> = emptyList()
}
