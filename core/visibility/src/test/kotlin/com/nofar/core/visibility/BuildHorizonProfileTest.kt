package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import org.junit.Test

/**
 * H-P1-16 / H-P1-11: prove the skyline profile is actually attached to a pass (fails if someone drops
 * the sweep) and is skipped entirely when the outline preference is off.
 */
class BuildHorizonProfileTest {
    private val computer = HorizonProfileComputer()
    private val flatSampler = DemSampler { _, _ -> 100f }

    @Test
    fun computeDisabled_returnsNull() {
        val profile =
            buildHorizonProfile(
                horizonProfileComputer = computer,
                computeHorizonProfile = false,
                observerLat = 32.5,
                observerLon = 35.5,
                observerEyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS,
                sampler = flatSampler,
                maxRadiusM = AppConfig.HORIZON_MAX_RADIUS_M
            )

        assertThat(profile).isNull()
    }

    @Test
    fun computeEnabled_attachesFullBucketFlatProfile() {
        val profile =
            buildHorizonProfile(
                horizonProfileComputer = computer,
                computeHorizonProfile = true,
                observerLat = 32.5,
                observerLon = 35.5,
                observerEyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS,
                sampler = flatSampler,
                maxRadiusM = AppConfig.HORIZON_MAX_RADIUS_M
            )

        assertThat(profile).isNotNull()
        val bucketCount = (360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt()
        assertThat(profile!!.elevationAnglesDeg.size).isEqualTo(bucketCount)
        profile.elevationAnglesDeg.forEach { angle ->
            assertThat(kotlin.math.abs(angle)).isLessThan(1f)
        }
    }
}
