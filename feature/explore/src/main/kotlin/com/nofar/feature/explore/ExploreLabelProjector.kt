package com.nofar.feature.explore

import com.nofar.core.designsystem.component.ArLabel
import com.nofar.core.model.DeviceOrientation
import com.nofar.core.visibility.CameraFieldOfView
import com.nofar.core.visibility.ClusteredLabel
import com.nofar.core.visibility.ExploreLabelRenderer
import com.nofar.core.visibility.VisibleEntity

internal object ExploreLabelProjector {
    fun project(
        entities: List<VisibleEntity>,
        orientation: DeviceOrientation,
        fov: CameraFieldOfView,
        screenWidthPx: Float,
        screenHeightPx: Float,
        expandedBucketIndex: Int?
    ): Pair<List<ClusteredLabel>, List<ArLabel>> {
        val clusters =
            ExploreLabelRenderer.projectAndCluster(
                entities = entities,
                trueAzimuthDeg = orientation.trueAzimuthDeg,
                cameraElevationDeg = orientation.cameraElevationDeg,
                fov = fov,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                expandedBucketIndex = expandedBucketIndex
            )

        val labels =
            clusters.flatMap { cluster ->
                cluster.labels.map { label ->
                    ArLabel(
                        name = label.name,
                        elevationM = label.elevationM,
                        distanceDisplay = label.distanceDisplay,
                        isPeak = label.isPeak,
                        cardXPx = label.cardXPx,
                        cardYPx = label.cardYPx,
                        terrainAnchorXPx = label.terrainAnchorXPx,
                        terrainAnchorYPx = label.terrainAnchorYPx,
                        hiddenCount = if (label == cluster.labels.last()) cluster.hiddenCount else 0,
                        bucketIndex = cluster.bucketIndex
                    )
                }
            }

        return clusters to labels
    }
}
