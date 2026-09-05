package com.nofar.core.visibility

import com.nofar.core.model.CoverageSet
import com.nofar.core.model.UserLocation

interface CoverageVisibilityComputer {
    /**
     * @param computeHorizonProfile when false the skyline sweep is skipped entirely (no DEM samples
     * spent) and [VisibilityResult.horizonProfile] is null.
     */
    suspend fun computeForCoverageSets(
        coverageSets: List<CoverageSet>,
        cellIds: Set<String>,
        location: UserLocation,
        computeHorizonProfile: Boolean = true
    ): VisibilityResult
}

/** @deprecated Use [CoverageVisibilityComputer]. */
typealias RegionVisibilityComputer = CoverageVisibilityComputer
