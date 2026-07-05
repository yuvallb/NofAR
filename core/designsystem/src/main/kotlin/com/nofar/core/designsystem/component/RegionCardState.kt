package com.nofar.core.designsystem.component

import com.nofar.core.model.Region
import java.time.Instant

data class RegionCardState(
    val region: Region,
    val isYouAreHere: Boolean,
    val canEnterExplore: Boolean,
    val osmSizeBytes: Long = region.estimatedSizeBytes,
    val demSizeBytes: Long = 0L,
    val latestDemTimestamp: Instant? = null
)
