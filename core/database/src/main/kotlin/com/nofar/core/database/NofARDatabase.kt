package com.nofar.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nofar.core.database.dao.CoverageCellDao
import com.nofar.core.database.dao.CoverageEntityDao
import com.nofar.core.database.dao.CoverageIngestDao
import com.nofar.core.database.dao.CoverageSetDao
import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.database.dao.GeoEntityDao
import com.nofar.core.database.dao.GeoEntitySpatialDao
import com.nofar.core.database.model.CoverageCellEntity
import com.nofar.core.database.model.CoverageEntityEntity
import com.nofar.core.database.model.CoverageSetEntity
import com.nofar.core.database.model.DemTileEntity
import com.nofar.core.database.model.GeoEntityEntity

@Database(
    entities = [
        CoverageSetEntity::class,
        GeoEntityEntity::class,
        CoverageEntityEntity::class,
        DemTileEntity::class,
        CoverageCellEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class NofARDatabase : RoomDatabase() {
    abstract fun coverageSetDao(): CoverageSetDao

    abstract fun geoEntityDao(): GeoEntityDao

    abstract fun geoEntitySpatialDao(): GeoEntitySpatialDao

    abstract fun coverageEntityDao(): CoverageEntityDao

    abstract fun demTileDao(): DemTileDao

    abstract fun coverageCellDao(): CoverageCellDao

    abstract fun coverageIngestDao(): CoverageIngestDao

    companion object {
        const val DATABASE_NAME = "nofar.db"
    }
}
