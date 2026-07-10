package com.nofar.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nofar.core.common.locale.LocaleApplier
import com.nofar.core.data.preferences.UserPreferencesRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class NofARApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate() {
        super.onCreate()
        runBlocking {
            LocaleApplier.apply(userPreferencesRepository.appLanguage.first())
        }
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
