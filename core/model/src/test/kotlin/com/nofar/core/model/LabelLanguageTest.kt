package com.nofar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabelLanguageTest {
    @Test
    fun fromStoredName_parsesKnownValues() {
        assertEquals(LabelLanguage.HEBREW, LabelLanguage.fromStoredName("HEBREW"))
        assertEquals(LabelLanguage.ARABIC, LabelLanguage.fromStoredName("arabic"))
        assertEquals(LabelLanguage.DEFAULT, LabelLanguage.fromStoredName("unknown"))
    }

    @Test
    fun osmNameTag_matchesStandardKeys() {
        assertNull(LabelLanguage.DEFAULT.osmNameTag)
        assertEquals("name:he", LabelLanguage.HEBREW.osmNameTag)
        assertEquals("name:ar", LabelLanguage.ARABIC.osmNameTag)
        assertEquals("name:en", LabelLanguage.ENGLISH.osmNameTag)
        assertEquals("name:ru", LabelLanguage.RUSSIAN.osmNameTag)
    }

    @Test
    fun fromDeviceLanguageCode_mapsSupportedLanguages() {
        assertEquals(LabelLanguage.HEBREW, LabelLanguage.fromDeviceLanguageCode("he"))
        assertEquals(LabelLanguage.HEBREW, LabelLanguage.fromDeviceLanguageCode("iw"))
        assertEquals(LabelLanguage.HEBREW, LabelLanguage.fromDeviceLanguageCode("HE"))
        assertEquals(LabelLanguage.ARABIC, LabelLanguage.fromDeviceLanguageCode("ar"))
        assertEquals(LabelLanguage.ENGLISH, LabelLanguage.fromDeviceLanguageCode("en"))
        assertEquals(LabelLanguage.RUSSIAN, LabelLanguage.fromDeviceLanguageCode("ru"))
    }

    @Test
    fun fromDeviceLanguageCode_unsupportedFallsBackToDefault() {
        assertEquals(LabelLanguage.DEFAULT, LabelLanguage.fromDeviceLanguageCode("fr"))
        assertEquals(LabelLanguage.DEFAULT, LabelLanguage.fromDeviceLanguageCode("de"))
        assertEquals(LabelLanguage.DEFAULT, LabelLanguage.fromDeviceLanguageCode(""))
    }
}
