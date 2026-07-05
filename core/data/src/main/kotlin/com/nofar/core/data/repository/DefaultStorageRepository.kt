package com.nofar.core.data.repository

import android.content.Context
import com.nofar.core.database.NofARDatabase
import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.database.dao.GeoEntityDao
import com.nofar.core.database.dao.RegionDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DefaultStorageRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val regionDao: RegionDao,
    private val demTileDao: DemTileDao,
    private val geoEntityDao: GeoEntityDao
) : StorageRepository {
    override suspend fun getStorageStats(): StorageStats {
        val dbFile = context.getDatabasePath(NofARDatabase.DATABASE_NAME)
        val entityDbSize = if (dbFile.exists()) dbFile.length() else 0L
        return StorageStats(
            regionCount = regionDao.getAll().size,
            entityDbSizeBytes = entityDbSize,
            demCacheSizeBytes = demTileDao.totalCacheSizeBytes(),
            entityRowCount = geoEntityDao.countAll()
        )
    }
}
