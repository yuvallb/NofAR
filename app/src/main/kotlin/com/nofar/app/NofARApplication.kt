package com.nofar.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nofar.core.common.locale.LocaleApplier
import com.nofar.core.data.preferences.UserPreferencesRepository
import com.nofar.core.model.AppLanguage
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class NofARApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate() {
        super.onCreate()
        migrateLegacyAppLanguageIfNeeded()
    }

    private fun migrateLegacyAppLanguageIfNeeded() {
        if (!AppCompatDelegate.getApplicationLocales().isEmpty) return
        runBlocking {
            val legacy = userPreferencesRepository.consumeLegacyAppLanguage() ?: return@runBlocking
            if (legacy != AppLanguage.SYSTEM) {
                LocaleApplier.apply(legacy)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
