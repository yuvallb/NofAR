package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LabelClusteringTest {
    @Test
    fun distanceFormatter_alwaysUsesKilometersWithOneDecimal() {
        assertThat(ExploreDistanceFormatter.format(821.0)).isEqualTo("0.8 km")
        assertThat(ExploreDistanceFormatter.format(7_500.0)).isEqualTo("7.5 km")
        assertThat(ExploreDistanceFormatter.format(1_000.0)).isEqualTo("1.0 km")
    }

    @Test
    fun nearbyLabels_occupyDifferentShelves() {
        val projected =
            listOf(
                labelAt(distanceM = 1_000.0, name = "Near", terrainX = 100f, terrainY = 500f),
                labelAt(distanceM = 1_100.0, name = "AlsoNear", terrainX = 105f, terrainY = 500f)
            )

        val clusters =
            LabelClustering.cluster(
                projected = projected,
                screenWidthPx = 1_080f,
                screenHeightPx = 1_920f
            )

        val labels = clusters.flatMap { it.labels }
        assertThat(labels).hasSize(2)
        assertThat(labels.map { it.cardYPx }.toSet()).hasSize(2)
        assertThat(labels.minBy { it.distanceM }.cardYPx)
            .isGreaterThan(labels.maxBy { it.distanceM }.cardYPx)
    }

    @Test
    fun shelfExhaustion_attachesHiddenCountToPrimary() {
        val projected =
            (0 until 8).map { index ->
                labelAt(
                    distanceM = 1_000.0 + index * 100.0,
                    name = "Peak$index",
                    terrainX = 100f + index * 2f,
                    terrainY = 500f
                )
            }

        val clusters =
            LabelClustering.cluster(
                projected = projected,
                screenWidthPx = 1_080f,
                screenHeightPx = 1_920f,
                shelfCount = 5
            )

        val placedCount = clusters.sumOf { it.labels.size }
        val hiddenCount = clusters.sumOf { it.hiddenCount }
        assertThat(placedCount).isEqualTo(5)
        assertThat(hiddenCount).isEqualTo(3)
        assertThat(clusters.any { it.hiddenCount > 0 }).isTrue()
    }

    @Test
    fun placedLabels_keepDistinctTerrainAndCardAnchorsWhenStacked() {
        val projected =
            listOf(
                labelAt(distanceM = 1_000.0, name = "Near", terrainX = 100f, terrainY = 500f),
                labelAt(distanceM = 5_000.0, name = "Far", terrainX = 100f, terrainY = 500f)
            )

        val clusters =
            LabelClustering.cluster(
                projected = projected,
                screenWidthPx = 1_080f,
                screenHeightPx = 1_920f
            )

        val labels = clusters.first().labels
        assertThat(labels).hasSize(2)
        labels.forEach { label ->
            assertThat(label.terrainAnchorYPx).isEqualTo(500f)
            assertThat(label.cardYPx).isLessThan(label.terrainAnchorYPx)
        }
        assertThat(labels.first().cardYPx).isGreaterThan(labels.last().cardYPx)
    }

    @Test
    fun expandedBucket_showsAllLabelsWithoutHiddenCount() {
        val projected =
            (0 until 8).map { index ->
                labelAt(
                    distanceM = 1_000.0 + index * 100.0,
                    name = "Peak$index",
                    terrainX = 100f,
                    terrainY = 500f
                )
            }
        val groupId = LabelClustering.bucketIndex(100f)

        val clusters =
            LabelClustering.cluster(
                projected = projected,
                screenWidthPx = 1_080f,
                screenHeightPx = 1_920f,
                expandedBucketIndex = groupId
            )

        assertThat(clusters).hasSize(1)
        assertThat(clusters.first().labels).hasSize(8)
        assertThat(clusters.first().hiddenCount).isEqualTo(0)
    }

    private fun labelAt(distanceM: Double, name: String, terrainX: Float, terrainY: Float): ProjectedLabel =
        ProjectedLabel(
            entityId = name,
            name = name,
            isPeak = true,
            elevationM = 1_000,
            distanceM = distanceM,
            distanceDisplay = ExploreDistanceFormatter.format(distanceM),
            terrainAnchorXPx = terrainX,
            terrainAnchorYPx = terrainY,
            cardXPx = terrainX,
            cardYPx = terrainY,
            bucketIndex = LabelClustering.bucketIndex(terrainX)
        )
}
