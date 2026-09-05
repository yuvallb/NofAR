package com.nofar.core.data.usecase

import com.nofar.core.data.repository.CoverageSetRepository
import com.nofar.core.model.CellMembership
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
import java.util.UUID
import javax.inject.Inject

/**
 * Determines whether a GPS point lies inside a coverage set (1° cell membership).
 */
class InsideCoverageUseCase
@Inject
constructor(private val coverageSetRepository: CoverageSetRepository) {
    suspend fun isInsideCoverageSet(coverageSet: CoverageSet, lat: Double, lon: Double): Boolean {
        val cellIds = coverageSetRepository.getCellIdsForCoverageSet(coverageSet.id).toSet()
        return CellMembership.hasCell(cellIds, lat, lon)
    }

    fun isExploreEligible(coverageSet: CoverageSet): Boolean = coverageSet.downloadStatus == DownloadStatus.READY ||
        coverageSet.downloadStatus == DownloadStatus.PARTIAL

    suspend fun coverageSetsContainingPoint(lat: Double, lon: Double): List<CoverageSet> =
        coverageSetRepository.coverageSetsContainingPoint(lat, lon)

    suspend fun exploreEligibleCoverageSetsContainingPoint(lat: Double, lon: Double): List<CoverageSet> =
        coverageSetsContainingPoint(lat, lon).filter(::isExploreEligible)

    suspend fun insideCoverageSetIds(lat: Double, lon: Double, coverageSets: List<CoverageSet>): Set<UUID> =
        coverageSets.filter { coverageSet ->
            runCatching { isInsideCoverageSet(coverageSet, lat, lon) }.getOrDefault(false)
        }.map { it.id }.toSet()
}
