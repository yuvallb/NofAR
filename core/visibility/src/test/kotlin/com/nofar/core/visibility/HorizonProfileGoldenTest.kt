package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import com.nofar.core.model.GeoMathBounds
import org.junit.Test

/**
 * Golden skyline fixtures for [HorizonProfileComputer] (H-P2-04) plus the tile-gap policy lock
 * (H-P2-05, pairs with H-P1-07). Uses deterministic synthetic [DemSampler]s so the goldens are
 * analytic (no checked-in binary fixtures) and any drift in the raycast / bulge math fails CI.
 */
class HorizonProfileGoldenTest {
    private val computer = HorizonProfileComputer()
    private val observerLat = 32.5
    private val observerLon = 35.5
    private val eyeM = 100.0 + AppConfig.EYE_HEIGHT_METERS

    private fun distanceFromObserverM(lat: Double, lon: Double): Double =
        GeoMathBounds.haversineDistanceM(observerLat, observerLon, lat, lon)

    // H-P2-04: flat, bulge-corrected terrain sits exactly on the true horizon in every bucket.
    @Test
    fun flatTerrain_golden_allBucketsOnHorizon() {
        val sampler = DemSampler { _, _ -> 100f }

        val profile = computer.sweep(observerLat, observerLon, eyeM, sampler, maxRadiusM = 15_000.0)

        assertThat(profile.elevationAnglesDeg.size).isEqualTo((360f / AppConfig.HORIZON_AZIMUTH_STEP_DEG).toInt())
        profile.elevationAnglesDeg.forEach { angle -> assertThat(angle).isWithin(0.05f).of(0f) }
    }

    // H-P2-04: a radially symmetric rising ramp produces an identical skyline angle in every azimuth
    // (symmetry golden). The absolute angle is locked per slope — it sits just above atan(slope) because
    // the shared earth-bulge term lifts far samples (H-P1-06). Steeper ground reads a higher golden.
    // Goldens captured from the current raycast + bulge math; changing them must be intentional.
    @Test
    fun radialRise_golden_isSymmetricAndSlopeLocked() {
        val gentle = sweepRadialRise(slope = 0.02)
        val steep = sweepRadialRise(slope = 0.05)

        val gentleFirst = gentle.elevationAnglesDeg.first()
        gentle.elevationAnglesDeg.forEach { angle -> assertThat(angle).isWithin(0.02f).of(gentleFirst) }

        assertThat(gentleFirst).isWithin(0.02f).of(1.1654f)
        assertThat(steep.elevationAnglesDeg.first()).isWithin(0.02f).of(2.8820f)
    }

    // H-P2-05: with a coverage hole the ray breaks before the far ridge → skyline stays flat. The exact
    // same ridge is clearly visible once the hole is filled. Locks the break-on-null policy (H-P1-07).
    @Test
    fun tileGap_golden_hidesRidge_whileOpenCoverageSeesIt() {
        val cutoffM = 3_000.0
        val holeEndM = 6_000.0
        val ridgeElevationM = 2_000f
        val hiddenSampler = ridgeSampler(cutoffM, holeEndM, ridgeElevationM, hole = true)
        val openSampler = ridgeSampler(cutoffM, holeEndM, ridgeElevationM, hole = false)

        val hidden = computer.sweep(observerLat, observerLon, eyeM, hiddenSampler, maxRadiusM = 15_000.0)
        val seen = computer.sweep(observerLat, observerLon, eyeM, openSampler, maxRadiusM = 15_000.0)

        hidden.elevationAnglesDeg.forEach { angle -> assertThat(angle).isLessThan(0.05f) }
        assertThat(seen.elevationAnglesDeg.maxOrNull()).isGreaterThan(2f)
    }

    private fun sweepRadialRise(slope: Double): HorizonProfile {
        val sampler =
            DemSampler { lat, lon ->
                (100.0 + slope * distanceFromObserverM(lat, lon)).toFloat()
            }
        return computer.sweep(observerLat, observerLon, eyeM, sampler, maxRadiusM = 15_000.0)
    }

    private fun ridgeSampler(cutoffM: Double, holeEndM: Double, ridgeElevationM: Float, hole: Boolean): DemSampler =
        DemSampler { lat, lon ->
            val distanceM = distanceFromObserverM(lat, lon)
            when {
                distanceM <= cutoffM + 1.0 -> 100f
                distanceM <= holeEndM -> if (hole) null else 100f
                else -> ridgeElevationM
            }
        }
}
