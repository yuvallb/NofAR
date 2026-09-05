package com.nofar.feature.explore

import com.nofar.core.data.repository.CoverageSetRepository
import com.nofar.core.model.AppConfig
import com.nofar.core.model.CellMembership
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DemTileId
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.GeoMathBounds
import java.util.UUID

internal data class CoverageSetSelection(
    val membership: List<CoverageSet>,
    val contributing: List<CoverageSet>,
    val cellIds: Set<String>
)

internal object ExploreCoverageHelper {
    private val exploreEligibleStatuses = setOf(DownloadStatus.READY, DownloadStatus.PARTIAL)

    fun exploreEligible(coverageSets: List<CoverageSet>): List<CoverageSet> =
        coverageSets.filter { it.downloadStatus in exploreEligibleStatuses }

    fun cellIntersectsQueryRadius(
        cellId: String,
        lat: Double,
        lon: Double,
        radiusM: Double = AppConfig.EXPLORE_ENTITY_QUERY_RADIUS_M
    ): Boolean {
        val parsed = DemTileId.parse(cellId) ?: return false
        val (tileLat, tileLon) = parsed
        val minLat = tileLat.toDouble()
        val maxLat = minLat + 1.0
        val minLon = tileLon.toDouble()
        val maxLon = minLon + 1.0
        val nearestLat = lat.coerceIn(minLat, maxLat)
        val nearestLon = lon.coerceIn(minLon, maxLon)
        return GeoMathBounds.haversineDistanceM(lat, lon, nearestLat, nearestLon) <= radiusM
    }

    suspend fun contributingCoverageSets(
        repository: CoverageSetRepository,
        eligible: List<CoverageSet>,
        lat: Double,
        lon: Double
    ): List<CoverageSet> = eligible.filter { set ->
        repository.getCellIdsForCoverageSet(set.id).any { cellId ->
            cellIntersectsQueryRadius(cellId, lat, lon)
        }
    }

    suspend fun membershipCoverageSets(
        repository: CoverageSetRepository,
        eligible: List<CoverageSet>,
        lat: Double,
        lon: Double
    ): List<CoverageSet> = eligible.filter { set ->
        val cellIds = repository.getCellIdsForCoverageSet(set.id).toSet()
        CellMembership.hasCell(cellIds, lat, lon)
    }

    suspend fun selectForLocation(
        repository: CoverageSetRepository,
        allCoverageSets: List<CoverageSet>,
        lat: Double,
        lon: Double
    ): CoverageSetSelection {
        val eligible = exploreEligible(allCoverageSets)
        val membership = membershipCoverageSets(repository, eligible, lat, lon).sortedByDescending { it.updatedAt }
        val contributing = contributingCoverageSets(repository, eligible, lat, lon).sortedByDescending { it.updatedAt }
        val cellIds = repository.getCellIdsForCoverageSets(contributing.map { it.id }).toSet()
        return CoverageSetSelection(membership = membership, contributing = contributing, cellIds = cellIds)
    }

    fun isInsideMembership(
        membership: List<CoverageSet>,
        cellIdsBySet: Map<UUID, Set<String>>,
        lat: Double,
        lon: Double
    ): Boolean = membership.any { set ->
        CellMembership.hasCell(cellIdsBySet[set.id].orEmpty(), lat, lon)
    }
}
