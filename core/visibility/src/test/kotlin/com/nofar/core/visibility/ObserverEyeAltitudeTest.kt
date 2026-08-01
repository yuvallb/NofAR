package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import org.junit.Test

class ObserverEyeAltitudeTest {
    @Test
    fun demKnown_usesDemAlignedEye_evenWhenGpsAgrees() {
        val eye = ObserverEyeAltitude.resolve(observerElevationM = 81.0, demGroundM = 80.0f)

        assertThat(eye.eyeM).isWithin(0.01).of(80.0 + AppConfig.EYE_HEIGHT_METERS)
        assertThat(eye.source).isEqualTo(ObserverEyeSource.DEM)
    }

    @Test
    fun demKnown_usesDemAlignedEye_whenGpsBelowDem() {
        val eye = ObserverEyeAltitude.resolve(observerElevationM = 81.0, demGroundM = 120.0f)

        assertThat(eye.eyeM).isWithin(0.01).of(120.0 + AppConfig.EYE_HEIGHT_METERS)
        assertThat(eye.source).isEqualTo(ObserverEyeSource.DEM)
    }

    @Test
    fun demKnown_usesDemAlignedEye_whenGpsAboveDem() {
        val eye = ObserverEyeAltitude.resolve(observerElevationM = 120.0, demGroundM = 80.0f)

        assertThat(eye.eyeM).isWithin(0.01).of(80.0 + AppConfig.EYE_HEIGHT_METERS)
        assertThat(eye.source).isEqualTo(ObserverEyeSource.DEM)
    }

    @Test
    fun missingDem_fallsBackToGpsEye() {
        val eye = ObserverEyeAltitude.resolve(observerElevationM = 81.0, demGroundM = null)

        assertThat(eye.eyeM).isWithin(0.01).of(81.0 + AppConfig.EYE_HEIGHT_METERS)
        assertThat(eye.source).isEqualTo(ObserverEyeSource.GPS)
    }

    // H-P0-03: horizon uses the DEM-aligned eye while entity line-of-sight keeps the GPS-priority
    // observer elevation (Requirements §4.6). This documents the resulting vertical Δ under a GPS/DEM
    // offset so a future product decision to unify them is a conscious change, not an accident.
    @Test
    fun horizonEyeAndEntityEye_divergeByGpsDemOffset() {
        val gpsElevationM = 105.0
        val demGroundM = 120.0f
        val horizonEyeM = ObserverEyeAltitude.resolve(gpsElevationM, demGroundM).eyeM
        // DemRaycastVisibilityEngine computes observerEyeM = observerElevationM + eyeHeightM from the
        // GPS-priority elevation supplied by ObserverElevationResolver.
        val entityEyeM = gpsElevationM + AppConfig.EYE_HEIGHT_METERS

        assertThat(horizonEyeM - entityEyeM).isWithin(0.01).of(demGroundM - gpsElevationM)
    }
}
