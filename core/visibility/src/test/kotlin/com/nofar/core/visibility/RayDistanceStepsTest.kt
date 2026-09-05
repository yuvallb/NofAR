package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import org.junit.Test

class RayDistanceStepsTest {
    @Test
    fun horizonDistances_shortReach_usesNearStepOnly() {
        val distances = RayDistanceSteps.horizonDistances(maxRadiusM = 5_000.0)

        assertThat(distances.first()).isWithin(0.001).of(0.0)
        assertThat(distances.last()).isWithin(0.001).of(5_000.0)
        assertThat(distances[1] - distances[0]).isWithin(0.001).of(AppConfig.HORIZON_RAY_STEP_M)
    }

    @Test
    fun horizonDistances_longReach_coarsensBeyondNearField() {
        val distances = RayDistanceSteps.horizonDistances(maxRadiusM = 100_000.0)

        assertThat(distances.last()).isWithin(0.001).of(100_000.0)
        var hasFarStep = false
        for (index in 1 until distances.size) {
            if (distances[index] - distances[index - 1] == AppConfig.HORIZON_FAR_RAY_STEP_M) {
                hasFarStep = true
                break
            }
        }
        assertThat(hasFarStep).isEqualTo(true)
    }

    @Test
    fun mapPreviewRadialStepM_switchesForLargeExtent() {
        assertThat(RayDistanceSteps.mapPreviewRadialStepM(10_000.0))
            .isEqualTo(AppConfig.MAP_PREVIEW_RADIAL_STEP_M)
        assertThat(RayDistanceSteps.mapPreviewRadialStepM(50_000.0))
            .isEqualTo(AppConfig.MAP_PREVIEW_FAR_RADIAL_STEP_M)
    }
}
