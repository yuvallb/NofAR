package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import kotlin.math.min

/** Builds distance samples for long horizon / entity rays without exceeding the visibility budget. */
internal object RayDistanceSteps {
    fun horizonDistances(maxRadiusM: Double): DoubleArray {
        if (maxRadiusM <= 0.0) return doubleArrayOf(0.0)
        return steppedDistances(
            maxRadiusM = maxRadiusM,
            nearStepM = AppConfig.HORIZON_RAY_STEP_M,
            farStepM = AppConfig.HORIZON_FAR_RAY_STEP_M,
            nearEndM = AppConfig.HORIZON_NEAR_FIELD_END_M
        )
    }

    /**
     * Entity line-of-sight samples: fine step in the near field, coarser beyond, always including
     * the target distance so there is no unsampled gap before the endpoint.
     */
    fun entityRayDistances(totalDistanceM: Double, nearStepM: Double): DoubleArray {
        if (totalDistanceM <= 0.0) return doubleArrayOf(0.0)
        return steppedDistances(
            maxRadiusM = totalDistanceM,
            nearStepM = nearStepM,
            farStepM = AppConfig.HORIZON_FAR_RAY_STEP_M,
            nearEndM = AppConfig.HORIZON_NEAR_FIELD_END_M
        )
    }

    private fun steppedDistances(
        maxRadiusM: Double,
        nearStepM: Double,
        farStepM: Double,
        nearEndM: Double
    ): DoubleArray {
        val nearFieldEndM = min(nearEndM, maxRadiusM)
        val distances = ArrayList<Double>(64)
        distances += 0.0
        var distanceM = nearStepM
        while (distanceM <= nearFieldEndM) {
            distances += distanceM
            distanceM += nearStepM
        }
        if (maxRadiusM > nearFieldEndM) {
            var farDistanceM =
                if (distances.last() < nearFieldEndM) {
                    nearFieldEndM + farStepM
                } else {
                    distances.last() + farStepM
                }
            while (farDistanceM <= maxRadiusM) {
                if (farDistanceM > distances.last()) {
                    distances += farDistanceM
                }
                farDistanceM += farStepM
            }
        }
        if (distances.last() < maxRadiusM) {
            distances += maxRadiusM
        }
        return distances.toDoubleArray()
    }

    fun mapPreviewRadialStepM(maxEdgeM: Double): Double = if (maxEdgeM > AppConfig.MAP_PREVIEW_NEAR_FIELD_END_M) {
        AppConfig.MAP_PREVIEW_FAR_RADIAL_STEP_M
    } else {
        AppConfig.MAP_PREVIEW_RADIAL_STEP_M
    }
}
