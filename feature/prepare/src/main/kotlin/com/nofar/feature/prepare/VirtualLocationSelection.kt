package com.nofar.feature.prepare

import com.nofar.core.model.AppConfig
import com.nofar.core.model.CellMembership
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DemTileId
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.GeoMathBounds
import java.util.UUID

data class VirtualLocationSelection(
    val lat: Double,
    val lon: Double,
    val primaryCoverageSetId: UUID,
    val containingCoverageSetIds: Set<UUID>,
    val contributingCoverageSetIds: Set<UUID>
) {
    val primaryRegionId: UUID get() = primaryCoverageSetId

    val contributingRegionIds: Set<UUID> get() = contributingCoverageSetIds
}

object VirtualLocationSelectionLogic {
    fun isValidCoordinate(lat: Double, lon: Double): Boolean = lat in -90.0..90.0 && lon in -180.0..180.0

    fun exploreEligible(regions: List<CoverageSet>): List<CoverageSet> = regions.filter {
        it.downloadStatus == DownloadStatus.READY || it.downloadStatus == DownloadStatus.PARTIAL
    }

    fun coverageSetsContainingPoint(
        regions: List<CoverageSet>,
        cellIdsBySet: Map<UUID, Set<String>>,
        lat: Double,
        lon: Double
    ): List<CoverageSet> = exploreEligible(regions).filter { set ->
        CellMembership.hasCell(cellIdsBySet[set.id].orEmpty(), lat, lon)
    }

    fun contributingCoverageSets(
        regions: List<CoverageSet>,
        cellIdsBySet: Map<UUID, Set<String>>,
        lat: Double,
        lon: Double
    ): List<CoverageSet> = exploreEligible(regions).filter { set ->
        cellIdsBySet[set.id].orEmpty().any { cellId ->
            cellIntersectsQueryRadius(cellId, lat, lon)
        }
    }

    fun resolveSelection(
        regions: List<CoverageSet>,
        cellIdsBySet: Map<UUID, Set<String>>,
        lat: Double,
        lon: Double
    ): VirtualLocationSelection? {
        if (!isValidCoordinate(lat, lon)) return null
        val containing = coverageSetsContainingPoint(regions, cellIdsBySet, lat, lon)
        val contributing = contributingCoverageSets(regions, cellIdsBySet, lat, lon)
        return containing.maxWithOrNull(primaryCoverageSetComparator)?.let { primary ->
            VirtualLocationSelection(
                lat = lat,
                lon = lon,
                primaryCoverageSetId = primary.id,
                containingCoverageSetIds = containing.map { it.id }.toSet(),
                contributingCoverageSetIds = contributing.map { it.id }.toSet()
            )
        }
    }

    fun initialMapCenter(
        regions: List<CoverageSet>,
        cellIdsBySet: Map<UUID, Set<String>>,
        deviceLat: Double?,
        deviceLon: Double?
    ): Pair<Double, Double>? {
        val eligible = exploreEligible(regions)
        if (eligible.isEmpty()) return null
        val atDevice =
            deviceLat != null &&
                deviceLon != null &&
                coverageSetsContainingPoint(eligible, cellIdsBySet, deviceLat, deviceLon).isNotEmpty()
        return when {
            atDevice -> deviceLat to deviceLon
            else ->
                eligible.maxByOrNull { it.updatedAt }?.let { set ->
                    cellCenter(cellIdsBySet[set.id]?.firstOrNull())
                }
        }
    }

    fun cellIdsForSelection(cellIdsBySet: Map<UUID, Set<String>>, coverageSetIds: Set<UUID>): Set<String> =
        coverageSetIds.flatMap { cellIdsBySet[it].orEmpty() }.toSet()

    private fun cellCenter(cellId: String?): Pair<Double, Double>? {
        val parsed = cellId?.let { DemTileId.parse(it) } ?: return null
        val (tileLat, tileLon) = parsed
        return (tileLat + 0.5) to (tileLon + 0.5)
    }

    private fun cellIntersectsQueryRadius(cellId: String, lat: Double, lon: Double): Boolean {
        val parsed = DemTileId.parse(cellId) ?: return false
        val (tileLat, tileLon) = parsed
        val minLat = tileLat.toDouble()
        val maxLat = minLat + 1.0
        val minLon = tileLon.toDouble()
        val maxLon = minLon + 1.0
        val nearestLat = lat.coerceIn(minLat, maxLat)
        val nearestLon = lon.coerceIn(minLon, maxLon)
        return GeoMathBounds.haversineDistanceM(lat, lon, nearestLat, nearestLon) <=
            AppConfig.EXPLORE_ENTITY_QUERY_RADIUS_M
    }

    private val primaryCoverageSetComparator: Comparator<CoverageSet> =
        compareBy<CoverageSet> { it.downloadStatus == DownloadStatus.READY }
            .thenBy { it.updatedAt }
}
