package com.nofar.core.common.locale

import androidx.core.os.LocaleListCompat
import com.nofar.core.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleApplierTest {
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
    }

    @Test
    fun localeListCompat_forEnglish_usesEnTag() {
        val locales = LocaleListCompat.forLanguageTags("en")
        assertEquals("en", locales[0]?.toLanguageTag())
    }

    @Test
    fun localeListCompat_forHebrew_usesIwTag() {
        val locales = LocaleListCompat.forLanguageTags("iw")
        val tag = locales[0]?.toLanguageTag().orEmpty()
        assertEquals(true, tag == "iw" || tag == "he")
    }
}
