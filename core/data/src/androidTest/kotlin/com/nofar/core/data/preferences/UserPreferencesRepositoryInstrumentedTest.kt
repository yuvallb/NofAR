package com.nofar.core.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nofar.core.model.AppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPreferencesRepositoryInstrumentedTest {
    private lateinit var repository: DefaultUserPreferencesRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = DefaultUserPreferencesRepository(context)
    }

    @Test
    fun appLanguage_defaultsToSystem() = runTest {
        assertEquals(AppLanguage.SYSTEM, repository.appLanguage.first())
    }

    @Test
    fun setAppLanguage_persistsEnglish() = runTest {
        repository.setAppLanguage(AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, repository.appLanguage.first())
    }

    @Test
    fun setAppLanguage_persistsHebrew() = runTest {
        repository.setAppLanguage(AppLanguage.HEBREW)
        assertEquals(AppLanguage.HEBREW, repository.appLanguage.first())
    }

    @Test
    fun setAppLanguage_canReturnToSystem() = runTest {
        repository.setAppLanguage(AppLanguage.HEBREW)
        repository.setAppLanguage(AppLanguage.SYSTEM)
        assertEquals(AppLanguage.SYSTEM, repository.appLanguage.first())
    }
}
