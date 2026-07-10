package com.nofar.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun fromStorageValue_mapsKnownTags() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStorageValue(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStorageValue("system"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromStorageValue("en"))
        assertEquals(AppLanguage.HEBREW, AppLanguage.fromStorageValue("iw"))
    }

    @Test
    fun storageValue_roundTrips() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language, AppLanguage.fromStorageValue(language.storageValue))
        }
    }
}
