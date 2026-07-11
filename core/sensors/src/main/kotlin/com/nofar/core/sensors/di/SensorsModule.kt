package com.nofar.core.sensors.di

import com.nofar.core.sensors.AndroidDeclinationProvider
import com.nofar.core.sensors.AndroidDisplayRotationReader
import com.nofar.core.sensors.DeclinationCorrector
import com.nofar.core.sensors.DeclinationProvider
import com.nofar.core.sensors.DisplayRotationReader
import com.nofar.core.sensors.OrientationProvider
import com.nofar.core.sensors.RotationVectorOrientationProvider
import com.nofar.core.sensors.SmoothedOrientationProvider
import com.nofar.core.sensors.TrueNorthOrientationProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SensorsModule {
    @Provides
    @Singleton
    fun provideDeclinationProvider(impl: AndroidDeclinationProvider): DeclinationProvider = impl

    @Provides
    @Singleton
    fun provideDisplayRotationReader(impl: AndroidDisplayRotationReader): DisplayRotationReader = impl

    @Provides
    @Singleton
    @UnsmoothedOrientation
    fun provideUnsmoothedOrientationProvider(
        rawProvider: RotationVectorOrientationProvider,
        declinationCorrector: DeclinationCorrector
    ): OrientationProvider = TrueNorthOrientationProvider(rawProvider, declinationCorrector)

    @Provides
    @Singleton
    fun provideOrientationProvider(@UnsmoothedOrientation trueNorthProvider: OrientationProvider): OrientationProvider =
        SmoothedOrientationProvider(trueNorthProvider)
}
