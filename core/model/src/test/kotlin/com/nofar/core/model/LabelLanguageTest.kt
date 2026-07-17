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
}
