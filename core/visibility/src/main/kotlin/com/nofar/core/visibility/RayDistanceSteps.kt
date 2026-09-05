package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import kotlin.math.min

/** Builds distance samples for long horizon rays without exceeding the visibility budget. */
internal object RayDistanceSteps {
    fun horizonDistances(maxRadiusM: Double): DoubleArray {
        if (maxRadiusM <= 0.0) return doubleArrayOf(0.0)
        val nearStepM = AppConfig.HORIZON_RAY_STEP_M
        val farStepM = AppConfig.HORIZON_FAR_RAY_STEP_M
        val nearEndM = min(AppConfig.HORIZON_NEAR_FIELD_END_M, maxRadiusM)
        val distances = ArrayList<Double>(64)
        distances += 0.0
        var distanceM = nearStepM
        while (distanceM <= nearEndM) {
            distances += distanceM
            distanceM += nearStepM
        }
        if (maxRadiusM > nearEndM) {
            var farDistanceM =
                if (distances.last() < nearEndM) {
                    nearEndM + farStepM
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
