package com.nofar.core.network.di

import com.nofar.core.network.DefaultDemTileFetcher
import com.nofar.core.network.DefaultOverpassApi
import com.nofar.core.network.DemTileFetcher
import com.nofar.core.network.OverpassApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Do not hardcode CertificatePinner pins here: SPKI pins rotate and would break
        // production downloads. Prefer platform TLS + app network_security_config (cleartext
        // blocked). Add CertificatePinner only after documenting current pins for Overpass
        // mirrors and the Copernicus DEM S3 endpoint.
        .build()

    @Provides
    @Singleton
    fun provideOverpassApi(client: OkHttpClient): OverpassApi = DefaultOverpassApi(client)

    @Provides
    @Singleton
    fun provideDemTileFetcher(client: OkHttpClient): DemTileFetcher = DefaultDemTileFetcher(client)
}
