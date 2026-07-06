package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LabelClusteringTest {
    @Test
    fun fiveLabelsInSameBucket_showsTwoPlusThreeMore() {
        val projected =
            (1..5).map { index ->
                ProjectedLabel(
                    entityId = "entity-$index",
                    name = "Peak $index",
                    isPeak = true,
                    elevationM = 1_000 + index,
                    distanceM = index * 1_000.0,
                    distanceDisplay = "$index km",
                    anchorXPx = 100f,
                    anchorYPx = 500f,
                    bucketIndex = 2
                )
            }

        val clusters =
            LabelClustering.cluster(
                projected = projected,
                maxLabelsPerBucket = 2
            )

        assertThat(clusters).hasSize(1)
        assertThat(clusters.first().labels).hasSize(2)
        assertThat(clusters.first().hiddenCount).isEqualTo(3)
    }

    @Test
    fun expandedBucket_showsAllLabelsWithoutHiddenCount() {
        val projected =
            (1..5).map { index ->
                ProjectedLabel(
                    entityId = "entity-$index",
                    name = "Peak $index",
                    isPeak = true,
                    elevationM = 1_000 + index,
                    distanceM = index * 1_000.0,
                    distanceDisplay = "$index km",
                    anchorXPx = 100f,
                    anchorYPx = 500f,
                    bucketIndex = 2
                )
            }

        val clusters =
            LabelClustering.cluster(
                projected = projected,
                expandedBucketIndex = 2
            )

        assertThat(clusters.first().labels).hasSize(5)
        assertThat(clusters.first().hiddenCount).isEqualTo(0)
    }

    @Test
    fun closerLabelsAppearLowerInStack() {
        val projected =
            listOf(
                labelAt(distanceM = 5_000.0, name = "Far"),
                labelAt(distanceM = 1_000.0, name = "Near")
            )

        val clusters = LabelClustering.cluster(projected = projected, stackOffsetPx = 50)

        assertThat(clusters.first().labels.first().name).isEqualTo("Near")
        assertThat(clusters.first().labels.last().name).isEqualTo("Far")
        assertThat(clusters.first().labels.first().anchorYPx)
            .isGreaterThan(clusters.first().labels.last().anchorYPx)
    }

    private fun labelAt(distanceM: Double, name: String): ProjectedLabel = ProjectedLabel(
        entityId = name,
        name = name,
        isPeak = true,
        elevationM = 1_000,
        distanceM = distanceM,
        distanceDisplay = ExploreDistanceFormatter.format(distanceM),
        anchorXPx = 100f,
        anchorYPx = 500f,
        bucketIndex = 2
    )
}
