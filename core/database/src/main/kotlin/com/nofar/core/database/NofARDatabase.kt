package com.nofar.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.database.dao.GeoEntityDao
import com.nofar.core.database.dao.RegionDao
import com.nofar.core.database.dao.RegionEntityCoverageDao
import com.nofar.core.database.dao.TileCoverageDao
import com.nofar.core.database.model.DemTileEntity
import com.nofar.core.database.model.GeoEntityEntity
import com.nofar.core.database.model.RegionEntity
import com.nofar.core.database.model.RegionEntityCoverageEntity
import com.nofar.core.database.model.TileCoverageEntity

@Database(
    entities = [
        RegionEntity::class,
        GeoEntityEntity::class,
        RegionEntityCoverageEntity::class,
        DemTileEntity::class,
        TileCoverageEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class NofARDatabase : RoomDatabase() {
    abstract fun regionDao(): RegionDao

    abstract fun geoEntityDao(): GeoEntityDao

    abstract fun regionEntityCoverageDao(): RegionEntityCoverageDao

    abstract fun demTileDao(): DemTileDao

    abstract fun tileCoverageDao(): TileCoverageDao

    companion object {
        const val DATABASE_NAME = "nofar.db"
    }
}
