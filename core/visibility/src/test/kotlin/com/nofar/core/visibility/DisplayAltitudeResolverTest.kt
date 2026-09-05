package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AltitudeSource
import com.nofar.core.model.CellMembership
import com.nofar.core.model.UserLocation
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DisplayAltitudeResolverTest {
    private val lookup = FakeDemPointElevationSource()
    private val resolver = DisplayAltitudeResolver(lookup)

    @Before
    fun resetLookup() {
        lookup.nextElevationM = null
    }

    @Test
    fun resolve_gpsWithGoodVerticalAccuracy_returnsConfirmedGps() = runTest {
        val location = userLocation(altitudeMeters = 1234.6, verticalAccuracyMeters = 20f)

        val reading = resolver.resolve(location, lastKnownGpsAltitudeM = null, cellIds = emptySet())

        assertThat(reading).isNotNull()
        assertThat(reading!!.meters).isEqualTo(1235)
        assertThat(reading.source).isEqualTo(AltitudeSource.GPS)
        assertThat(reading.isEstimate).isFalse()
        assertThat(reading.accuracyMeters).isEqualTo(5)
        assertThat(reading.accuracyIsVertical).isTrue()
        assertThat(reading.demMeters).isNull()
    }

    @Test
    fun resolve_gpsWithPoorVerticalAccuracy_stillReturnsGps() = runTest {
        val location = userLocation(altitudeMeters = 1200.0, verticalAccuracyMeters = 100f)

        val reading = resolver.resolve(location, lastKnownGpsAltitudeM = null, cellIds = emptySet())

        assertThat(reading).isNotNull()
        assertThat(reading!!.isEstimate).isFalse()
        assertThat(reading.source).isEqualTo(AltitudeSource.GPS)
    }

    @Test
    fun resolve_gpsWithoutVerticalAccuracy_usesHorizontalAccuracy() = runTest {
        val location = userLocation(altitudeMeters = 900.0, verticalAccuracyMeters = null, accuracyMeters = 8f)

        val reading = resolver.resolve(location, lastKnownGpsAltitudeM = null, cellIds = emptySet())

        assertThat(reading).isNotNull()
        assertThat(reading!!.isEstimate).isFalse()
        assertThat(reading.accuracyMeters).isEqualTo(8)
        assertThat(reading.accuracyIsVertical).isFalse()
    }

    @Test
    fun resolve_noGpsAltitude_usesStickyLastKnownGps() = runTest {
        val location = userLocation(altitudeMeters = null, accuracyMeters = 12f)

        val reading = resolver.resolve(location, lastKnownGpsAltitudeM = 1100.8, cellIds = emptySet())

        assertThat(reading).isNotNull()
        assertThat(reading!!.meters).isEqualTo(1101)
        assertThat(reading.source).isEqualTo(AltitudeSource.LAST_KNOWN_GPS)
        assertThat(reading.isEstimate).isFalse()
        assertThat(reading.accuracyMeters).isEqualTo(12)
        assertThat(reading.demMeters).isNull()
    }

    @Test
    fun resolve_noGpsAltitude_fallsBackToDem() = runTest {
        lookup.nextElevationM = 1180.4f
        val location = userLocation(altitudeMeters = null, accuracyMeters = 6f)

        val reading = resolver.resolve(location, lastKnownGpsAltitudeM = null, cellIds = testCellIds())

        assertThat(reading).isNotNull()
        assertThat(reading!!.meters).isEqualTo(1180)
        assertThat(reading.source).isEqualTo(AltitudeSource.DEM)
        assertThat(reading.isEstimate).isFalse()
        assertThat(reading.accuracyMeters).isNull()
    }

    @Test
    fun resolve_noSources_returnsNull() = runTest {
        lookup.nextElevationM = null
        val location = userLocation(altitudeMeters = null)

        val reading = resolver.resolve(location, lastKnownGpsAltitudeM = null, cellIds = testCellIds())

        assertThat(reading).isNull()
    }

    @Test
    fun resolve_virtual_returnsDemWithoutAccuracy() = runTest {
        lookup.nextElevationM = 140.4f
        val location = userLocation(altitudeMeters = null, accuracyMeters = 5f)

        val reading =
            resolver.resolve(
                location,
                lastKnownGpsAltitudeM = 159.0,
                cellIds = testCellIds(),
                isVirtual = true
            )

        assertThat(reading).isNotNull()
        assertThat(reading!!.meters).isEqualTo(140)
        assertThat(reading.source).isEqualTo(AltitudeSource.DEM)
        assertThat(reading.accuracyMeters).isNull()
    }

    @Test
    fun resolve_virtual_demMissing_returnsNull() = runTest {
        val location = userLocation(altitudeMeters = 200.0)

        val reading =
            resolver.resolve(
                location,
                lastKnownGpsAltitudeM = 200.0,
                cellIds = testCellIds(),
                isVirtual = true
            )

        assertThat(reading).isNull()
    }

    @Test
    fun resolve_gpsAndDemAgree_omitsDem() = runTest {
        lookup.nextElevationM = 157f
        val location = userLocation(altitudeMeters = 159.0, verticalAccuracyMeters = 3f)

        val reading = resolver.resolve(location, lastKnownGpsAltitudeM = null, cellIds = testCellIds())

        assertThat(reading).isNotNull()
        assertThat(reading!!.meters).isEqualTo(159)
        assertThat(reading.source).isEqualTo(AltitudeSource.GPS)
        assertThat(reading.demMeters).isNull()
    }

    @Test
    fun resolve_gpsAndDemDifferByThreshold_attachesDem() = runTest {
        lookup.nextElevationM = 130f
        val location = userLocation(altitudeMeters = 159.0, verticalAccuracyMeters = 3f)

        val reading = resolver.resolve(location, lastKnownGpsAltitudeM = null, cellIds = testCellIds())

        assertThat(reading).isNotNull()
        assertThat(reading!!.demMeters).isEqualTo(130)
    }

    @Test
    fun resolve_gpsWithoutDem_omitsDem() = runTest {
        val location = userLocation(altitudeMeters = 159.0, verticalAccuracyMeters = 3f)

        val reading = resolver.resolve(location, lastKnownGpsAltitudeM = null, cellIds = emptySet())

        assertThat(reading).isNotNull()
        assertThat(reading!!.meters).isEqualTo(159)
        assertThat(reading.source).isEqualTo(AltitudeSource.GPS)
        assertThat(reading.demMeters).isNull()
    }

    private fun userLocation(
        altitudeMeters: Double?,
        accuracyMeters: Float = 5f,
        verticalAccuracyMeters: Float? = 10f
    ): UserLocation = UserLocation(
        latitude = 32.5,
        longitude = 35.5,
        altitudeMeters = altitudeMeters,
        accuracyMeters = accuracyMeters,
        verticalAccuracyMeters = verticalAccuracyMeters,
        timestampMillis = 1L
    )

    private class FakeDemPointElevationSource : DemPointElevationSource {
        var nextElevationM: Float? = null

        override suspend fun elevationAt(lat: Double, lon: Double, cellIds: Set<String>): Float? = nextElevationM
    }
}

private fun testCellIds(): Set<String> = setOf(CellMembership.cellIdForPoint(32.5, 35.5))
