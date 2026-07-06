package com.nofar.core.visibility

import javax.inject.Inject

/**
 * Test / stub implementation that marks every candidate as visible without raycasting.
 */
class NoOpVisibilityEngine @Inject constructor() : VisibilityEngine {
    override suspend fun computeVisibleEntities(request: VisibilityRequest): VisibilityResult {
        val entities =
            request.candidates.map { candidate ->
                VisibleEntity(
                    bearingDeg = candidate.bearingDeg,
                    distanceM = candidate.distanceM,
                    elevationAngleDeg = 0.0,
                    entity = candidate.entity
                )
            }
        return VisibilityResult(
            entities = entities,
            computationTimeMs = 0L,
            warnings = request.warnings
        )
    }
}
