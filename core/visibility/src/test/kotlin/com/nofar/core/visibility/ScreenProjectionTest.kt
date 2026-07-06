package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScreenProjectionTest {
    @Test
    fun entityAtBearingZeroWithDeviceFacingNorth_isCenteredOnX() {
        val projection =
            ScreenProjector.projectEntityToScreen(
                bearingDeg = 0.0,
                elevationAngleDeg = 0.0,
                trueAzimuthDeg = 0f,
                pitchDeg = 0f,
                horizontalFovDeg = 60f,
                verticalFovDeg = 45f,
                screenWidthPx = 1080f,
                screenHeightPx = 1920f
            )

        assertThat(projection).isNotNull()
        assertThat(projection!!.anchorXPx).isWithin(0.1f).of(540f)
        assertThat(projection.anchorYPx).isWithin(0.1f).of(960f)
    }

    @Test
    fun entityOutsideHorizontalFov_isNotDrawn() {
        val projection =
            ScreenProjector.projectEntityToScreen(
                bearingDeg = 90.0,
                elevationAngleDeg = 0.0,
                trueAzimuthDeg = 0f,
                pitchDeg = 0f,
                horizontalFovDeg = 60f,
                verticalFovDeg = 45f,
                screenWidthPx = 1080f,
                screenHeightPx = 1920f
            )

        assertThat(projection).isNull()
    }

    @Test
    fun normalizeHeadingDelta_wrapsToSignedRange() {
        assertThat(ScreenProjector.normalizeHeadingDelta(10.0, 350f)).isWithin(0.001).of(20.0)
        assertThat(ScreenProjector.normalizeHeadingDelta(350.0, 10f)).isWithin(0.001).of(-20.0)
    }
}
