package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import com.nofar.core.model.GeoEntityType
import kotlin.math.abs
import kotlin.math.round

data class ProjectedLabel(
    val entityId: String,
    val name: String,
    val isPeak: Boolean,
    val elevationM: Int?,
    val distanceM: Double,
    val distanceDisplay: String,
    val terrainAnchorXPx: Float,
    val terrainAnchorYPx: Float,
    val cardXPx: Float,
    val cardYPx: Float,
    val bucketIndex: Int,
    val estimatedWidthPx: Float = 0f
)

data class ClusteredLabel(
    val anchorXPx: Float,
    val anchorYPx: Float,
    val bucketIndex: Int,
    val labels: List<ProjectedLabel>,
    val hiddenCount: Int
)

object ExploreDistanceFormatter {
    fun format(distanceM: Double): String {
        val km = distanceM / 1_000.0
        val rounded = round(km * 10.0) / 10.0
        return "$rounded km"
    }
}

/**
 * Screen-space clustering for Explore labels (Requirements §3.3.2).
 *
 * Places labels on vertical shelves with horizontal AABB collision checks so nearby
 * azimuths do not stack on the same track. Overflow collapses into "+N more" on the
 * nearest placed label.
 */
object LabelClustering {
    fun cluster(
        projected: List<ProjectedLabel>,
        screenWidthPx: Float,
        screenHeightPx: Float,
        shelfCount: Int = AppConfig.EXPLORE_LABEL_SHELF_COUNT,
        shelfPitchPx: Int = AppConfig.EXPLORE_LABEL_SHELF_PITCH_PX,
        leaderGapPx: Int = AppConfig.EXPLORE_LABEL_LEADER_GAP_PX,
        expandedBucketIndex: Int? = null
    ): List<ClusteredLabel> {
        if (projected.isEmpty()) return emptyList()

        val prepared =
            projected.map { label ->
                val width = estimateCardWidthPx(label.name)
                label.copy(
                    estimatedWidthPx = width,
                    bucketIndex = bucketIndex(label.terrainAnchorXPx),
                    cardXPx = label.terrainAnchorXPx,
                    cardYPx = label.terrainAnchorYPx
                )
            }

        val baseline =
            LabelShelfLayout.placeLabels(
                prepared = prepared,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                shelfCount = shelfCount,
                shelfPitchPx = shelfPitchPx,
                leaderGapPx = leaderGapPx,
                forcePlaceEntityIds = emptySet(),
                forcePlaceGroupId = null
            )

        val forcePlaceEntityIds =
            if (expandedBucketIndex == null) {
                emptySet()
            } else {
                LabelShelfLayout.expandedMemberEntityIds(
                    pass = baseline,
                    prepared = prepared,
                    expandedBucketIndex = expandedBucketIndex
                )
            }

        val pass =
            if (forcePlaceEntityIds.isEmpty()) {
                baseline
            } else {
                LabelShelfLayout.placeLabels(
                    prepared = prepared,
                    screenWidthPx = screenWidthPx,
                    screenHeightPx = screenHeightPx,
                    shelfCount = shelfCount,
                    shelfPitchPx = shelfPitchPx,
                    leaderGapPx = leaderGapPx,
                    forcePlaceEntityIds = forcePlaceEntityIds,
                    forcePlaceGroupId = expandedBucketIndex
                )
            }

        return LabelShelfLayout.toClusters(
            pass = pass,
            expandedBucketIndex = expandedBucketIndex
        )
    }

    fun bucketIndex(anchorXPx: Float, bucketWidthPx: Int = AppConfig.EXPLORE_CLUSTER_BUCKET_WIDTH_PX): Int =
        kotlin.math.floor(anchorXPx / bucketWidthPx).toInt()

    fun estimateCardWidthPx(name: String): Float {
        val raw =
            name.length * AppConfig.EXPLORE_LABEL_CHAR_WIDTH_PX +
                AppConfig.EXPLORE_LABEL_HORIZONTAL_PADDING_PX
        return raw
            .toFloat()
            .coerceIn(
                AppConfig.EXPLORE_LABEL_MIN_WIDTH_PX.toFloat(),
                AppConfig.EXPLORE_LABEL_MAX_WIDTH_PX.toFloat()
            )
    }
}

private object LabelShelfLayout {
    data class ShelfSpan(val leftPx: Float, val rightPx: Float)

