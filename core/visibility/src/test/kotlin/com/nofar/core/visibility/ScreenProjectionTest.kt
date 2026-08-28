package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScreenProjectionTest {
    @Test
    fun orientedForScreen_landscape_keepsSensorWidthAsHorizontalAndCropsVertical() {
        val fov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f)
        val oriented = fov.orientedForScreen(screenWidthPx = 1920f, screenHeightPx = 1080f)
        // Landscape: sensor width FOV stays horizontal. 16:9 view is wider than 4:3 image, so
        // FILL_CENTER crops top/bottom and narrows vertical FOV.
        assertThat(oriented.horizontalDeg).isWithin(0.1f).of(60f)
        assertThat(oriented.verticalDeg).isWithin(0.1f).of(36.0f)
    }

    @Test
    fun orientedForScreen_portrait_swapsAxesAndCropsHorizontal() {
        val fov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f)
        val oriented = fov.orientedForScreen(screenWidthPx = 1080f, screenHeightPx = 1920f)
        // Portrait: sensor height FOV becomes horizontal, then FILL_CENTER crops the sides.
        assertThat(oriented.horizontalDeg).isWithin(0.1f).of(36.0f)
        assertThat(oriented.verticalDeg).isWithin(0.1f).of(60f)
    }

    @Test
    fun zoomed_oneX_isIdentity() {
        val fov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f)
        val zoomed = fov.zoomed(1f)
        assertThat(zoomed.horizontalDeg).isWithin(0.001f).of(60f)
        assertThat(zoomed.verticalDeg).isWithin(0.001f).of(45f)
    }

    @Test
    fun zoomed_twoX_narrowsHorizontalFov() {
        val fov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f)
        val zoomed = fov.zoomed(2f)
        assertThat(zoomed.horizontalDeg).isWithin(0.1f).of(32.2f)
        assertThat(zoomed.verticalDeg).isWithin(0.1f).of(23.5f)
    }

    @Test
    fun zoomed_belowOneX_widensFov() {
        val fov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f)
        val zoomed = fov.zoomed(0.5f)
        assertThat(zoomed.horizontalDeg).isGreaterThan(60f)
    }

    @Test
    fun zoomed_compositionWithOrientedForScreen_isOrderIndependent() {
        val fov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f)
        val zoomThenOrient =
            fov.zoomed(2f).orientedForScreen(screenWidthPx = 1920f, screenHeightPx = 1080f)
        val orientThenZoom =
            fov.orientedForScreen(screenWidthPx = 1920f, screenHeightPx = 1080f).zoomed(2f)
        assertThat(zoomThenOrient.horizontalDeg).isWithin(0.001f).of(orientThenZoom.horizontalDeg)
        assertThat(zoomThenOrient.verticalDeg).isWithin(0.001f).of(orientThenZoom.verticalDeg)
    }

    @Test
    fun zoomedFov_movesLabelFurtherFromCenter() {
        val baseFov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f)
        val at1x =
            ScreenProjector.projectEntityToScreen(
                bearingDeg = 10.0,
                elevationAngleDeg = 0.0,
                trueAzimuthDeg = 0f,
                cameraElevationDeg = 0f,
                horizontalFovDeg = baseFov.horizontalDeg,
                verticalFovDeg = baseFov.verticalDeg,
                screenWidthPx = 1080f,
                screenHeightPx = 1920f
            )
        val zoomedFov = baseFov.zoomed(3f)
        val at3x =
            ScreenProjector.projectEntityToScreen(
                bearingDeg = 10.0,
                elevationAngleDeg = 0.0,
                trueAzimuthDeg = 0f,
                cameraElevationDeg = 0f,
                horizontalFovDeg = zoomedFov.horizontalDeg,
                verticalFovDeg = zoomedFov.verticalDeg,
                screenWidthPx = 1080f,
                screenHeightPx = 1920f
            )

        assertThat(at1x).isNotNull()
        assertThat(at3x).isNotNull()
        assertThat(kotlin.math.abs(at3x!!.anchorXPx - 540f)).isGreaterThan(kotlin.math.abs(at1x!!.anchorXPx - 540f))
    }

    @Test
    fun zoomedFov_cullsEntitiesOutsideNarrowedFrustum() {
        val baseFov = CameraFieldOfView(horizontalDeg = 60f, verticalDeg = 45f)
        val zoomedFov = baseFov.zoomed(3f)
        val projection =
            ScreenProjector.projectEntityToScreen(
                bearingDeg = 20.0,
                elevationAngleDeg = 0.0,
                trueAzimuthDeg = 0f,
                cameraElevationDeg = 0f,
                horizontalFovDeg = zoomedFov.horizontalDeg,
                verticalFovDeg = zoomedFov.verticalDeg,
                screenWidthPx = 1080f,
                screenHeightPx = 1920f
            )

        assertThat(projection).isNull()
    }

    @Test
    fun entityAtBearingZeroWithDeviceFacingNorth_isCenteredOnX() {
        val projection =
            ScreenProjector.projectEntityToScreen(
                bearingDeg = 0.0,
                elevationAngleDeg = 0.0,
                trueAzimuthDeg = 0f,
                cameraElevationDeg = 0f,
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
                cameraElevationDeg = 0f,
                horizontalFovDeg = 60f,
                verticalFovDeg = 45f,
                screenWidthPx = 1080f,
                screenHeightPx = 1920f
            )

        assertThat(projection).isNull()
    }

    @Test
    fun tiltingCameraUp_movesLabelDownOnScreen() {
        val level =
            ScreenProjector.projectEntityToScreen(
                bearingDeg = 0.0,
                elevationAngleDeg = 10.0,
                trueAzimuthDeg = 0f,
                cameraElevationDeg = 0f,
                horizontalFovDeg = 60f,
                verticalFovDeg = 45f,
                screenWidthPx = 1080f,
                screenHeightPx = 1920f
            )
        val tiltedUp =
            ScreenProjector.projectEntityToScreen(
                bearingDeg = 0.0,
                elevationAngleDeg = 10.0,
                trueAzimuthDeg = 0f,
                cameraElevationDeg = 10f,
                horizontalFovDeg = 60f,
                verticalFovDeg = 45f,
                screenWidthPx = 1080f,
                screenHeightPx = 1920f
            )

        assertThat(level).isNotNull()
        assertThat(tiltedUp).isNotNull()
        assertThat(tiltedUp!!.anchorYPx).isGreaterThan(level!!.anchorYPx)
        assertThat(tiltedUp.anchorXPx).isWithin(0.1f).of(level.anchorXPx)
    }

    @Test
    fun normalizeHeadingDelta_wrapsToSignedRange() {
        assertThat(ScreenProjector.normalizeHeadingDelta(10.0, 350f)).isWithin(0.001).of(20.0)
        assertThat(ScreenProjector.normalizeHeadingDelta(350.0, 10f)).isWithin(0.001).of(-20.0)
    }

    @Test
    fun projectFootprintSpan_centroidOutsideFov_clipsVisibleSlice() {
        val span =
            ScreenProjector.projectFootprintSpan(
                bearingDeg = 32.0,
                footprintRadiusM = 600.0,
                centerDistanceM = 4_000.0,
                trueAzimuthDeg = 0f,
                horizontalFovDeg = 60f,
                screenWidthPx = 1080f
            )

        assertThat(span).isNotNull()
        assertThat(span!!.leftXPx).isGreaterThan(540f)
        assertThat(span.rightXPx).isWithin(0.1f).of(1080f)
        assertThat(span.anchorXPx).isAtLeast(span.leftXPx)
        assertThat(span.anchorXPx).isAtMost(span.rightXPx)
    }

    @Test
    fun projectFootprintSpan_fullyOutsideFov_returnsNull() {
        val span =
            ScreenProjector.projectFootprintSpan(
                bearingDeg = 120.0,
                footprintRadiusM = 500.0,
                centerDistanceM = 1_000.0,
                trueAzimuthDeg = 0f,
                horizontalFovDeg = 60f,
                screenWidthPx = 1080f
            )

        assertThat(span).isNull()
    }

    @Test
    fun projectFootprintSpan_observerInsideFootprint_usesFullHalfPlane() {
        val span =
            ScreenProjector.projectFootprintSpan(
                bearingDeg = 0.0,
                footprintRadiusM = 2_000.0,
                centerDistanceM = 500.0,
                trueAzimuthDeg = 0f,
                horizontalFovDeg = 60f,
                screenWidthPx = 1080f
            )

        assertThat(span).isNotNull()
        assertThat(span!!.leftXPx).isWithin(0.1f).of(0f)
        assertThat(span.rightXPx).isWithin(0.1f).of(1080f)
    }

    @Test
    fun perspectiveProjection_placesOffCenterBearingInwardOfLinearMapping() {
        val halfFov = 30f
        val screenWidth = 1080f
        val headingDelta = 15.0
        val perspectiveX =
            ScreenProjector.anchorXPx(
                headingDeltaDeg = headingDelta,
                halfHorizontalFovDeg = halfFov,
                screenWidthPx = screenWidth
            )
        val linearX = screenWidth / 2f + (headingDelta / halfFov).toFloat() * (screenWidth / 2f)

        assertThat(perspectiveX).isGreaterThan(screenWidth / 2f)
        assertThat(perspectiveX).isLessThan(linearX)
    }

    @Test
    fun perspectiveProjection_mapsHalfFovToScreenEdge() {
        val x =
            ScreenProjector.anchorXPx(
                headingDeltaDeg = 30.0,
                halfHorizontalFovDeg = 30f,
                screenWidthPx = 1080f
            )
        assertThat(x).isWithin(0.1f).of(1080f)
    }
}
