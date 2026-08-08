package com.nofar.feature.prepare

import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import java.util.UUID

data class VirtualLocationSelection(
    val lat: Double,
    val lon: Double,
    val primaryRegionId: UUID,
    val containingRegionIds: Set<UUID>
)

object VirtualLocationSelectionLogic {
    fun isValidCoordinate(lat: Double, lon: Double): Boolean = lat in -90.0..90.0 && lon in -180.0..180.0

    fun exploreEligible(regions: List<Region>): List<Region> = regions.filter {
        it.downloadStatus == DownloadStatus.READY || it.downloadStatus == DownloadStatus.PARTIAL
    }

    fun regionsContainingPoint(regions: List<Region>, lat: Double, lon: Double): List<Region> =
        exploreEligible(regions).filter { RegionBounds.containsPoint(it, lat, lon) }

    fun resolveSelection(regions: List<Region>, lat: Double, lon: Double): VirtualLocationSelection? {
        if (!isValidCoordinate(lat, lon)) return null
        val containing = regionsContainingPoint(regions, lat, lon)
        return containing.maxWithOrNull(primaryRegionComparator)?.let { primary ->
            VirtualLocationSelection(
                lat = lat,
                lon = lon,
                primaryRegionId = primary.id,
                containingRegionIds = containing.map { it.id }.toSet()
            )
        }
    }

    fun initialMapCenter(regions: List<Region>, deviceLat: Double?, deviceLon: Double?): Pair<Double, Double>? {
        val eligible = exploreEligible(regions)
        if (eligible.isEmpty()) return null
        val atDevice =
            deviceLat != null &&
                deviceLon != null &&
                regionsContainingPoint(eligible, deviceLat, deviceLon).isNotEmpty()
        return when {
            atDevice -> deviceLat to deviceLon
            else -> eligible.maxByOrNull { it.updatedAt }?.let { it.centerLat to it.centerLon }
        }
    }

    private val primaryRegionComparator: Comparator<Region> =
        compareBy<Region> { it.downloadStatus == DownloadStatus.READY }
            .thenBy { it.radiusM }
            .thenBy { it.updatedAt }
}