    data class PlacementPass(
        val placed: List<ProjectedLabel>,
        val hiddenByPrimaryEntityId: Map<String, List<ProjectedLabel>>
    )

    fun placeLabels(
        prepared: List<ProjectedLabel>,
        screenWidthPx: Float,
        screenHeightPx: Float,
        shelfCount: Int,
        shelfPitchPx: Int,
        leaderGapPx: Int,
        forcePlaceEntityIds: Set<String>,
        forcePlaceGroupId: Int?
    ): PlacementPass {
        val sorted = prepared.sortedBy { it.distanceM }
        val expandExtraShelves = if (forcePlaceEntityIds.isNotEmpty()) sorted.size else 0
        val occupancy =
            Array(shelfCount + expandExtraShelves) { mutableListOf<ShelfSpan>() }
        val placed = mutableListOf<ProjectedLabel>()
        val hidden = mutableListOf<ProjectedLabel>()

        for (label in sorted) {
            val forcePlace = label.entityId in forcePlaceEntityIds
            val placedLabel =
                tryPlaceOnShelf(
                    label = label,
                    forcePlace = forcePlace,
                    forcePlaceGroupId = forcePlaceGroupId,
                    shelfCount = shelfCount,
                    shelfPitchPx = shelfPitchPx,
                    leaderGapPx = leaderGapPx,
                    screenHeightPx = screenHeightPx,
                    occupancy = occupancy
                )
            if (placedLabel == null) {
                hidden.add(label)
            } else {
                placed.add(placedLabel)
            }
        }

        return PlacementPass(
            placed = placed,
            hiddenByPrimaryEntityId = attachHidden(hidden, placed, screenWidthPx)
        )
    }

    fun expandedMemberEntityIds(
        pass: PlacementPass,
        prepared: List<ProjectedLabel>,
        expandedBucketIndex: Int
    ): Set<String> {
        val fromBucket =
            prepared
                .asSequence()
                .filter { it.bucketIndex == expandedBucketIndex }
                .map { it.entityId }
        val primaries = pass.placed.filter { it.bucketIndex == expandedBucketIndex }
        val attached =
            primaries
                .asSequence()
                .flatMap { primary -> pass.hiddenByPrimaryEntityId[primary.entityId].orEmpty() }
                .map { it.entityId }
        return (fromBucket + attached).toCollection(linkedSetOf())
    }

    fun toClusters(pass: PlacementPass, expandedBucketIndex: Int?): List<ClusteredLabel> = pass.placed
        .groupBy { it.bucketIndex }
        .toSortedMap()
        .map { (groupId, labelsInGroup) ->
            val primary = labelsInGroup.minBy { it.distanceM }
            val hiddenForGroup =
                labelsInGroup.flatMap { label ->
                    pass.hiddenByPrimaryEntityId[label.entityId].orEmpty()
                }
            val showAll = expandedBucketIndex == groupId
            ClusteredLabel(
                anchorXPx = primary.terrainAnchorXPx,
                anchorYPx = primary.terrainAnchorYPx,
                bucketIndex = groupId,
                labels = labelsInGroup.sortedBy { it.distanceM },
                hiddenCount =
                if (showAll) {
                    0
                } else {
                    hiddenForGroup.size
                }
            )
        }

    private fun tryPlaceOnShelf(
        label: ProjectedLabel,
        forcePlace: Boolean,
        forcePlaceGroupId: Int?,
        shelfCount: Int,
        shelfPitchPx: Int,
        leaderGapPx: Int,
        screenHeightPx: Float,
        occupancy: Array<MutableList<ShelfSpan>>
    ): ProjectedLabel? {
        val maxShelfExclusive = if (forcePlace) occupancy.size else shelfCount
        val shelf =
            findShelf(
                label = label,
                maxShelfExclusive = maxShelfExclusive,
                occupancy = occupancy
            ) ?: return null
        val cardY =
            cardYForShelf(
                terrainYPx = label.terrainAnchorYPx,
                shelfIndex = shelf,
                shelfPitchPx = shelfPitchPx,
                leaderGapPx = leaderGapPx,
                screenHeightPx = screenHeightPx
            )
        val placedLabel =
            label.copy(
                cardXPx = label.terrainAnchorXPx,
                cardYPx = cardY,
                bucketIndex =
                if (forcePlace && forcePlaceGroupId != null) {
                    forcePlaceGroupId
                } else {
                    label.bucketIndex
                }
            )
        occupyShelf(occupancy, shelf, placedLabel)
        return placedLabel
    }

