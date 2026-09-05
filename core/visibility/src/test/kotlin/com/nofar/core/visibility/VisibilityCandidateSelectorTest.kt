package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.ElevationSource
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.OsmType
import com.nofar.core.model.UserLocation
import java.time.Instant
import org.junit.Test

class VisibilityCandidateSelectorTest {
    private val location =
        UserLocation(
            latitude = 32.0,
            longitude = 35.0,
            altitudeMeters = null,
            accuracyMeters = 5f,
            timestampMillis = 0L
        )

    @Test
    fun underCap_keepsAllSortedByDistance() {
        val entities =
            listOf(
                entity("far-peak", GeoEntityType.PEAK, lat = 32.08, lon = 35.0),
                entity("near-place", GeoEntityType.VILLAGE, lat = 32.01, lon = 35.0),
                entity("mid-peak", GeoEntityType.PEAK, lat = 32.04, lon = 35.0)
            )

        val selected =
            VisibilityCandidateSelector.select(
                entities = entities,
                location = location,
                maxCandidates = 100,
                peakBudget = 70
            )

        assertThat(selected.map { it.id }).containsExactly("near-place", "mid-peak", "far-peak").inOrder()
    }

    @Test
    fun overCap_keepsNearestPeaksThenFillsWithPlaces() {
        val peaks =
            (0 until 5).map { index ->
                entity("peak-$index", GeoEntityType.PEAK, lat = 32.0 + (index + 1) * 0.01, lon = 35.0)
            }
        val places =
            (0 until 5).map { index ->
                entity(
                    "place-$index",
                    GeoEntityType.VILLAGE,
                    lat = 32.0 + (index + 1) * 0.001,
                    lon = 35.0
                )
            }

        val selected =
            VisibilityCandidateSelector.select(
                entities = peaks + places,
                location = location,
                maxCandidates = 4,
                peakBudget = 2
            )

        assertThat(selected).hasSize(4)
        assertThat(selected.take(2).map { it.id }).containsExactly("peak-0", "peak-1").inOrder()
        assertThat(selected.drop(2).map { it.type }).containsExactly(GeoEntityType.VILLAGE, GeoEntityType.VILLAGE)
        assertThat(selected.drop(2).map { it.id }).containsExactly("place-0", "place-1").inOrder()
    }

    @Test
    fun overCap_reservesHighestRemainingPeaks() {
        val nearLowPeaks =
            (0 until 55).map { index ->
                entity(
                    id = "near-$index",
                    type = GeoEntityType.PEAK,
                    lat = 32.0 + (index + 1) * 0.001,
                    lon = 35.0,
                    elevation = 500 + index
                )
            }
        val farHighPeak =
            entity(
                id = "far-high",
                type = GeoEntityType.PEAK,
                lat = 32.8,
                lon = 35.0,
                elevation = 2_800
            )
        val places =
            (0 until 50).map { index ->
                entity("place-$index", GeoEntityType.VILLAGE, lat = 32.001 + index * 0.0001, lon = 35.0)
            }

        val selected =
            VisibilityCandidateSelector.select(
                entities = nearLowPeaks + farHighPeak + places,
                location = location,
                maxCandidates = 100,
                peakBudget = 70,
                nearestPeakBudget = 50,
                longRangePeakBudget = 20
            )

        assertThat(selected.map { it.id }).contains("far-high")
        assertThat(selected.filter { it.type == GeoEntityType.PEAK }.map { it.id }).contains("far-high")
    }

    private fun entity(id: String, type: GeoEntityType, lat: Double, lon: Double, elevation: Int = 100): GeoEntity =
        GeoEntity(
            id = id,
            osmType = OsmType.NODE,
            name = id,
            type = type,
            lat = lat,
            lon = lon,
            elevation = elevation,
            elevationSource = ElevationSource.OSM_TAG,
            lastSeenAt = Instant.EPOCH
        )
}
