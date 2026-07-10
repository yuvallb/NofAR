package com.nofar.core.common.locale

import androidx.core.os.LocaleListCompat
import com.nofar.core.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleApplierTest {
    @Test
    fun fromLocaleList_empty_returnsSystem() {
        assertEquals(AppLanguage.SYSTEM, LocaleApplier.fromLocaleList(LocaleListCompat.getEmptyLocaleList()))
    }

    @Test
    fun fromLocaleList_english_returnsEnglish() {
        val locales = LocaleListCompat.forLanguageTags("en")
        assertEquals(AppLanguage.ENGLISH, LocaleApplier.fromLocaleList(locales))
    }

    @Test
    fun fromLocaleList_hebrew_acceptsIwAndHeTags() {
        assertEquals(
            AppLanguage.HEBREW,
            LocaleApplier.fromLocaleList(LocaleListCompat.forLanguageTags("iw"))
        )
        assertEquals(
            AppLanguage.HEBREW,
            LocaleApplier.fromLocaleList(LocaleListCompat.forLanguageTags("he"))
        )
    }

    @Test
    fun storageValues_mapToExpectedLocaleTags() {
        assertEquals("system", AppLanguage.SYSTEM.storageValue)
        assertEquals("en", AppLanguage.ENGLISH.storageValue)
        assertEquals("iw", AppLanguage.HEBREW.storageValue)
    }

    @Test
    fun fromStorageValue_roundTripsKnownLanguages() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStorageValue(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStorageValue("system"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromStorageValue("en"))
        assertEquals(AppLanguage.HEBREW, AppLanguage.fromStorageValue("iw"))
        assertEquals(AppLanguage.HEBREW, AppLanguage.fromStorageValue("he"))
    }
}
