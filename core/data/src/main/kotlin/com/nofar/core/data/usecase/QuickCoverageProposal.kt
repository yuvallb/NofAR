package com.nofar.core.data.usecase

import java.util.UUID

data class QuickCoverageProposal(
    val centerLat: Double,
    val centerLon: Double,
    val cellIds: List<String>,
    val name: String,
    val estimateBytes: Long,
    val demTileCount: Int,
    val existingCoverageSetId: UUID? = null,
    val packCacheRaiseBytes: Long? = null
)
