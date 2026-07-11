package com.nofar.core.data.usecase

import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.model.DemTile
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EvictUnusedDemTilesUseCaseTest {
    @Test
    fun clearUnusedTiles_evictsOnlyRefCountZeroAndReturnsFreedBytes() = runTest {
        val repository = FakeDemTileRepository(
            tiles =
            listOf(
                tile("tile-a", refCount = 0, sizeBytes = 10_000_000),
                tile("tile-b", refCount = 1, sizeBytes = 20_000_000),
                tile("tile-c", refCount = 0, sizeBytes = 5_000_000)
            )
        )
        val useCase = EvictUnusedDemTilesUseCase(repository)

        val result = useCase.execute()

        assertEquals(2, result.tilesEvicted)
        assertEquals(15_000_000L, result.bytesFreed)
        assertEquals(listOf("tile-b"), repository.remainingTileIds())
    }

    private fun tile(tileId: String, refCount: Int, sizeBytes: Long): DemTile = DemTile(
        tileId = tileId,
        filePath = "dem/$tileId.bin",
        width = 3600,
        height = 3600,
        tileLat = 32,
        tileLon = 35,
        noDataValue = -32768f,
        sizeBytes = sizeBytes,
        refCount = refCount,
        lastAccessedAt = Instant.now()
    )
}

private class FakeDemTileRepository(tiles: List<DemTile>) : DemTileRepository {
    private val store = tiles.associateBy { it.tileId }.toMutableMap()

    override suspend fun registerTile(tile: DemTile) {
        store[tile.tileId] = tile
    }

    override suspend fun getTile(tileId: String): DemTile? = store[tileId]

    override fun isBinReadable(tileId: String): Boolean = store.containsKey(tileId)

    override suspend fun ensureRegisteredFromBin(tileId: String): Boolean = store.containsKey(tileId)

    override fun openReader(tileId: String) = null

    override suspend fun incrementRefCount(tileId: String) = Unit

    override suspend fun decrementRefCount(tileId: String) = Unit

    override suspend fun totalCacheSizeBytes(): Long = store.values.sumOf { it.sizeBytes }

    override suspend fun evictTile(tileId: String): Boolean {
        store.remove(tileId)
        return true
    }

    override suspend fun getUnusedTiles(): List<DemTile> = store.values.filter { it.refCount == 0 }

    override suspend fun getLruUnusedCandidates(): List<DemTile> = getUnusedTiles()

    override suspend fun getAllLruCandidates(): List<DemTile> = store.values.toList()

    fun remainingTileIds(): List<String> = store.keys.sorted()
}
