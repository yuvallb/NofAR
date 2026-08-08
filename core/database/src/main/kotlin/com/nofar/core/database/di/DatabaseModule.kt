package com.nofar.core.database.di

import android.content.Context
import androidx.room.Room
import com.nofar.core.database.GeoEntitySpatialQuery
import com.nofar.core.database.NofARDatabase
import com.nofar.core.database.NofARDatabaseMigrations
import com.nofar.core.database.dao.CoverageLinker
import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.database.dao.GeoEntityDao
import com.nofar.core.database.dao.GeoEntitySpatialDao
import com.nofar.core.database.dao.GeoEntityUpserter
import com.nofar.core.database.dao.RegionDao
import com.nofar.core.database.dao.RegionEntityCoverageDao
import com.nofar.core.database.dao.RegionIngestDao
import com.nofar.core.database.dao.TileCoverageDao
import com.nofar.core.database.useBundledSqliteWithRTree
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NofARDatabase = Room.databaseBuilder(
        context,
        NofARDatabase::class.java,
        NofARDatabase.DATABASE_NAME
    )
        .useBundledSqliteWithRTree()
        .addMigrations(
            NofARDatabaseMigrations.MIGRATION_1_2,
            NofARDatabaseMigrations.MIGRATION_2_3,
            NofARDatabaseMigrations.MIGRATION_3_4
        )
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @Provides
    fun provideRegionDao(database: NofARDatabase): RegionDao = database.regionDao()

    @Provides
    fun provideGeoEntityDao(database: NofARDatabase): GeoEntityDao = database.geoEntityDao()

    @Provides
    fun provideGeoEntitySpatialDao(database: NofARDatabase): GeoEntitySpatialDao = database.geoEntitySpatialDao()

    @Provides
    fun provideRegionEntityCoverageDao(database: NofARDatabase): RegionEntityCoverageDao =
        database.regionEntityCoverageDao()

    @Provides
    fun provideDemTileDao(database: NofARDatabase): DemTileDao = database.demTileDao()

    @Provides
    fun provideTileCoverageDao(database: NofARDatabase): TileCoverageDao = database.tileCoverageDao()

    @Provides
    fun provideRegionIngestDao(database: NofARDatabase): RegionIngestDao = database.regionIngestDao()

    @Provides
    @Singleton
    fun provideCoverageLinker(
        regionEntityCoverageDao: RegionEntityCoverageDao,
        tileCoverageDao: TileCoverageDao,
        regionIngestDao: RegionIngestDao
    ): CoverageLinker = CoverageLinker(regionEntityCoverageDao, tileCoverageDao, regionIngestDao)

    @Provides
    @Singleton
    fun provideGeoEntityUpserter(geoEntityDao: GeoEntityDao): GeoEntityUpserter = GeoEntityUpserter(geoEntityDao)

    @Provides
    @Singleton
    fun provideGeoEntitySpatialQuery(
        geoEntitySpatialDao: GeoEntitySpatialDao,
        geoEntityDao: GeoEntityDao,
        regionEntityCoverageDao: RegionEntityCoverageDao
    ): GeoEntitySpatialQuery = GeoEntitySpatialQuery(geoEntitySpatialDao, geoEntityDao, regionEntityCoverageDao)
}
