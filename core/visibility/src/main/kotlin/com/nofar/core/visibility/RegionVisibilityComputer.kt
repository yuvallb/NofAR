package com.nofar.core.visibility

import com.nofar.core.model.Region
import com.nofar.core.model.UserLocation

interface RegionVisibilityComputer {
    /**
     * @param computeHorizonProfile when false the skyline sweep is skipped entirely (no DEM samples
     * spent) and [VisibilityResult.horizonProfile] is null. Set from the Explore horizon-outline
     * preference so a disabled outline costs nothing on the visibility pass (H-P1-11).
     */
    suspend fun computeForRegions(
        regions: List<Region>,
        location: UserLocation,
        computeHorizonProfile: Boolean = true
    ): VisibilityResult
}
