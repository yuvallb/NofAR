package com.nofar.core.network

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.RegionBounds
import org.junit.Test

class OverpassQueryBuilderTest {
    @Test
    fun boundingBoxFromCircle_includesPadding() {
        val bbox = OverpassQueryBuilder.boundingBoxFromCircle(32.0, 35.0, 10_000.0)
        val base = RegionBounds.boundingBox(32.0, 35.0, 10_000.0)
        assertThat(bbox.minLat).isLessThan(base.minLat)
        assertThat(bbox.maxLat).isGreaterThan(base.maxLat)
        assertThat(bbox.minLon).isLessThan(base.minLon)
        assertThat(bbox.maxLon).isGreaterThan(base.maxLon)
    }

    @Test
    fun buildQuery_containsPlaceAndPeakSelectors() {
        val bbox = RegionBounds.boundingBox(32.0, 35.0, 10_000.0)
        val query = OverpassQueryBuilder.buildQuery(bbox)
        assertThat(query).contains("node[\"place\"~")
        assertThat(query).contains("node[\"natural\"=\"peak\"]")
        assertThat(query).contains("way[\"place\"~")
        assertThat(query).contains("relation[\"natural\"=\"peak\"]")
        assertThat(query).contains("out center;")
        assertThat(query).contains("${bbox.minLat},${bbox.minLon},${bbox.maxLat},${bbox.maxLon}")
    }
}
