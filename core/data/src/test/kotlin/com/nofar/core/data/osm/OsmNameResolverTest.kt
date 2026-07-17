package com.nofar.core.data.osm

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.LabelLanguage
import org.junit.Test

class OsmNameResolverTest {
    @Test
    fun resolveDisplayName_default_usesName() {
        val tags = mapOf("name" to "Mount Hermon", "name:he" to "הר חרמון")
        assertThat(OsmNameResolver.resolveDisplayName(tags, LabelLanguage.DEFAULT))
            .isEqualTo("Mount Hermon")
    }

    @Test
    fun resolveDisplayName_hebrew_prefersLocalizedTag() {
        val tags = mapOf("name" to "Mount Hermon", "name:he" to "הר חרמון")
        assertThat(OsmNameResolver.resolveDisplayName(tags, LabelLanguage.HEBREW))
            .isEqualTo("הר חרמון")
    }

    @Test
    fun resolveDisplayName_hebrew_fallsBackToName() {
        val tags = mapOf("name" to "Mount Hermon")
        assertThat(OsmNameResolver.resolveDisplayName(tags, LabelLanguage.HEBREW))
            .isEqualTo("Mount Hermon")
    }

    @Test
    fun resolveDisplayName_arabic_andEnglish() {
        val tags =
            mapOf(
                "name" to "Jerusalem",
                "name:ar" to "القدس",
                "name:en" to "Jerusalem"
            )
        assertThat(OsmNameResolver.resolveDisplayName(tags, LabelLanguage.ARABIC))
            .isEqualTo("القدس")
        assertThat(OsmNameResolver.resolveDisplayName(tags, LabelLanguage.ENGLISH))
            .isEqualTo("Jerusalem")
    }

    @Test
    fun resolveDisplayName_russian_fallsBackWhenMissing() {
        val tags = mapOf("name" to "Peak", "name:he" to "פסגה")
        assertThat(OsmNameResolver.resolveDisplayName(tags, LabelLanguage.RUSSIAN))
            .isEqualTo("Peak")
    }

    @Test
    fun resolveDisplayName_localizedOnly_withoutDefaultName() {
        val tags = mapOf("name:he" to "פסגה")
        assertThat(OsmNameResolver.resolveDisplayName(tags, LabelLanguage.HEBREW))
            .isEqualTo("פסגה")
        assertThat(OsmNameResolver.resolveDisplayName(tags, LabelLanguage.DEFAULT)).isNull()
    }

    @Test
    fun resolveDisplayName_nonDefault_usesIntNameBeforeDefaultName() {
        val tags = mapOf("name" to "הר תבור", "int_name" to "Mount Tabor")
        assertThat(OsmNameResolver.resolveDisplayName(tags, LabelLanguage.ENGLISH))
            .isEqualTo("Mount Tabor")
        assertThat(OsmNameResolver.resolveDisplayName(tags, LabelLanguage.DEFAULT))
            .isEqualTo("הר תבור")
    }

    @Test
    fun resolveCanonicalName_readsNameTag() {
        val tags = mapOf("name" to "Haifa", "name:he" to "חיפה")
        assertThat(OsmNameResolver.resolveCanonicalName(tags)).isEqualTo("Haifa")
    }
}
