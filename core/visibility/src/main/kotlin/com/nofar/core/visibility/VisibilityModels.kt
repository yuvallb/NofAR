package com.nofar.core.visibility

import com.nofar.core.data.dem.DemTileReader
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.ResolutionLevel
import java.util.UUID

data class VisibilityCandidate(val entity: GeoEntity, val bearingDeg: Double, val distanceM: Double)

data class VisibilityRequest(
    val observerLat: Double,
    val observerLon: Double,
    val observerElevationM: Double,
    val eyeHeightM: Double,
    val regionId: UUID,
    val radiusM: Double,
    val resolutionLevel: ResolutionLevel,
    val demReaders: Map<String, DemTileReader>,
    val candidates: List<VisibilityCandidate>,
    val rayStepM: Double,
    val warnings: Set<VisibilityWarning> = emptySet()
)

data class VisibleEntity(
    val bearingDeg: Double,
    val distanceM: Double,
    val elevationAngleDeg: Double,
    val entity: GeoEntity
)

data class VisibilityResult(
    val entities: List<VisibleEntity>,
    val computationTimeMs: Long,
    val warnings: Set<VisibilityWarning> = emptySet(),
    val horizonProfile: HorizonProfile? = null
)

enum class VisibilityWarning {
    OBSERVER_ELEVATION_FALLBACK_SEA_LEVEL,
    OBSERVER_ELEVATION_FROM_DEM,
    DEM_TILE_MISSING,
    CANDIDATE_CAP_EXCEEDED
}
