package com.nofar.core.database.di

import android.content.Context
import androidx.room.Room
import com.nofar.core.database.GeoEntitySpatialQuery
import com.nofar.core.database.NofARDatabase
import com.nofar.core.database.NofARDatabaseMigrations
import com.nofar.core.database.dao.CoverageCellDao
import com.nofar.core.database.dao.CoverageEntityDao
import com.nofar.core.database.dao.CoverageIngestDao
import com.nofar.core.database.dao.CoverageLinker
import com.nofar.core.database.dao.CoverageSetDao
import com.nofar.core.database.dao.DemTileDao
import com.nofar.core.database.dao.GeoEntityDao
import com.nofar.core.database.dao.GeoEntitySpatialDao
import com.nofar.core.database.dao.GeoEntityUpserter
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
        // No fallbackToDestructiveMigration — ship explicit migrations from baseline v1 only.
        .addMigrations(*NofARDatabaseMigrations.ALL)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @Provides
    fun provideCoverageSetDao(database: NofARDatabase): CoverageSetDao = database.coverageSetDao()

    @Provides
    fun provideGeoEntityDao(database: NofARDatabase): GeoEntityDao = database.geoEntityDao()

    @Provides
    fun provideGeoEntitySpatialDao(database: NofARDatabase): GeoEntitySpatialDao = database.geoEntitySpatialDao()

    @Provides
    fun provideCoverageEntityDao(database: NofARDatabase): CoverageEntityDao = database.coverageEntityDao()

    @Provides
    fun provideDemTileDao(database: NofARDatabase): DemTileDao = database.demTileDao()

    @Provides
    fun provideCoverageCellDao(database: NofARDatabase): CoverageCellDao = database.coverageCellDao()

    @Provides
    fun provideCoverageIngestDao(database: NofARDatabase): CoverageIngestDao = database.coverageIngestDao()

    @Provides
    @Singleton
    fun provideCoverageLinker(
        coverageEntityDao: CoverageEntityDao,
        coverageCellDao: CoverageCellDao,
        coverageIngestDao: CoverageIngestDao
    ): CoverageLinker = CoverageLinker(coverageEntityDao, coverageCellDao, coverageIngestDao)

    @Provides
    @Singleton
    fun provideGeoEntityUpserter(geoEntityDao: GeoEntityDao): GeoEntityUpserter = GeoEntityUpserter(geoEntityDao)

    @Provides
    @Singleton
    fun provideGeoEntitySpatialQuery(
        geoEntitySpatialDao: GeoEntitySpatialDao,
        geoEntityDao: GeoEntityDao,
        coverageEntityDao: CoverageEntityDao
    ): GeoEntitySpatialQuery = GeoEntitySpatialQuery(geoEntitySpatialDao, geoEntityDao, coverageEntityDao)
}
