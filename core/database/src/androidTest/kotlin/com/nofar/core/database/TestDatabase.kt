package com.nofar.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nofar.core.database.dao.CoverageCellDao
import com.nofar.core.database.dao.CoverageEntityDao
import com.nofar.core.database.dao.CoverageSetDao
import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.database.dao.GeoEntityDao
import com.nofar.core.database.dao.GeoEntityUpserter

object TestDatabase {
    fun inMemory(context: Context = ApplicationProvider.getApplicationContext()): NofARDatabase =
        Room.inMemoryDatabaseBuilder(context, NofARDatabase::class.java)
            .allowMainThreadQueries()
            .useBundledSqliteWithRTree()
            .build()
}

class DatabaseTestFixtures(val database: NofARDatabase) {
    val coverageSetDao: CoverageSetDao = database.coverageSetDao()
    val geoEntityDao: GeoEntityDao = database.geoEntityDao()
    val coverageDao: CoverageEntityDao = database.coverageEntityDao()
    val demTileDao: DemTileDao = database.demTileDao()
    val coverageCellDao: CoverageCellDao = database.coverageCellDao()
    val spatialQuery: GeoEntitySpatialQuery =
        GeoEntitySpatialQuery(database.geoEntitySpatialDao(), geoEntityDao, coverageDao)
    val geoEntitySpatialDao = database.geoEntitySpatialDao()
    val geoEntityUpserter: GeoEntityUpserter = GeoEntityUpserter(geoEntityDao)
}
