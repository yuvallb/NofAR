package com.nofar.core.data.osm

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.LabelLanguage
import com.nofar.core.model.OsmType
import org.junit.Test

class OverpassStreamParserTest {
    private val parser = OverpassStreamParser()

    @Test
    fun parse_streamsElementsWithoutLoadingFullDocument() {
        val fixture = javaClass.getResourceAsStream("/overpass_fixture.json")!!
        var peakCount = 0
        val count =
            parser.parse(fixture) { element ->
                if (element.type == GeoEntityType.PEAK) peakCount++
            }
        assertThat(count).isAtLeast(100)
        assertThat(peakCount).isGreaterThan(0)
    }

    @Test
    fun parse_skipsUnnamedEntities() {
        val json =
            """
            {"elements":[
              {"type":"node","id":1,"lat":32.0,"lon":35.0,"tags":{"natural":"peak"}},
              {"type":"node","id":2,"lat":32.1,"lon":35.1,"tags":{"natural":"peak","name":"Mount Test"}}
            ]}
            """.trimIndent()
        val parsed = mutableListOf<ParsedOsmElement>()
        val count = parser.parse(json.byteInputStream(), parsed::add)
        assertThat(count).isEqualTo(1)
        assertThat(parsed.single().name).isEqualTo("Mount Test")
    }

    @Test
    fun parse_hebrew_usesLocalizedNameWithFallback() {
        val json =
            """
            {"elements":[
              {"type":"node","id":1,"lat":32.0,"lon":35.0,"tags":{"natural":"peak","name":"Mount Test","name:he":"הר בדיקה"}},
              {"type":"node","id":2,"lat":32.1,"lon":35.1,"tags":{"natural":"peak","name":"Only English"}},
              {"type":"node","id":3,"lat":32.2,"lon":35.2,"tags":{"natural":"peak","name:he":"רק עברית"}}
            ]}
            """.trimIndent()
        val parsed = mutableListOf<ParsedOsmElement>()
        val count = parser.parse(json.byteInputStream(), LabelLanguage.HEBREW, parsed::add)
        assertThat(count).isEqualTo(3)
        assertThat(parsed[0].name).isEqualTo("הר בדיקה")
        assertThat(parsed[0].canonicalName).isEqualTo("Mount Test")
        assertThat(parsed[1].name).isEqualTo("Only English")
        assertThat(parsed[2].name).isEqualTo("רק עברית")
        assertThat(parsed[2].canonicalName).isEqualTo("רק עברית")
    }

    @Test
    fun parse_english_prefersNameEnOverHebrewPrimaryName() {
        val json =
            """
            {"elements":[
              {"type":"node","id":1,"lat":32.0,"lon":35.0,"tags":{"natural":"peak","name":"הר חרמון","name:he":"הר חרמון","name:en":"Mount Hermon"}}
            ]}
            """.trimIndent()
        val parsed = mutableListOf<ParsedOsmElement>()
        parser.parse(json.byteInputStream(), LabelLanguage.ENGLISH, parsed::add)
        assertThat(parsed.single().name).isEqualTo("Mount Hermon")
        assertThat(parsed.single().canonicalName).isEqualTo("הר חרמון")
        assertThat(parser.toGeoEntity(parsed.single()).name).isEqualTo("Mount Hermon")
    }

    @Test
    fun toGeoEntity_usesDisplayName() {
        val element =
            ParsedOsmElement(
                osmType = OsmType.NODE,
                osmId = 42,
                name = "חיפה",
                canonicalName = "Haifa",
                type = GeoEntityType.CITY,
                lat = 32.8,
                lon = 34.98,
                elevation = 10.0
            )
        val entity = parser.toGeoEntity(element)
        assertThat(entity.id).isEqualTo("node/42")
        assertThat(entity.name).isEqualTo("חיפה")
        assertThat(entity.type).isEqualTo(GeoEntityType.CITY)
    }
}
