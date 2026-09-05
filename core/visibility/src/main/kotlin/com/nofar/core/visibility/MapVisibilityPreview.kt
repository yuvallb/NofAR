package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import com.nofar.core.model.Region
import com.nofar.core.model.RegionBounds
import kotlin.math.floor

/**
 * Polar viewshed grid from an observer for expert virtual-location map preview.
 *
 * Rows are azimuth buckets ([azimuthStepDeg]); columns are radial steps of [radialStepM] out to
 * [regionEdgeMeters] for that bearing.
 */
data class MapVisibilityPreview(
    val observerLat: Double,
    val observerLon: Double,
    val azimuthStepDeg: Float,
    val radialStepM: Double,
    val regionEdgeMeters: FloatArray,
    val maxRadialCells: Int,
    private val clipRegions: List<Region> = emptyList(),
    private val stateCodes: ByteArray
) {
    fun maxRegionEdgeM(): Double = regionEdgeMeters.maxOrNull()?.toDouble() ?: 0.0

    fun regionEdgeM(azimuthDeg: Double): Double {
        val normalized = normalizeAzimuth(azimuthDeg)
        val indexPosition = normalized / azimuthStepDeg
        val lowerIndex = floor(indexPosition).toInt() % regionEdgeMeters.size
        val upperIndex = (lowerIndex + 1) % regionEdgeMeters.size
        val fraction = indexPosition - floor(indexPosition)
        return regionEdgeMeters[lowerIndex] * (1.0 - fraction) +
            regionEdgeMeters[upperIndex] * fraction
    }

    fun isInsideRegion(azimuthDeg: Double, distanceM: Double): Boolean {
        if (distanceM > regionEdgeM(azimuthDeg)) return false
        val bearingDeg = azimuthDeg
        val (lat, lon) = GeoMath.destinationPoint(observerLat, observerLon, bearingDeg, distanceM)
        return clipRegions.isEmpty() ||
            clipRegions.any { region -> RegionBounds.containsPoint(region, lat, lon) }
    }

    fun radialCellCount(azimuthIndex: Int): Int {
        val edgeM = regionEdgeMeters.getOrNull(azimuthIndex)?.toDouble() ?: 0.0
        return when {
            edgeM <= 0.0 -> 0
            else -> floor(edgeM / radialStepM).toInt().coerceAtLeast(0)
        }
    }

    fun cellState(azimuthIndex: Int, radialIndex: Int): MapVisibilityCellState {
        val inRange = azimuthIndex in regionEdgeMeters.indices && radialIndex in 0 until maxRadialCells
        val inRay = inRange && radialIndex < radialCellCount(azimuthIndex)
        return when {
            !inRay -> MapVisibilityCellState.UNKNOWN
            else -> MapVisibilityCellState.fromCode(stateCodes[azimuthIndex * maxRadialCells + radialIndex])
        }
    }

    /**
     * Nearest cell for a ground distance and bearing (degrees clockwise from north).
     * Inside the first radial step, returns [MapVisibilityCellState.VISIBLE].
     */
    fun sampleState(azimuthDeg: Double, distanceM: Double): MapVisibilityCellState {
        if (distanceM <= 0.0) {
            return MapVisibilityCellState.VISIBLE
        }
        val normalized = normalizeAzimuth(azimuthDeg)
        val azimuthIndex =
            (normalized / azimuthStepDeg).toInt().coerceIn(0, regionEdgeMeters.lastIndex)
        return when {
            !isInsideRegion(azimuthDeg, distanceM) -> MapVisibilityCellState.UNKNOWN
            distanceM < radialStepM -> MapVisibilityCellState.VISIBLE
            else -> cellState(azimuthIndex, (distanceM / radialStepM).toInt() - 1)
        }
    }

    internal fun setCellState(azimuthIndex: Int, radialIndex: Int, state: MapVisibilityCellState) {
        stateCodes[azimuthIndex * maxRadialCells + radialIndex] = state.code
    }

    internal fun stateCodes(): ByteArray = stateCodes

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MapVisibilityPreview) return false
        return observerLat == other.observerLat &&
            observerLon == other.observerLon &&
            azimuthStepDeg == other.azimuthStepDeg &&
            radialStepM == other.radialStepM &&
            maxRadialCells == other.maxRadialCells &&
            regionEdgeMeters.contentEquals(other.regionEdgeMeters) &&
            clipRegions == other.clipRegions &&
            stateCodes.contentEquals(other.stateCodes)
    }

    override fun hashCode(): Int {
        var result = observerLat.hashCode()
        result = 31 * result + observerLon.hashCode()
        result = 31 * result + azimuthStepDeg.hashCode()
        result = 31 * result + radialStepM.hashCode()
        result = 31 * result + maxRadialCells
        result = 31 * result + regionEdgeMeters.contentHashCode()
        result = 31 * result + clipRegions.hashCode()
        result = 31 * result + stateCodes.contentHashCode()
        return result
    }

    companion object {
        private fun normalizeAzimuth(azimuthDeg: Double): Double {
            var normalized = azimuthDeg % 360.0
            if (normalized < 0.0) normalized += 360.0
            return normalized
        }

        fun createEmpty(
            observerLat: Double,
            observerLon: Double,
            azimuthStepDeg: Float = AppConfig.MAP_PREVIEW_AZIMUTH_STEP_DEG,
            radialStepM: Double = AppConfig.MAP_PREVIEW_RADIAL_STEP_M,
            regionEdgeMeters: FloatArray,
            maxRadialCells: Int,
            clipRegions: List<Region> = emptyList()
        ): MapVisibilityPreview = MapVisibilityPreview(
            observerLat = observerLat,
            observerLon = observerLon,
            azimuthStepDeg = azimuthStepDeg,
            radialStepM = radialStepM,
            regionEdgeMeters = regionEdgeMeters,
            maxRadialCells = maxRadialCells,
            clipRegions = clipRegions,
            stateCodes = ByteArray(regionEdgeMeters.size * maxRadialCells) {
                MapVisibilityCellState.UNKNOWN.code
            }
        )
    }
}
