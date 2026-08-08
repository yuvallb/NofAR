package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import javax.inject.Inject
import kotlin.math.floor

/**
 * Running-horizon viewshed for the expert virtual-location map preview only.
 * Explore skyline uses [HorizonProfileComputer] separately.
 */
@Suppress("ReturnCount")
class TerrainViewshedComputer
@Inject
constructor() {
    fun compute(
        observerLat: Double,
        observerLon: Double,
        observerEyeM: Double,
        clipRegion: Region,
        sampler: DemSampler,
        isCancelled: () -> Boolean = { false }
    ): MapVisibilityPreview? = if (!RegionBounds.containsPoint(clipRegion, observerLat, observerLon)) {
        null
    } else {
        buildPreview(
            observerLat = observerLat,
            observerLon = observerLon,
            observerEyeM = observerEyeM,
            clipRegion = clipRegion,
            sampler = sampler,
            isCancelled = isCancelled
        )
    }

    private fun buildPreview(
        observerLat: Double,
        observerLon: Double,
        observerEyeM: Double,
        clipRegion: Region,
        sampler: DemSampler,
        isCancelled: () -> Boolean
    ): MapVisibilityPreview? {
        val azimuthStepDeg = AppConfig.MAP_PREVIEW_AZIMUTH_STEP_DEG
        val radialStepM = AppConfig.MAP_PREVIEW_RADIAL_STEP_M
        val azimuthCount = (360f / azimuthStepDeg).toInt()
        val regionEdgeMeters =
            computeRegionEdgeMeters(
                clipRegion = clipRegion,
                observerLat = observerLat,
                observerLon = observerLon,
                azimuthStepDeg = azimuthStepDeg,
                azimuthCount = azimuthCount,
                isCancelled = isCancelled
            ) ?: return null
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
                maxRadialCells = maxOf(maxRadialCells, 1)
            )
        val completed =
            fillViewshedGrid(
                preview = preview,
                observerLat = observerLat,
                observerLon = observerLon,
                observerEyeM = observerEyeM,
                azimuthStepDeg = azimuthStepDeg,
                radialStepM = radialStepM,
                azimuthCount = azimuthCount,
                sampler = sampler,
                isCancelled = isCancelled
            )
        return if (completed) preview else null
    }

    private fun computeRegionEdgeMeters(
        clipRegion: Region,
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
                RegionRayExtent.maxDistanceInsideRegionM(
                    region = clipRegion,
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
        rayLengthM: Double,
        radialStepM: Double,
        toleranceM: Double,
        sampler: DemSampler,
        maxSlope: Double
    ): Double? {
        val distanceM = (radialIndex + 1) * radialStepM
        val (lat, lon) = GeoMath.destinationPoint(observerLat, observerLon, bearingDeg, distanceM)
        val terrainM = sampler.elevationAt(lat, lon)?.toDouble()
        if (terrainM == null) {
            for (remaining in radialIndex until radialCells) {
                preview.setCellState(azimuthIndex, remaining, MapVisibilityCellState.UNKNOWN)
            }
            return null
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
