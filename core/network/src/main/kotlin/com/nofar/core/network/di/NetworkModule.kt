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
        .build()

    @Provides
    @Singleton
    fun provideOverpassApi(client: OkHttpClient): OverpassApi = DefaultOverpassApi(client)

    @Provides
    @Singleton
    fun provideDemTileFetcher(client: OkHttpClient): DemTileFetcher = DefaultDemTileFetcher(client)
}
