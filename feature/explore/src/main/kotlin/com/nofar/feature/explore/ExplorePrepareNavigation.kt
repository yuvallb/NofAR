package com.nofar.feature.explore

import com.nofar.core.data.usecase.QuickRegionProposal
import java.util.UUID

/** Navigation payload so Prepare owns network downloads (Explore stays offline). */
data class ExplorePrepareNavigation(
    val regionId: UUID? = null,
    val centerLat: Double,
    val centerLon: Double,
    val radiusM: Double,
    val name: String
) {
    companion object {
        fun fromProposal(proposal: QuickRegionProposal): ExplorePrepareNavigation = ExplorePrepareNavigation(
            regionId = proposal.existingRegionId,
            centerLat = proposal.centerLat,
            centerLon = proposal.centerLon,
            radiusM = proposal.radiusM,
            name = proposal.name
        )
    }
}
