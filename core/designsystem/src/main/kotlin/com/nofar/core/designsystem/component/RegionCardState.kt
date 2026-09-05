package com.nofar.core.designsystem.component

import com.nofar.core.model.CoverageSet
import java.time.Instant

data class CoverageSetCardState(
    val coverageSet: CoverageSet,
    val isYouAreHere: Boolean,
    val osmSizeBytes: Long = coverageSet.estimatedSizeBytes,
    val demSizeBytes: Long = 0L,
    val latestDemTimestamp: Instant? = null
)

/** @deprecated Use [CoverageSetCardState]. */
typealias RegionCardState = CoverageSetCardState
