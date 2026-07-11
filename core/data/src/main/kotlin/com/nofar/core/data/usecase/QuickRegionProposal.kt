package com.nofar.core.data.usecase

import java.util.UUID

data class QuickRegionProposal(
    val centerLat: Double,
    val centerLon: Double,
    val radiusM: Double,
    val name: String,
    val estimateBytes: Long,
    val demTileCount: Int,
    val existingRegionId: UUID? = null
)
