package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AltitudeSource
import com.nofar.core.model.AppConfig
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import com.nofar.core.model.UserLocation
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DisplayAltitudeResolverTest {
    private val lookup = FakeDemPointElevationSource()
    private val resolver = DisplayAltitudeResolver(lookup)

    @Test
    fun resolve_gpsWithGoodVerticalAccuracy_returnsConfirmedGps() = runTest {
        val location = userLocation(altitudeMeters = 1234.6, verticalAccuracyMeters = 20f)

        val reading = resolver.resolve(location, lastKnownGpsAltitudeM = null, region = null)

        assertThat(reading).isNotNull()
        assertThat(reading!!.meters).isEqualTo(1235)
        assertThat(reading.source).isEqualTo(AltitudeSource.GPS)
        assertThat(reading.isEstimate).isFalse()
        assertThat(reading.accuracyMeters).isEqualTo(20)
        assertThat(reading.accuracyIsVertical).isTrue()
    }

    @Test
    fun resolve_gpsWithPoorVerticalAccuracy_returnsEstimateGps() = runTest {
        val location =
            userLocation(
                altitudeMeters = 1200.0,
                verticalAccuracyMeters = AppConfig.GPS_ALTITUDE_ACCURACY_THRESHOLD_METERS + 1f
            )

        val reading = resolver.resolve(location, lastKnownGpsAltitudeM = null, region = null)

        assertThat(reading).isNotNull()
        assertThat(reading!!.isEstimate).isTrue()
        assertThat(reading.source).isEqualTo(AltitudeSource.GPS)
    }

    @Test
    fun resolve_gpsWithoutVerticalAccuracy_returnsEstimateWithHorizontalAccuracy() = runTest {
        val location = userLocation(altitudeMeters = 900.0, verticalAccuracyMeters = null, accuracyMeters = 8f)

        val reading = resolver.resolve(location, lastKnownGpsAltitudeM = null, region = null)

        assertThat(reading).isNotNull()
        assertThat(reading!!.isEstimate).isTrue()
        assertThat(reading.accuracyMeters).isEqualTo(8)
        assertThat(reading.accuracyIsVertical).isFalse()
    }

    @Test
    fun resolve_noGpsAltitude_usesStickyLastKnownGps() = runTest {
        val location = userLocation(altitudeMeters = null, accuracyMeters = 12f)

        val reading = resolver.resolve(location, lastKnownGpsAltitudeM = 1100.8, region = null)

        assertThat(reading).isNotNull()
        assertThat(reading!!.meters).isEqualTo(1101)
        assertThat(reading.source).isEqualTo(AltitudeSource.LAST_KNOWN_GPS)
        assertThat(reading.isEstimate).isTrue()
        assertThat(reading.accuracyMeters).isEqualTo(12)
    }

    @Test
    fun resolve_noGpsAltitude_fallsBackToDem() = runTest {
        lookup.nextElevationM = 1180.4f
        val location = userLocation(altitudeMeters = null, accuracyMeters = 6f)

        val reading = resolver.resolve(location, lastKnownGpsAltitudeM = null, region = testRegion())

        assertThat(reading).isNotNull()
        assertThat(reading!!.meters).isEqualTo(1180)
        assertThat(reading.source).isEqualTo(AltitudeSource.DEM)
        assertThat(reading.isEstimate).isTrue()
        assertThat(reading.accuracyMeters).isEqualTo(6)
    }

    @Test
    fun resolve_noSources_returnsNull() = runTest {
        lookup.nextElevationM = null
        val location = userLocation(altitudeMeters = null)

        val reading = resolver.resolve(location, lastKnownGpsAltitudeM = null, region = testRegion())

        assertThat(reading).isNull()
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

        override suspend fun elevationAt(lat: Double, lon: Double, region: Region?): Float? = nextElevationM
    }
}

private fun testRegion(): Region {
    val box = RegionBounds.boundingBox(32.5, 35.5, 10_000.0)
    return Region(
        id = UUID.randomUUID(),
        name = "Test",
        centerLat = 32.5,
        centerLon = 35.5,
        radiusM = 10_000.0,
        minLat = box.minLat,
        maxLat = box.maxLat,
        minLon = box.minLon,
        maxLon = box.maxLon,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        downloadStatus = DownloadStatus.READY,
        downloadProgressPct = 100,
        osmDatasetVersion = Instant.EPOCH,
        estimatedSizeBytes = 1L,
        entityCount = 1
    )
}
