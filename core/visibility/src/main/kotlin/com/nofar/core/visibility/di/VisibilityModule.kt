package com.nofar.core.visibility.di

import com.nofar.core.common.DefaultDispatchers
import com.nofar.core.common.DispatcherProvider
import com.nofar.core.visibility.DemRaycastVisibilityEngine
import com.nofar.core.visibility.ObserverElevationResolver
import com.nofar.core.visibility.RegionVisibilityComputer
import com.nofar.core.visibility.VisibilityEngine
import com.nofar.core.visibility.VisibilityUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VisibilityModule {
    @Binds
    @Singleton
    abstract fun bindVisibilityEngine(impl: DemRaycastVisibilityEngine): VisibilityEngine

    @Binds
    @Singleton
    abstract fun bindRegionVisibilityComputer(impl: VisibilityUseCase): RegionVisibilityComputer

    companion object {
        @Provides
        @Singleton
        fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatchers

        @Provides
        @Singleton
        fun provideObserverElevationResolver(): ObserverElevationResolver = ObserverElevationResolver()
    }
}
