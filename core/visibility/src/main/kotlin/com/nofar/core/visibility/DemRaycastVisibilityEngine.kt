package com.nofar.core.visibility

import com.nofar.core.common.DispatcherProvider
import javax.inject.Inject
import kotlin.math.max
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
        val footprintRadiusM = candidate.entity.footprintRadiusM ?: 0.0
        val nearEdgeDistanceM =
            if (footprintRadiusM > 0.0) {
                max(candidate.distanceM - footprintRadiusM, 0.0)
            } else {
                candidate.distanceM
            }
        val rayDistanceM = if (footprintRadiusM > 0.0) nearEdgeDistanceM else candidate.distanceM
        val (targetLat, targetLon) =
            if (footprintRadiusM > 0.0 && nearEdgeDistanceM < candidate.distanceM) {
                GeoMath.destinationPoint(
                    request.observerLat,
                    request.observerLon,
                    candidate.bearingDeg,
                    nearEdgeDistanceM
                )
            } else {
                candidate.entity.lat to candidate.entity.lon
            }
        val targetElevationM = resolveTargetElevationM(candidate, sampler, targetLat, targetLon)
        val isVisible =
            rayDistanceM <= request.radiusM &&
                rayMarcher.isTargetVisible(
                    observerLat = request.observerLat,
                    observerLon = request.observerLon,
                    targetLat = targetLat,
                    targetLon = targetLon,
                    totalDistanceM = rayDistanceM,
                    observerEyeM = observerEyeM,
                    targetElevationM = targetElevationM,
                    rayStepM = request.rayStepM,
                    sampler = sampler
                )
        if (!isVisible) return null

        val horizontalDistanceForElevation = rayDistanceM.coerceAtLeast(0.001)
        return VisibleEntity(
            bearingDeg = candidate.bearingDeg,
            distanceM = candidate.distanceM,
            elevationAngleDeg =
            GeoMath.elevationAngleDeg(
                observerEyeM = observerEyeM,
                targetElevationM = targetElevationM,
                horizontalDistanceM = horizontalDistanceForElevation
            ),
            entity = candidate.entity,
            nearEdgeDistanceM = nearEdgeDistanceM
        )
    }

    private fun resolveTargetElevationM(
        candidate: VisibilityCandidate,
        sampler: DemElevationSampler,
        targetLat: Double,
        targetLon: Double
    ): Double {
        candidate.entity.elevation?.let { elevation ->
            if (candidate.entity.footprintRadiusM == null) return elevation
        }
        return sampler.elevationAt(targetLat, targetLon)?.toDouble()
            ?: candidate.entity.elevation
            ?: 0.0
    }
}
