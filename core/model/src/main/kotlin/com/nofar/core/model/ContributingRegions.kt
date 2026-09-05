package com.nofar.core.model

/**
 * Resolves which downloaded regions contribute DEM, horizon, and labels from an observer point.
 *
 * Membership (containing) uses [Region.radiusM]. Contributing regions extend up to
 * [AppConfig.CONTRIBUTING_REGION_MAX_DISTANCE_M] to the near edge of each region's collection disk.
 */
object ContributingRegions {
    fun isExploreEligible(region: Region): Boolean =
        region.downloadStatus == DownloadStatus.READY || region.downloadStatus == DownloadStatus.PARTIAL

    fun membershipRegions(regions: List<Region>, observerLat: Double, observerLon: Double): List<Region> =
        regions.filter { region ->
            isExploreEligible(region) && RegionBounds.containsPoint(region, observerLat, observerLon)
        }

    fun contributesAt(observerLat: Double, observerLon: Double, region: Region): Boolean {
        val collectionRadiusM = RegionBounds.dataCollectionRadiusM(region)
        val centerDistanceM =
            RegionBounds.haversineDistanceM(
                observerLat,
                observerLon,
                region.centerLat,
                region.centerLon
            )
        return centerDistanceM - collectionRadiusM <= AppConfig.CONTRIBUTING_REGION_MAX_DISTANCE_M
    }

    fun contributingRegions(regions: List<Region>, observerLat: Double, observerLon: Double): List<Region> {
        if (membershipRegions(regions, observerLat, observerLon).isEmpty()) {
            return emptyList()
        }
        return regions.filter { region -> isExploreEligible(region) && contributesAt(observerLat, observerLon, region) }
    }

    /** Outward horizon reach capped at [AppConfig.CONTRIBUTING_REGION_MAX_DISTANCE_M]. */
    fun maxHorizonRadiusM(regions: List<Region>, observerLat: Double, observerLon: Double): Double {
        if (regions.isEmpty()) return 0.0
        val farthestCollectionEdgeM =
            regions.maxOf { region ->
                val collectionRadiusM = RegionBounds.dataCollectionRadiusM(region)
                val centerDistanceM =
                    RegionBounds.haversineDistanceM(
                        observerLat,
                        observerLon,
                        region.centerLat,
                        region.centerLon
                    )
                centerDistanceM + collectionRadiusM
            }
        return minOf(AppConfig.CONTRIBUTING_REGION_MAX_DISTANCE_M, farthestCollectionEdgeM)
    }
}
