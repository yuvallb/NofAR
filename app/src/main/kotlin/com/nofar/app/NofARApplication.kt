package com.nofar.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class NofARApplication :
    Application(),
    Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() {
            val workerFactory =
                EntryPointAccessors.fromApplication(
                    applicationContext,
                    WorkManagerEntryPoint::class.java
                ).workerFactory()
            return Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
        }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkManagerEntryPoint {
        fun workerFactory(): HiltWorkerFactory
    }
}
