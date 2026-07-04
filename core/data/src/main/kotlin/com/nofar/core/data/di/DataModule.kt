package com.nofar.core.data.di

import com.nofar.core.data.repository.DefaultDemTileRepository
import com.nofar.core.data.repository.DefaultGeoEntityRepository
import com.nofar.core.data.repository.DefaultRegionRepository
import com.nofar.core.data.repository.DefaultStorageRepository
import com.nofar.core.data.repository.DemTileRepository
import com.nofar.core.data.repository.GeoEntityRepository
import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.data.repository.StorageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindRegionRepository(impl: DefaultRegionRepository): RegionRepository

    @Binds
    @Singleton
    abstract fun bindGeoEntityRepository(impl: DefaultGeoEntityRepository): GeoEntityRepository

    @Binds
    @Singleton
    abstract fun bindDemTileRepository(impl: DefaultDemTileRepository): DemTileRepository

    @Binds
    @Singleton
    abstract fun bindStorageRepository(impl: DefaultStorageRepository): StorageRepository
}
