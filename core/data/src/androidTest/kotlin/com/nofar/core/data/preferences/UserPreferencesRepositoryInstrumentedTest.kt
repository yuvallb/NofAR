package com.nofar.core.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
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
    fun consumeLegacyAppLanguage_returnsNullWhenUnset() = runTest {
        assertNull(repository.consumeLegacyAppLanguage())
    }

    @Test
    fun consumeLegacyAppLanguage_isIdempotentAfterClear() = runTest {
        repository.consumeLegacyAppLanguage()
        assertNull(repository.consumeLegacyAppLanguage())
    }
}
