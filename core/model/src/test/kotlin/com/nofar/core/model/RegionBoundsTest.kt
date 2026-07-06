package com.nofar.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RegionBoundsTest {
    @Test
    fun haversineDistance_samePoint_isZero() {
        val distance = RegionBounds.haversineDistanceM(32.0, 35.0, 32.0, 35.0)
        assertThat(distance).isWithin(0.001).of(0.0)
    }

    @Test
    fun containsPoint_insideRadius_returnsTrue() {
        val box = RegionBounds.boundingBox(32.0, 35.0, 10_000.0)
        val region =
            Region(
                id = java.util.UUID.randomUUID(),
                name = "Test",
                centerLat = 32.0,
                centerLon = 35.0,
                radiusM = 10_000.0,
                minLat = box.minLat,
                maxLat = box.maxLat,
                minLon = box.minLon,
                maxLon = box.maxLon,
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now(),
                downloadStatus = DownloadStatus.READY,
                downloadProgressPct = 100,
                osmDatasetVersion = null,
                estimatedSizeBytes = 0,
                entityCount = 0
            )
        assertThat(RegionBounds.containsPoint(region, 32.01, 35.01)).isTrue()
    }

    @Test
    fun overlappingCircles_shareEntities() {
        val centerDistance =
            RegionBounds.haversineDistanceM(32.0, 35.0, 32.05, 35.05)
        assertThat(centerDistance).isLessThan(10_000.0)
    }
}
