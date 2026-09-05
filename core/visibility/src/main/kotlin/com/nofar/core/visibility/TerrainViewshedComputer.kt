package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import com.nofar.core.model.Region
import javax.inject.Inject
import kotlin.math.floor

/**
 * Running-horizon viewshed for the expert virtual-location map preview only.
 * Explore skyline uses [HorizonProfileComputer] separately.
 */
@Suppress("ReturnCount", "LongParameterList")
class TerrainViewshedComputer
@Inject
constructor() {
    fun compute(
        observerLat: Double,
        observerLon: Double,
        observerEyeM: Double,
        clipRegions: List<Region>,
        sampler: DemSampler,
        isCancelled: () -> Boolean = { false }
    ): MapVisibilityPreview? {
        if (clipRegions.isEmpty()) return null
        if (!RegionRayExtent.observerInsideAnyRegion(clipRegions, observerLat, observerLon)) {
            return null
        }
        return buildPreview(
            observerLat = observerLat,
            observerLon = observerLon,
            observerEyeM = observerEyeM,
            clipRegions = clipRegions,
            sampler = sampler,
            isCancelled = isCancelled
        )
    }

    private fun buildPreview(
        observerLat: Double,
        observerLon: Double,
        observerEyeM: Double,
        clipRegions: List<Region>,
        sampler: DemSampler,
        isCancelled: () -> Boolean
    ): MapVisibilityPreview? {
        val azimuthStepDeg = AppConfig.MAP_PREVIEW_AZIMUTH_STEP_DEG
        val azimuthCount = (360f / azimuthStepDeg).toInt()
        val regionEdgeMeters =
            computeRegionEdgeMeters(
                clipRegions = clipRegions,
                observerLat = observerLat,
                observerLon = observerLon,
                azimuthStepDeg = azimuthStepDeg,
                azimuthCount = azimuthCount,
                isCancelled = isCancelled
            ) ?: return null
        val maxEdgeM = regionEdgeMeters.maxOrNull()?.toDouble() ?: 0.0
        val radialStepM = RayDistanceSteps.mapPreviewRadialStepM(maxEdgeM)
        val maxRadialCells =
            regionEdgeMeters.maxOfOrNull { edge ->
                if (edge <= 0f) 0 else floor(edge / radialStepM).toInt()
            }?.coerceAtLeast(0) ?: 0
        val preview =
            MapVisibilityPreview.createEmpty(
                observerLat = observerLat,
                observerLon = observerLon,
                azimuthStepDeg = azimuthStepDeg,
                radialStepM = radialStepM,
                regionEdgeMeters = regionEdgeMeters,
                maxRadialCells = maxOf(maxRadialCells, 1),
                clipRegions = clipRegions
            )
        val completed =
            fillViewshedGrid(
                preview = preview,
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = observerEyeM,
                clipRegions = clipRegions,
                azimuthStepDeg = azimuthStepDeg,
                radialStepM = radialStepM,
                azimuthCount = azimuthCount,
                sampler = sampler,
                isCancelled = isCancelled
            )
        return if (completed) preview else null
    }

    private fun computeRegionEdgeMeters(
        clipRegions: List<Region>,
        observerLat: Double,
        observerLon: Double,
        azimuthStepDeg: Float,
        azimuthCount: Int,
        isCancelled: () -> Boolean
    ): FloatArray? {
        val regionEdgeMeters = FloatArray(azimuthCount)
        for (azimuthIndex in 0 until azimuthCount) {
            if (isCancelled()) return null
            val bearingDeg = azimuthIndex * azimuthStepDeg
            val edgeM =
                RegionRayExtent.maxDistanceInsideAnyRegionM(
                    regions = clipRegions,
                    observerLat = observerLat,
                    observerLon = observerLon,
                    bearingDeg = bearingDeg.toDouble()
                )
            regionEdgeMeters[azimuthIndex] = edgeM.toFloat()
        }
        return regionEdgeMeters
    }

    private fun fillViewshedGrid(
        preview: MapVisibilityPreview,
        observerLat: Double,
        observerLon: Double,
        observerEyeM: Double,
        clipRegions: List<Region>,
        azimuthStepDeg: Float,
        radialStepM: Double,
        azimuthCount: Int,
        sampler: DemSampler,
        isCancelled: () -> Boolean
    ): Boolean {
        val toleranceM = AppConfig.MAP_PREVIEW_OCCLUSION_TOLERANCE_M
        for (azimuthIndex in 0 until azimuthCount) {
            val rayCompleted =
                fillAzimuthRay(
                    preview = preview,
                    azimuthIndex = azimuthIndex,
                    observerLat = observerLat,
                    observerLon = observerLon,
                    observerEyeM = observerEyeM,
                    clipRegions = clipRegions,
                    azimuthStepDeg = azimuthStepDeg,
                    radialStepM = radialStepM,
                    toleranceM = toleranceM,
                    sampler = sampler,
                    isCancelled = isCancelled
                )
            if (!rayCompleted) return false
        }
        return true
    }

    /** @return false when cancelled; true when the ray finished (including empty/missing DEM). */
    private fun fillAzimuthRay(
        preview: MapVisibilityPreview,
        azimuthIndex: Int,
        observerLat: Double,
        observerLon: Double,
        observerEyeM: Double,
        clipRegions: List<Region>,
        azimuthStepDeg: Float,
        radialStepM: Double,
        toleranceM: Double,
        sampler: DemSampler,
        isCancelled: () -> Boolean
    ): Boolean {
        if (isCancelled()) return false
        val bearingDeg = azimuthIndex * azimuthStepDeg.toDouble()
        val rayLengthM = preview.regionEdgeMeters[azimuthIndex].toDouble()
        if (rayLengthM <= 0.0) return true
        val radialCells = floor(rayLengthM / radialStepM).toInt()
        var maxSlope = Double.NEGATIVE_INFINITY
        for (radialIndex in 0 until radialCells) {
            if (isCancelled()) return false
            maxSlope =
                marchRadialCell(
                    preview = preview,
                    azimuthIndex = azimuthIndex,
                    radialIndex = radialIndex,
                    radialCells = radialCells,
                    observerLat = observerLat,
                    observerLon = observerLon,
                    bearingDeg = bearingDeg,
                    observerEyeM = observerEyeM,
                    clipRegions = clipRegions,
                    rayLengthM = rayLengthM,
                    radialStepM = radialStepM,
                    toleranceM = toleranceM,
                    sampler = sampler,
                    maxSlope = maxSlope
                ) ?: return true
        }
        return true
    }

    private fun marchRadialCell(
        preview: MapVisibilityPreview,
        azimuthIndex: Int,
        radialIndex: Int,
        radialCells: Int,
        observerLat: Double,
        observerLon: Double,
        bearingDeg: Double,
        observerEyeM: Double,
        clipRegions: List<Region>,
        rayLengthM: Double,
        radialStepM: Double,
        toleranceM: Double,
        sampler: DemSampler,
        maxSlope: Double
    ): Double? {
        val distanceM = (radialIndex + 1) * radialStepM
        val (lat, lon) = GeoMath.destinationPoint(observerLat, observerLon, bearingDeg, distanceM)
        val insideClip = RegionRayExtent.sampleInsideAnyRegion(clipRegions, lat, lon)
        val terrainM = sampler.elevationAt(lat, lon)?.toDouble()
        if (terrainM == null) {
            if (insideClip) {
                for (remaining in radialIndex until radialCells) {
                    preview.setCellState(azimuthIndex, remaining, MapVisibilityCellState.UNKNOWN)
                }
                return null
            }
            return maxSlope
        }
        if (!insideClip) {
            return maxSlope
        }
        val bulgeM = GeoMath.earthBulgeM(distanceM, rayLengthM)
        val surfaceM = terrainM + bulgeM
        val horizonElevationM = observerEyeM + maxSlope * distanceM
        val visible = !maxSlope.isFinite() || surfaceM + toleranceM >= horizonElevationM
        preview.setCellState(
            azimuthIndex,
            radialIndex,
            if (visible) MapVisibilityCellState.VISIBLE else MapVisibilityCellState.BLOCKED
        )
        val slope = (surfaceM - observerEyeM) / distanceM
        return if (slope > maxSlope) slope else maxSlope
    }
}
