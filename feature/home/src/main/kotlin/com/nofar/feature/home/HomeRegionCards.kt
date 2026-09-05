package com.nofar.feature.home

import com.nofar.core.data.repository.HomeCoverageSetMetadataRepository
import com.nofar.core.data.usecase.InsideCoverageUseCase
import com.nofar.core.designsystem.component.CoverageSetCardState
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.UserLocation
import kotlin.math.max

internal suspend fun buildHomeCoverageSetCards(
    insideCoverageUseCase: InsideCoverageUseCase,
    metadataRepository: HomeCoverageSetMetadataRepository,
    coverageSetRepository: com.nofar.core.data.repository.CoverageSetRepository,
    coverageSets: List<CoverageSet>,
    location: UserLocation?
): List<CoverageSetCardState> {
    val cellIdsBySet =
        coverageSets.associate { set ->
            set.id to coverageSetRepository.getCellIdsForCoverageSet(set.id).toSet()
        }
    val sorted = HomeCoverageLogic.sortCoverageSetsForDisplay(coverageSets, location, cellIdsBySet)
    val insideIds =
        if (location != null) {
            insideCoverageUseCase.insideCoverageSetIds(location.latitude, location.longitude, coverageSets)
        } else {
            emptySet()
        }
    return sorted.map { coverageSet ->
        val isInside = coverageSet.id in insideIds
        val cellIds = cellIdsBySet[coverageSet.id].orEmpty().toList()
        val metadata = metadataRepository.getMetadata(coverageSet.id, cellIds)
        val demSizeBytes = metadata.demSizeBytes
        val displayEntityCount = max(coverageSet.entityCount, metadata.liveEntityCount)
        val demTimestamp =
            metadata.latestDemTimestamp
                ?: if (
                    coverageSet.downloadStatus == DownloadStatus.READY ||
                    coverageSet.downloadStatus == DownloadStatus.PARTIAL
                ) {
                    coverageSet.updatedAt
                } else {
                    null
                }
        val osmSizeBytes =
            if (demSizeBytes > 0L) {
                max(0L, coverageSet.estimatedSizeBytes - demSizeBytes)
            } else {
                coverageSet.estimatedSizeBytes
            }
        CoverageSetCardState(
            coverageSet = coverageSet.copy(entityCount = displayEntityCount),
            isYouAreHere = HomeCoverageLogic.shouldShowYouAreHere(coverageSet, isInside),
            osmSizeBytes = osmSizeBytes,
            demSizeBytes = demSizeBytes,
            latestDemTimestamp = demTimestamp
        )
    }
}
