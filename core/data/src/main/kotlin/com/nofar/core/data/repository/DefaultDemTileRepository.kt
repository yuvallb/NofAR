package com.nofar.core.data.repository

import android.content.Context
import android.util.Log
import com.nofar.core.data.dem.DemBinaryFormat
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

    override fun isBinReadable(tileId: String): Boolean {
        val file = demFile(tileId)
        return file.exists() && file.length() > DemBinaryFormat.HEADER_SIZE_BYTES
    }

    override suspend fun ensureRegisteredFromBin(tileId: String): Boolean {
        val binFile = demFile(tileId)
        return when {
            !isBinReadable(tileId) -> false
            getTile(tileId) != null -> true
            else ->
                runCatching {
                    DemTileReader.open(binFile).use { reader ->
                        registerTile(
                            DemTile(
                                tileId = tileId,
                                filePath = demFilePath(tileId),
                                width = reader.width,
                                height = reader.height,
                                tileLat = reader.tileLat,
                                tileLon = reader.tileLon,
                                noDataValue = reader.noDataValue,
                                sizeBytes = binFile.length(),
                                refCount = 0,
                                lastAccessedAt = java.time.Instant.now()
                            )
                        )
                    }
                }.onFailure { error ->
                    Log.w(TAG, "Failed to register DEM tile $tileId from ${binFile.absolutePath}", error)
                }.isSuccess
        }
    }

    override fun openReader(tileId: String): DemTileReader? {
        val file = demFile(tileId)
        if (!file.exists()) {
            Log.w(TAG, "DEM bin missing for tile $tileId at ${file.absolutePath}")
            return null
        }
        return runCatching { DemTileReader.open(file) }.getOrElse { error ->
            Log.w(TAG, "Failed to open DEM tile $tileId (${file.length()} bytes)", error)
            null
        }
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

    companion object {
        private const val TAG = "DefaultDemTileRepository"
    }
}
