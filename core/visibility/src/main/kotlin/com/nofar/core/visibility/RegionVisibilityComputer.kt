package com.nofar.core.visibility

import com.nofar.core.model.Region
import com.nofar.core.model.UserLocation

fun interface RegionVisibilityComputer {
    suspend fun computeForRegions(regions: List<Region>, location: UserLocation): VisibilityResult
}
