package com.nofar.core.visibility

import com.nofar.core.common.DispatcherProvider
import javax.inject.Inject
import kotlinx.coroutines.withContext

class DemRaycastVisibilityEngine
@Inject
constructor(private val dispatchers: DispatcherProvider) :
    VisibilityEngine {
    private val rayMarcher = TerrainRayMarcher()

    override suspend fun computeVisibleEntities(request: VisibilityRequest): VisibilityResult =
        withContext(dispatchers.default) {
            val startNanos = System.nanoTime()
            val sampler = DemElevationSampler(request.demReaders)
            val observerEyeM = request.observerElevationM + request.eyeHeightM

            val visible =
                request.candidates.mapNotNull { candidate ->
                    toVisibleEntity(request, candidate, observerEyeM, sampler)
                }

            VisibilityResult(
                entities = visible,
                computationTimeMs = (System.nanoTime() - startNanos) / 1_000_000L,
                warnings = request.warnings
            )
        }

    private fun toVisibleEntity(
        request: VisibilityRequest,
        candidate: VisibilityCandidate,
        observerEyeM: Double,
        sampler: DemElevationSampler
    ): VisibleEntity? {
        val targetElevationM = resolveTargetElevationM(candidate, sampler)
        val isVisible =
            candidate.distanceM <= request.radiusM &&
                rayMarcher.isTargetVisible(
                    observerLat = request.observerLat,
                    observerLon = request.observerLon,
                    targetLat = candidate.entity.lat,
                    targetLon = candidate.entity.lon,
                    totalDistanceM = candidate.distanceM,
                    observerEyeM = observerEyeM,
                    targetElevationM = targetElevationM,
                    rayStepM = request.rayStepM,
                    sampler = sampler
                )
        if (!isVisible) return null

        return VisibleEntity(
            bearingDeg = candidate.bearingDeg,
            distanceM = candidate.distanceM,
            elevationAngleDeg =
            GeoMath.elevationAngleDeg(
                observerEyeM = observerEyeM,
                targetElevationM = targetElevationM,
                horizontalDistanceM = candidate.distanceM
            ),
            entity = candidate.entity
        )
    }

    private fun resolveTargetElevationM(candidate: VisibilityCandidate, sampler: DemElevationSampler): Double {
        candidate.entity.elevation?.let { return it }
        return sampler.elevationAt(candidate.entity.lat, candidate.entity.lon)?.toDouble() ?: 0.0
    }
}
