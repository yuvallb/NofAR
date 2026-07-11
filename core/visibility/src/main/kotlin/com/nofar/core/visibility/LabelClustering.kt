package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import com.nofar.core.model.GeoEntityType

data class ProjectedLabel(
    val entityId: String,
    val name: String,
    val isPeak: Boolean,
    val elevationM: Int?,
    val distanceM: Double,
    val distanceDisplay: String,
    val anchorXPx: Float,
    val anchorYPx: Float,
    val bucketIndex: Int
)

data class ClusteredLabel(
    val anchorXPx: Float,
    val anchorYPx: Float,
    val bucketIndex: Int,
    val labels: List<ProjectedLabel>,
    val hiddenCount: Int
)

object ExploreDistanceFormatter {
    fun format(distanceM: Double): String = if (distanceM < 1_000.0) {
        "${distanceM.toInt()} m"
    } else {
        val km = distanceM / 1_000.0
        val rounded = (kotlin.math.round(km * 10.0) / 10.0)
        "$rounded km"
    }
}

/**
 * Screen-space clustering for Explore labels (Requirements §3.3.2).
 */
object LabelClustering {
    fun cluster(
        projected: List<ProjectedLabel>,
        maxLabelsPerBucket: Int = AppConfig.EXPLORE_MAX_LABELS_PER_BUCKET,
        stackOffsetPx: Int = AppConfig.EXPLORE_LABEL_STACK_OFFSET_PX,
        expandedBucketIndex: Int? = null
    ): List<ClusteredLabel> {
        if (projected.isEmpty()) return emptyList()

        val grouped =
            projected
                .groupBy { it.bucketIndex }
                .toSortedMap()

        return grouped.map { (bucketIndex, labelsInBucket) ->
            val sorted = labelsInBucket.sortedBy { it.distanceM }
            val showAll = expandedBucketIndex == bucketIndex
            val visibleCount =
                if (showAll) {
                    sorted.size
                } else {
                    minOf(sorted.size, maxLabelsPerBucket)
                }
            val visible = sorted.take(visibleCount)
            val hiddenCount =
                if (showAll) {
                    0
                } else {
                    (sorted.size - visibleCount).coerceAtLeast(0)
                }

            val anchor = sorted.first()
            val stacked =
                visible.mapIndexed { index, label ->
                    label.copy(
                        anchorXPx = anchor.anchorXPx,
                        anchorYPx = anchor.anchorYPx - index * stackOffsetPx
                    )
                }

            ClusteredLabel(
                anchorXPx = anchor.anchorXPx,
                anchorYPx = anchor.anchorYPx,
                bucketIndex = bucketIndex,
                labels = stacked,
                hiddenCount = hiddenCount
            )
        }
    }

    fun bucketIndex(anchorXPx: Float, bucketWidthPx: Int = AppConfig.EXPLORE_CLUSTER_BUCKET_WIDTH_PX): Int =
        kotlin.math.floor(anchorXPx / bucketWidthPx).toInt()
}

object ExploreLabelRenderer {
    fun projectAndCluster(
        entities: List<VisibleEntity>,
        trueAzimuthDeg: Float,
        pitchDeg: Float,
        fov: CameraFieldOfView,
        screenWidthPx: Float,
        screenHeightPx: Float,
        expandedBucketIndex: Int? = null
    ): List<ClusteredLabel> {
        val orientedFov = fov.orientedForScreen(screenWidthPx, screenHeightPx)
        val projected =
            entities.mapNotNull { entity ->
                val projection =
                    ScreenProjector.projectEntityToScreen(
                        bearingDeg = entity.bearingDeg,
                        elevationAngleDeg = entity.elevationAngleDeg,
                        trueAzimuthDeg = trueAzimuthDeg,
                        pitchDeg = pitchDeg,
                        horizontalFovDeg = orientedFov.horizontalDeg,
                        verticalFovDeg = orientedFov.verticalDeg,
                        screenWidthPx = screenWidthPx,
                        screenHeightPx = screenHeightPx
                    ) ?: return@mapNotNull null

                ProjectedLabel(
                    entityId = entity.entity.id,
                    name = entity.entity.name,
                    isPeak = entity.entity.type == GeoEntityType.PEAK,
                    elevationM = entity.entity.elevation?.toInt(),
                    distanceM = entity.distanceM,
                    distanceDisplay = ExploreDistanceFormatter.format(entity.distanceM),
                    anchorXPx = projection.anchorXPx,
                    anchorYPx = projection.anchorYPx,
                    bucketIndex =
                    LabelClustering.bucketIndex(
                        anchorXPx = projection.anchorXPx
                    )
                )
            }

        return LabelClustering.cluster(
            projected = projected,
            expandedBucketIndex = expandedBucketIndex
        )
    }
}
