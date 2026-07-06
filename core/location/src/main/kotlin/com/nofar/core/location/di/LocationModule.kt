package com.nofar.core.location.di

import com.nofar.core.location.DefaultLocationRepository
import com.nofar.core.location.GpsOnlyLocationProvider
import com.nofar.core.location.LocationProvider
import com.nofar.core.location.LocationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds
    @Singleton
    abstract fun bindLocationProvider(impl: GpsOnlyLocationProvider): LocationProvider

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: DefaultLocationRepository): LocationRepository
}
