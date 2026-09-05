package com.nofar.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.util.UUID
import org.junit.Test

class ContributingRegionsTest {
    @Test
    fun emptyContaining_returnsEmptyContributing() {
        val home = testRegion(name = "Home", centerLat = 32.0, centerLon = 35.0, radiusM = 10_000.0)
        val regions = listOf(home)

        val contributing = ContributingRegions.contributingRegions(regions, 33.0, 36.0)

        assertThat(contributing).isEmpty()
    }

    @Test
    fun containingOnly_includesSelf() {
        val home = testRegion(name = "Home", centerLat = 32.0, centerLon = 35.0, radiusM = 10_000.0)
        val regions = listOf(home)

        val contributing = ContributingRegions.contributingRegions(regions, 32.0, 35.0)

        assertThat(contributing).containsExactly(home)
    }

    @Test
    fun neighborWithin300Km_isIncluded() {
        val home = testRegion(name = "Home", centerLat = 32.0, centerLon = 35.0, radiusM = 10_000.0)
        val (neighborLat, neighborLon) =
            destinationM(home.centerLat, home.centerLon, bearingDeg = 90.0, distanceM = 80_000.0)
        val neighbor =
            testRegion(name = "Neighbor", centerLat = neighborLat, centerLon = neighborLon, radiusM = 10_000.0)
        val regions = listOf(home, neighbor)

        val contributing = ContributingRegions.contributingRegions(regions, home.centerLat, home.centerLon)

        assertThat(contributing).containsExactly(home, neighbor)
    }

    @Test
    fun regionBeyond300Km_isExcluded() {
        val home = testRegion(name = "Home", centerLat = 32.0, centerLon = 35.0, radiusM = 10_000.0)
        val (farLat, farLon) =
            destinationM(home.centerLat, home.centerLon, bearingDeg = 90.0, distanceM = 320_000.0)
        val far = testRegion(name = "Far", centerLat = farLat, centerLon = farLon, radiusM = 10_000.0)
        val regions = listOf(home, far)

        val contributing = ContributingRegions.contributingRegions(regions, home.centerLat, home.centerLon)

        assertThat(contributing).containsExactly(home)
    }

    @Test
    fun nearEdgeJustInside300Km_isIncluded() {
        val home = testRegion(name = "Home", centerLat = 32.0, centerLon = 35.0, radiusM = 10_000.0)
        val collectionRadius = RegionBounds.dataCollectionRadiusM(home.radiusM)
        val centerDistance = AppConfig.CONTRIBUTING_REGION_MAX_DISTANCE_M - collectionRadius + 1_000.0
        val (edgeLat, edgeLon) =
            destinationM(home.centerLat, home.centerLon, bearingDeg = 0.0, distanceM = centerDistance)
        val edge = testRegion(name = "Edge", centerLat = edgeLat, centerLon = edgeLon, radiusM = 10_000.0)
        val regions = listOf(home, edge)

        val contributing = ContributingRegions.contributingRegions(regions, home.centerLat, home.centerLon)

        assertThat(contributing).containsExactly(home, edge)
    }

    @Test
    fun maxHorizonRadiusM_capsAt300Km() {
        val home = testRegion(name = "Home", centerLat = 32.0, centerLon = 35.0, radiusM = 10_000.0)
        val (nearLat, nearLon) =
            destinationM(home.centerLat, home.centerLon, bearingDeg = 90.0, distanceM = 80_000.0)
        val near = testRegion(name = "Near", centerLat = nearLat, centerLon = nearLon, radiusM = 20_000.0)
        val regions = listOf(home, near)

        val radiusM = ContributingRegions.maxHorizonRadiusM(regions, home.centerLat, home.centerLon)

        assertThat(radiusM).isAtMost(AppConfig.CONTRIBUTING_REGION_MAX_DISTANCE_M)
        assertThat(radiusM).isGreaterThan(RegionBounds.dataCollectionRadiusM(home))
    }

    private fun testRegion(name: String, centerLat: Double, centerLon: Double, radiusM: Double): Region {
        val box = RegionBounds.boundingBox(centerLat, centerLon, radiusM)
        return Region(
            id = UUID.randomUUID(),
            name = name,
            centerLat = centerLat,
            centerLon = centerLon,
            radiusM = radiusM,
            minLat = box.minLat,
            maxLat = box.maxLat,
            minLon = box.minLon,
            maxLon = box.maxLon,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            downloadStatus = DownloadStatus.READY,
            downloadProgressPct = 100,
            osmDatasetVersion = null,
            estimatedSizeBytes = 0,
            entityCount = 0
        )
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
