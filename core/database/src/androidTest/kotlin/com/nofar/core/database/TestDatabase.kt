package com.nofar.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.database.dao.GeoEntityDao
import com.nofar.core.database.dao.GeoEntityUpserter
import com.nofar.core.database.dao.RegionDao
import com.nofar.core.database.dao.RegionEntityCoverageDao
import com.nofar.core.database.dao.TileCoverageDao

object TestDatabase {
    fun inMemory(context: Context = ApplicationProvider.getApplicationContext()): NofARDatabase =
        Room.inMemoryDatabaseBuilder(context, NofARDatabase::class.java)
            .allowMainThreadQueries()
            .useBundledSqliteWithRTree()
            .build()
}

class DatabaseTestFixtures(val database: NofARDatabase) {
    val regionDao: RegionDao = database.regionDao()
    val geoEntityDao: GeoEntityDao = database.geoEntityDao()
    val coverageDao: RegionEntityCoverageDao = database.regionEntityCoverageDao()
    val demTileDao: DemTileDao = database.demTileDao()
    val tileCoverageDao: TileCoverageDao = database.tileCoverageDao()
    val spatialQuery: GeoEntitySpatialQuery =
        GeoEntitySpatialQuery(database.geoEntitySpatialDao(), geoEntityDao, coverageDao)
    val geoEntitySpatialDao = database.geoEntitySpatialDao()
    val geoEntityUpserter: GeoEntityUpserter = GeoEntityUpserter(geoEntityDao)
}