    private fun attachHidden(
        hidden: List<ProjectedLabel>,
        placed: List<ProjectedLabel>,
        screenWidthPx: Float
    ): Map<String, List<ProjectedLabel>> {
        val hiddenByPrimaryEntityId = LinkedHashMap<String, MutableList<ProjectedLabel>>()
        for (overflow in hidden) {
            val target =
                findAttachTarget(
                    overflow = overflow,
                    placed = placed,
                    screenWidthPx = screenWidthPx
                ) ?: continue
            hiddenByPrimaryEntityId
                .getOrPut(target.entityId) { mutableListOf() }
                .add(overflow)
        }
        return hiddenByPrimaryEntityId
    }

    private fun findShelf(
        label: ProjectedLabel,
        maxShelfExclusive: Int,
        occupancy: Array<MutableList<ShelfSpan>>
    ): Int? {
        val halfWidth = label.estimatedWidthPx / 2f + AppConfig.EXPLORE_LABEL_COLLISION_PAD_PX
        val left = label.terrainAnchorXPx - halfWidth
        val right = label.terrainAnchorXPx + halfWidth
        for (shelf in 0 until maxShelfExclusive.coerceAtMost(occupancy.size)) {
            val spans = occupancy[shelf]
            val overlaps = spans.any { span -> left < span.rightPx && right > span.leftPx }
            if (!overlaps) {
                return shelf
            }
        }
        return null
    }

    private fun occupyShelf(occupancy: Array<MutableList<ShelfSpan>>, shelf: Int, label: ProjectedLabel) {
        val halfWidth = label.estimatedWidthPx / 2f + AppConfig.EXPLORE_LABEL_COLLISION_PAD_PX
        occupancy[shelf].add(
            ShelfSpan(
                leftPx = label.terrainAnchorXPx - halfWidth,
                rightPx = label.terrainAnchorXPx + halfWidth
            )
        )
    }

    private fun cardYForShelf(
        terrainYPx: Float,
        shelfIndex: Int,
        shelfPitchPx: Int,
        leaderGapPx: Int,
        screenHeightPx: Float
    ): Float {
        val raw = terrainYPx - leaderGapPx - shelfIndex * shelfPitchPx
        val minCardY = AppConfig.EXPLORE_LABEL_ESTIMATED_HEIGHT_PX.toFloat()
        val maxCardY = (terrainYPx - leaderGapPx).coerceAtLeast(minCardY)
        return raw.coerceIn(minCardY, maxCardY.coerceAtMost(screenHeightPx))
    }

    private fun findAttachTarget(
        overflow: ProjectedLabel,
        placed: List<ProjectedLabel>,
        screenWidthPx: Float
    ): ProjectedLabel? {
        if (placed.isEmpty()) return null
        val proximityPx = screenWidthPx * 0.05f
        val overlapping =
            placed.filter { candidate ->
                val halfWidth =
                    candidate.estimatedWidthPx / 2f + AppConfig.EXPLORE_LABEL_COLLISION_PAD_PX
                val left = candidate.cardXPx - halfWidth
                val right = candidate.cardXPx + halfWidth
                val spansOverlap = overflow.terrainAnchorXPx in left..right
                val near =
                    abs(overflow.terrainAnchorXPx - candidate.terrainAnchorXPx) <= proximityPx
                spansOverlap || near || candidate.bucketIndex == overflow.bucketIndex
            }
        val pool = overlapping.ifEmpty { placed }
        return pool.minBy { abs(it.terrainAnchorXPx - overflow.terrainAnchorXPx) }
    }
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
                    terrainAnchorXPx = projection.anchorXPx,
                    terrainAnchorYPx = projection.anchorYPx,
                    cardXPx = projection.anchorXPx,
                    cardYPx = projection.anchorYPx,
                    bucketIndex =
                    LabelClustering.bucketIndex(
                        anchorXPx = projection.anchorXPx
                    )
                )
            }

        return LabelClustering.cluster(
            projected = projected,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            expandedBucketIndex = expandedBucketIndex
        )
    }
}
