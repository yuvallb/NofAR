package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.ElevationSource
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.GeoEntityType
import com.nofar.core.model.OsmType
import java.time.Instant
import org.junit.Test

class HereContextResolverTest {
    private val observerLat = 32.0
    private val observerLon = 35.0

    @Test
    fun placeInsideFootprint_isSelected() {
        val city =
            samplePlace(
                id = "city",
                name = "City",
                lat = observerLat,
                lon = observerLon,
                type = GeoEntityType.CITY,
                footprintRadiusM = 5_000.0
            )

        val context = HereContextResolver.resolve(observerLat, observerLon, listOf(city))

        assertThat(context.place).isEqualTo(city)
        assertThat(context.peak).isNull()
        assertThat(context.entityIds).containsExactly("city")
    }

    @Test
    fun placeOutsideFootprint_isNotSelected() {
        val city =
            samplePlace(
                id = "city",
                lat = observerLat + 0.1,
                lon = observerLon,
                footprintRadiusM = 500.0
            )

        assertThat(HereContextResolver.resolve(observerLat, observerLon, listOf(city)).place).isNull()
    }

    @Test
    fun nestedPlaces_prefersSmallestContainingFootprint() {
        val city =
            samplePlace(
                id = "city",
                name = "Metro",
                lat = observerLat,
                lon = observerLon,
                type = GeoEntityType.CITY,
                footprintRadiusM = 8_000.0
            )
        val hamlet =
            samplePlace(
                id = "hamlet",
                name = "Hamlet",
                lat = observerLat,
                lon = observerLon,
                type = GeoEntityType.HAMLET,
                footprintRadiusM = 800.0
            )

        val context = HereContextResolver.resolve(observerLat, observerLon, listOf(city, hamlet))

        assertThat(context.place?.id).isEqualTo("hamlet")
    }

    @Test
    fun sameFootprintSize_prefersMoreSpecificPlaceType() {
        val city =
            samplePlace(
                id = "city",
                name = "City",
                lat = observerLat,
                lon = observerLon,
                type = GeoEntityType.CITY,
                footprintRadiusM = 1_000.0
            )
        val locality =
            samplePlace(
                id = "locality",
                name = "Locality",
                lat = observerLat,
                lon = observerLon,
                type = GeoEntityType.LOCALITY,
                footprintRadiusM = 1_000.0
            )

        val context = HereContextResolver.resolve(observerLat, observerLon, listOf(city, locality))

        assertThat(context.place?.id).isEqualTo("locality")
    }

    @Test
    fun placeWithoutFootprint_usesMinimumRadius() {
        val near =
            samplePlace(
                id = "near",
                lat = observerLat,
                lon = observerLon + 0.0005,
                footprintRadiusM = null
            )
        val far =
            samplePlace(
                id = "far",
                lat = observerLat,
                lon = observerLon + 0.01,
                footprintRadiusM = null
            )

        val context = HereContextResolver.resolve(observerLat, observerLon, listOf(near, far))

        assertThat(context.place?.id).isEqualTo("near")
    }

    @Test
    fun peakWithinRadius_isSelected() {
        val peak =
            samplePeak(
                id = "peak",
                lat = observerLat,
                lon = observerLon + 0.0008
            )

        val context = HereContextResolver.resolve(observerLat, observerLon, listOf(peak))

        assertThat(context.peak).isEqualTo(peak)
    }

    @Test
    fun peakBeyondRadius_isNotSelected() {
        val peak =
            samplePeak(
                id = "peak",
                lat = observerLat,
                lon = observerLon + 0.05
            )

        assertThat(HereContextResolver.resolve(observerLat, observerLon, listOf(peak)).peak).isNull()
    }

    @Test
    fun placeAndPeak_canBothBeSelected() {
        val place =
            samplePlace(
                id = "place",
                lat = observerLat,
                lon = observerLon,
                footprintRadiusM = 2_000.0
            )
        val peak =
            samplePeak(
                id = "peak",
                lat = observerLat,
                lon = observerLon
            )

        val context = HereContextResolver.resolve(observerLat, observerLon, listOf(place, peak))

        assertThat(context.place?.id).isEqualTo("place")
        assertThat(context.peak?.id).isEqualTo("peak")
        assertThat(context.entityIds).containsExactly("place", "peak")
    }

    @Test
    fun excludingHereContext_removesMatchingVisibleEntities() {
        val place = samplePlace(id = "place", lat = observerLat, lon = observerLon, footprintRadiusM = 1_000.0)
        val peak = samplePeak(id = "peak", lat = observerLat, lon = observerLon)
        val other = samplePeak(id = "other", lat = observerLat + 1, lon = observerLon)
        val here = HereContext(place = place, peak = peak)
        val visible =
            listOf(
                visibleEntity(other),
                visibleEntity(place),
                visibleEntity(peak)
            )

        val filtered = visible.excludingHereContext(here)

        assertThat(filtered.map { it.entity.id }).containsExactly("other")
    }

    private fun visibleEntity(entity: GeoEntity): VisibleEntity = VisibleEntity(
        bearingDeg = 0.0,
        distanceM = 100.0,
        elevationAngleDeg = 1.0,
        entity = entity
    )

    private fun samplePlace(
        id: String,
        name: String = "Place",
        lat: Double,
        lon: Double,
        type: GeoEntityType = GeoEntityType.VILLAGE,
        footprintRadiusM: Double?
    ): GeoEntity = GeoEntity(
        id = id,
        osmType = OsmType.RELATION,
        name = name,
        type = type,
        lat = lat,
        lon = lon,
        elevation = null,
        elevationSource = null,
        lastSeenAt = Instant.EPOCH,
        footprintRadiusM = footprintRadiusM
    )

    private fun samplePeak(id: String, lat: Double, lon: Double): GeoEntity = GeoEntity(
        id = id,
        osmType = OsmType.NODE,
        name = "Peak",
        type = GeoEntityType.PEAK,
        lat = lat,
        lon = lon,
        elevation = 900,
        elevationSource = ElevationSource.OSM_TAG,
        lastSeenAt = Instant.EPOCH,
        footprintRadiusM = null
    )
}
