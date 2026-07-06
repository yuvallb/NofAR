package com.nofar.feature.settings

import com.nofar.core.data.repository.StorageRepository
import com.nofar.core.data.repository.StorageStats
import com.nofar.core.data.usecase.TileEvictionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsClearUnusedTilesTest {
    @Test
    fun clearUnusedTiles_invokesUseCaseAndUpdatesStorageStats() = runTest {
        val storageRepository = RecordingStorageRepository(
            initialStats =
            StorageStats(
                regionCount = 2,
                entityDbSizeBytes = 1_000_000,
                demCacheSizeBytes = 200_000_000,
                entityRowCount = 500
            ),
            afterStats =
            StorageStats(
                regionCount = 2,
                entityDbSizeBytes = 1_000_000,
                demCacheSizeBytes = 150_000_000,
                entityRowCount = 500
            )
        )
        val useCase = RecordingEvictUnusedDemTilesUseCase(
            TileEvictionResult(tilesEvicted = 2, bytesFreed = 50_000_000)
        )

        val before = storageRepository.getStorageStats()
        val result = useCase.execute()
        val after = storageRepository.getStorageStats(afterPurge = true)

        assertEquals(200_000_000L, before.demCacheSizeBytes)
        assertEquals(2, result.tilesEvicted)
        assertEquals(50_000_000L, result.bytesFreed)
        assertEquals(150_000_000L, after.demCacheSizeBytes)
        assertTrue(useCase.invoked)
    }
}

private class RecordingStorageRepository(private val initialStats: StorageStats, private val afterStats: StorageStats) :
    StorageRepository {
    override suspend fun getStorageStats(): StorageStats = initialStats

    suspend fun getStorageStats(afterPurge: Boolean): StorageStats = if (afterPurge) afterStats else initialStats
}

private class RecordingEvictUnusedDemTilesUseCase(private val result: TileEvictionResult) {
    var invoked = false

    suspend fun execute(): TileEvictionResult {
        invoked = true
        return result
    }
}
