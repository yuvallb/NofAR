package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
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
                labelAt(distanceM = 1_000.0, name = "Near", terrainX = 100f, terrainY = 900f),
                labelAt(distanceM = 1_100.0, name = "AlsoNear", terrainX = 105f, terrainY = 900f)
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
        assertThat(rectsOverlap(labels[0], labels[1])).isFalse()
    }

    @Test
    fun shelfExhaustion_attachesHiddenCountToPrimary() {
        // Enough vertical room for 5 non-overlapping shelves above the terrain anchors.
        val projected =
            (0 until 8).map { index ->
                labelAt(
                    distanceM = 1_000.0 + index * 100.0,
                    name = "Peak$index",
                    terrainX = 100f + index * 2f,
                    terrainY = 900f
                )
            }

        val clusters =
            LabelClustering.cluster(
                projected = projected,
                screenWidthPx = 1_080f,
                screenHeightPx = 1_920f,
                shelfCount = 5
            )

        val placed = clusters.flatMap { it.labels }
        val placedCount = placed.size
        val hiddenCount = clusters.sumOf { it.hiddenCount }
        assertThat(placedCount).isEqualTo(5)
        assertThat(hiddenCount).isEqualTo(3)
        assertThat(clusters.any { it.hiddenCount > 0 }).isTrue()
        for (i in placed.indices) {
            for (j in i + 1 until placed.size) {
                assertThat(rectsOverlap(placed[i], placed[j])).isFalse()
            }
        }
    }

    @Test
    fun placedLabels_keepDistinctTerrainAndCardAnchorsWhenStacked() {
        val projected =
            listOf(
                labelAt(distanceM = 1_000.0, name = "Near", terrainX = 100f, terrainY = 900f),
                labelAt(distanceM = 5_000.0, name = "Far", terrainX = 100f, terrainY = 900f)
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
            assertThat(label.terrainAnchorYPx).isEqualTo(900f)
            assertThat(label.cardYPx).isLessThan(label.terrainAnchorYPx)
        }
        assertThat(labels.first().cardYPx).isGreaterThan(labels.last().cardYPx)
        val verticalGap = labels.first().cardYPx - labels.last().cardYPx
        assertThat(verticalGap).isAtLeast(AppConfig.EXPLORE_LABEL_ESTIMATED_HEIGHT_PX.toFloat())
        assertThat(rectsOverlap(labels[0], labels[1])).isFalse()
    }

    @Test
    fun nearbyTerrainY_differentAnchors_stillAvoidCardOverlap() {
        val projected =
            listOf(
                labelAt(distanceM = 1_000.0, name = "Lower", terrainX = 200f, terrainY = 900f),
                labelAt(distanceM = 1_200.0, name = "Higher", terrainX = 210f, terrainY = 860f)
            )

        val clusters =
            LabelClustering.cluster(
                projected = projected,
                screenWidthPx = 1_080f,
                screenHeightPx = 1_920f
            )

        val labels = clusters.flatMap { it.labels }
        assertThat(labels).hasSize(2)
        assertThat(rectsOverlap(labels[0], labels[1])).isFalse()
    }

    @Test
    fun deduplicateByName_keepsLowestEntityIdPerDuplicateName() {
        val projected =
            listOf(
                labelAt(distanceM = 1_000.0, name = "Summit", terrainX = 100f, terrainY = 900f, entityId = "a"),
                labelAt(distanceM = 2_000.0, name = "Summit", terrainX = 120f, terrainY = 900f, entityId = "b"),
                labelAt(distanceM = 3_000.0, name = "Other", terrainX = 140f, terrainY = 900f, entityId = "c")
            )

        val deduplicated = LabelClustering.deduplicateByName(projected)

        assertThat(deduplicated.map { it.entityId }).containsExactly("a", "c")
    }

    @Test
    fun cluster_deduplicatesSameNameBeforePlacement() {
        val projected =
            listOf(
                labelAt(distanceM = 1_000.0, name = "Summit", terrainX = 100f, terrainY = 900f, entityId = "a"),
                labelAt(distanceM = 1_100.0, name = "Summit", terrainX = 105f, terrainY = 900f, entityId = "b"),
                labelAt(distanceM = 5_000.0, name = "Far", terrainX = 500f, terrainY = 900f, entityId = "c")
            )

        val clusters =
            LabelClustering.cluster(
                projected = projected,
                screenWidthPx = 1_080f,
                screenHeightPx = 1_920f
            )

        val labels = clusters.flatMap { it.labels }
        assertThat(labels.map { it.entityId }).containsExactly("a", "c")
    }

    @Test
    fun expandedBucket_showsAllLabelsWithoutHiddenCount() {
        val projected =
            (0 until 8).map { index ->
                labelAt(
                    distanceM = 1_000.0 + index * 100.0,
                    name = "Peak$index",
                    terrainX = 100f,
                    terrainY = 900f
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
        val labels = clusters.first().labels
        for (i in labels.indices) {
            for (j in i + 1 until labels.size) {
                assertThat(rectsOverlap(labels[i], labels[j])).isFalse()
            }
        }
    }

    private fun labelAt(
        distanceM: Double,
        name: String,
        terrainX: Float,
        terrainY: Float,
        entityId: String = name
    ): ProjectedLabel = ProjectedLabel(
        entityId = entityId,
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

    private fun rectsOverlap(a: ProjectedLabel, b: ProjectedLabel): Boolean {
        val hPad = AppConfig.EXPLORE_LABEL_COLLISION_PAD_PX
        val vGap = AppConfig.EXPLORE_LABEL_VERTICAL_GAP_PX / 2f
        val aWidth =
            a.estimatedWidthPx.takeIf { it > 0f } ?: LabelClustering.estimateCardWidthPx(a.name)
        val bWidth =
            b.estimatedWidthPx.takeIf { it > 0f } ?: LabelClustering.estimateCardWidthPx(b.name)
        val aHeight =
            a.estimatedHeightPx.takeIf { it > 0f }
                ?: LabelClustering.estimateCardHeightPx(hasElevation = a.elevationM != null)
        val bHeight =
            b.estimatedHeightPx.takeIf { it > 0f }
                ?: LabelClustering.estimateCardHeightPx(hasElevation = b.elevationM != null)
        val aLeft = a.cardXPx - aWidth / 2f - hPad
        val aRight = a.cardXPx + aWidth / 2f + hPad
        val aTop = a.cardYPx - aHeight - vGap
        val aBottom = a.cardYPx + vGap
        val bLeft = b.cardXPx - bWidth / 2f - hPad
        val bRight = b.cardXPx + bWidth / 2f + hPad
        val bTop = b.cardYPx - bHeight - vGap
        val bBottom = b.cardYPx + vGap
        return aLeft < bRight && aRight > bLeft && aTop < bBottom && aBottom > bTop
    }
}
