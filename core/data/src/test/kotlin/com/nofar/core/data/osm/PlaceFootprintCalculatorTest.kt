package com.nofar.core.data.osm

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import org.junit.Test

class PlaceFootprintCalculatorTest {
    @Test
    fun computeRadiusM_ringOfPoints_returnsExpectedRadius() {
        val centerLat = 32.0
        val centerLon = 35.0
        val radiusDeg = 0.01
        val points =
            (0 until 8).map { index ->
                val angle = index * Math.PI / 4
                val lat = centerLat + radiusDeg * kotlin.math.sin(angle)
                val lon = centerLon + radiusDeg * kotlin.math.cos(angle)
                lat to lon
            }

        val radiusM = PlaceFootprintCalculator.computeRadiusM(points)

        assertThat(radiusM).isNotNull()
        val expectedMeters = radiusDeg * Math.PI / 180.0 * AppConfig.EARTH_RADIUS_METERS
        assertThat(radiusM!!).isWithin(500.0).of(expectedMeters * AppConfig.FOOTPRINT_RADIUS_GYRATION_FACTOR)
    }

    @Test
    fun computeRadiusM_emptyPoints_returnsNull() {
        assertThat(PlaceFootprintCalculator.computeRadiusM(emptyList())).isNull()
    }

    @Test
    fun computeRadiusM_singlePoint_clampsToMinimum() {
        val radiusM = PlaceFootprintCalculator.computeRadiusM(listOf(32.0 to 35.0))
        assertThat(radiusM).isEqualTo(AppConfig.FOOTPRINT_RADIUS_MIN_M)
    }
}
