package com.nofar.core.data.repository

import android.content.Context
import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.database.model.asEntity
import com.nofar.core.database.model.asExternalModel
import com.nofar.core.model.DemTile
import com.nofar.core.model.DemTileId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject

@Suppress("TooManyFunctions")
class DefaultDemTileRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val demTileDao: DemTileDao
) : DemTileRepository {
    private val demDirectory: File
        get() = File(context.filesDir, "dem").also { it.mkdirs() }

    override suspend fun registerTile(tile: DemTile) {
        demTileDao.upsert(tile.asEntity())
    }

    override suspend fun getTile(tileId: String): DemTile? = demTileDao.getById(tileId)?.asExternalModel()

    override fun openReader(tileId: String): DemTileReader? {
        val file = demFile(tileId)
        if (!file.exists()) return null
        return DemTileReader.open(file)
    }

    override suspend fun incrementRefCount(tileId: String) {
        demTileDao.incrementRefCount(tileId)
        demTileDao.touch(tileId, Instant.now().toEpochMilli())
    }

    override suspend fun decrementRefCount(tileId: String) {
        demTileDao.decrementRefCount(tileId, Instant.now().toEpochMilli())
    }

    override suspend fun totalCacheSizeBytes(): Long = demTileDao.totalCacheSizeBytes()

    override suspend fun evictTile(tileId: String): Boolean {
        val file = demFile(tileId)
        if (file.exists()) {
            file.delete()
        }
        demTileDao.deleteById(tileId)
        return true
    }

    override suspend fun getUnusedTiles(): List<DemTile> = demTileDao.getUnusedTiles().map { it.asExternalModel() }

    override suspend fun getLruUnusedCandidates(): List<DemTile> =
        demTileDao.getLruUnusedCandidates().map { it.asExternalModel() }

    override suspend fun getAllLruCandidates(): List<DemTile> =
        demTileDao.getAllLruCandidates().map { it.asExternalModel() }

    fun demFile(tileId: String): File = File(demDirectory, DemTileId.binFileName(tileId))

    fun demFilePath(tileId: String): String = "dem/${DemTileId.binFileName(tileId)}"
}
