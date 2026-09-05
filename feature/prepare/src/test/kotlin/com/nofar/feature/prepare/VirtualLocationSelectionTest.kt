package com.nofar.feature.prepare

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import java.time.Instant
import java.util.UUID
import org.junit.Test

class VirtualLocationSelectionTest {
    private val readyRegion =
        Region(
            id = UUID.randomUUID(),
            name = "Ready",
            centerLat = 32.0,
            centerLon = 35.0,
            radiusM = 12_000.0,
            minLat = 31.9,
            maxLat = 32.1,
            minLon = 34.9,
            maxLon = 35.1,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
            downloadStatus = DownloadStatus.READY,
            downloadProgressPct = 100,
            osmDatasetVersion = null,
            estimatedSizeBytes = 1L,
            entityCount = 1
        )

    private val partialRegion =
        readyRegion.copy(
            id = UUID.randomUUID(),
            name = "Partial",
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            downloadStatus = DownloadStatus.PARTIAL
        )

    @Test
    fun resolveSelection_equalRadiusRegions_prefersReadyThenNewest() {
        val selection =
            VirtualLocationSelectionLogic.resolveSelection(
                regions = listOf(partialRegion, readyRegion),
                lat = readyRegion.centerLat,
                lon = readyRegion.centerLon
            )
        assertThat(selection?.primaryRegionId).isEqualTo(readyRegion.id)
    }

    @Test
    fun resolveSelection_overlappingRegions_prefersLargestReadyRegion() {
        val smallerNewer =
            readyRegion.copy(
                id = UUID.randomUUID(),
                radiusM = 10_000.0,
                updatedAt = Instant.parse("2026-01-03T00:00:00Z")
            )
        val largerOlder =
            readyRegion.copy(
                id = UUID.randomUUID(),
                radiusM = 20_000.0,
                updatedAt = Instant.parse("2026-01-01T00:00:00Z")
            )

        val selection =
            VirtualLocationSelectionLogic.resolveSelection(
                regions = listOf(smallerNewer, largerOlder),
                lat = readyRegion.centerLat,
                lon = readyRegion.centerLon
            )

        assertThat(selection?.primaryRegionId).isEqualTo(largerOlder.id)
        assertThat(selection?.containingRegionIds).containsExactly(smallerNewer.id, largerOlder.id)
    }

    @Test
    fun resolveSelection_outsideRegions_returnsNull() {
        val selection =
            VirtualLocationSelectionLogic.resolveSelection(
                regions = listOf(readyRegion),
                lat = 0.0,
                lon = 0.0
            )
        assertThat(selection).isNull()
    }

    @Test
    fun resolveSelection_includesContributingRegionsWithin300Km() {
        val (neighborLat, neighborLon) =
            destinationM(
                lat = readyRegion.centerLat,
                lon = readyRegion.centerLon,
                bearingDeg = 90.0,
                distanceM = 80_000.0
            )
        val neighbor =
            readyRegion.copy(
                id = UUID.randomUUID(),
                name = "Neighbor",
                centerLat = neighborLat,
                centerLon = neighborLon
            )

        val selection =
            VirtualLocationSelectionLogic.resolveSelection(
                regions = listOf(readyRegion, neighbor),
                lat = readyRegion.centerLat,
                lon = readyRegion.centerLon
            )

        assertThat(selection?.contributingRegionIds).containsExactly(readyRegion.id, neighbor.id)
    }

    @Test
    fun resolveSelection_invalidCoordinate_returnsNull() {
        assertThat(
            VirtualLocationSelectionLogic.resolveSelection(listOf(readyRegion), lat = 91.0, lon = 0.0)
        ).isNull()
    }

    private fun destinationM(lat: Double, lon: Double, bearingDeg: Double, distanceM: Double): Pair<Double, Double> {
        val angularDistance = distanceM / AppConfig.EARTH_RADIUS_METERS
        val bearing = Math.toRadians(bearingDeg)
        val phi1 = Math.toRadians(lat)
        val lambda1 = Math.toRadians(lon)
        val phi2 =
            kotlin.math.asin(
                kotlin.math.sin(phi1) * kotlin.math.cos(angularDistance) +
                    kotlin.math.cos(phi1) * kotlin.math.sin(angularDistance) * kotlin.math.cos(bearing)
            )
        val lambda2 =
            lambda1 +
                kotlin.math.atan2(
                    kotlin.math.sin(bearing) * kotlin.math.sin(angularDistance) * kotlin.math.cos(phi1),
                    kotlin.math.cos(angularDistance) - kotlin.math.sin(phi1) * kotlin.math.sin(phi2)
                )
        return Math.toDegrees(phi2) to Math.toDegrees(lambda2)
    }
}
