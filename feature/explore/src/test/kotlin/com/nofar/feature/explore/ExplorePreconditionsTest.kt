package com.nofar.feature.explore

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import com.nofar.core.model.CompassCalibrationState
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.LocationAccessState
import java.time.Instant
import java.util.UUID
import org.junit.Test

class ExplorePreconditionsTest {
    @Test
    fun allPreconditionsMet_returnsReady() {
        val gate = resolve(sampleRegion(DownloadStatus.READY))
        assertThat(gate).isEqualTo(ExploreGate.READY)
    }

    @Test
    fun cameraDenied_returnsCameraDenied() {
        val gate =
            ExplorePreconditions.resolveGate(
                locationAccessState = LocationAccessState.GRANTED,
                waitingForGpsFix = false,
                cameraGranted = false,
                calibrationState = CompassCalibrationState.OK,
                activeCoverageSet = sampleRegion(DownloadStatus.READY),
                graceExpired = false,
                simpleModeEnabled = false,
                regionDownloadNeeded = false,
                regionDownloading = false
            )
        assertThat(gate).isEqualTo(ExploreGate.CAMERA_DENIED)
    }

    @Test
    fun graceExpiredInAdvancedMode_takesPriority() {
        val gate =
            ExplorePreconditions.resolveGate(
                locationAccessState = LocationAccessState.GRANTED,
                waitingForGpsFix = false,
                cameraGranted = true,
                calibrationState = CompassCalibrationState.OK,
                activeCoverageSet = sampleRegion(DownloadStatus.READY),
                graceExpired = true,
                simpleModeEnabled = false,
                regionDownloadNeeded = false,
                regionDownloading = false
            )
        assertThat(gate).isEqualTo(ExploreGate.GRACE_EXPIRED)
    }

    @Test
    fun simpleModeDownloading_returnsDownloadingGate() {
        val gate =
            ExplorePreconditions.resolveGate(
                locationAccessState = LocationAccessState.GRANTED,
                waitingForGpsFix = false,
                cameraGranted = true,
                calibrationState = CompassCalibrationState.OK,
                activeCoverageSet = null,
                graceExpired = false,
                simpleModeEnabled = true,
                regionDownloadNeeded = true,
                regionDownloading = true
            )
        assertThat(gate).isEqualTo(ExploreGate.REGION_DOWNLOADING)
    }

    @Test
    fun simpleModeDownloadNeeded_returnsDownloadNeededGate() {
        val gate =
            ExplorePreconditions.resolveGate(
                locationAccessState = LocationAccessState.GRANTED,
                waitingForGpsFix = false,
                cameraGranted = true,
                calibrationState = CompassCalibrationState.OK,
                activeCoverageSet = null,
                graceExpired = false,
                simpleModeEnabled = true,
                regionDownloadNeeded = true,
                regionDownloading = false
            )
        assertThat(gate).isEqualTo(ExploreGate.REGION_DOWNLOAD_NEEDED)
    }

    @Test
    fun partialRegionStillAllowsReady() {
        val gate = resolve(sampleRegion(DownloadStatus.PARTIAL))
        assertThat(gate).isEqualTo(ExploreGate.READY)
    }

    @Test
    fun readFieldOfViewFromSensor_matchesExpectedAngles() {
        val fov =
            readFieldOfViewFromSensor(
                focalLengthMm = 4.0f,
                sensorWidthMm = 5.0f,
                sensorHeightMm = 3.75f
            )
        assertThat(fov.isFallback).isFalse()
        assertThat(fov.horizontalDeg).isGreaterThan(AppConfig.CAMERA_HORIZONTAL_FOV_FALLBACK_DEG / 2f)
        assertThat(fov.verticalDeg).isGreaterThan(AppConfig.CAMERA_VERTICAL_FOV_FALLBACK_DEG / 2f)
    }

    private fun resolve(activeCoverageSet: CoverageSet?): ExploreGate = ExplorePreconditions.resolveGate(
        locationAccessState = LocationAccessState.GRANTED,
        waitingForGpsFix = false,
        cameraGranted = true,
        calibrationState = CompassCalibrationState.OK,
        activeCoverageSet = activeCoverageSet,
        graceExpired = false,
        simpleModeEnabled = false,
        regionDownloadNeeded = false,
        regionDownloading = false
    )

    private fun sampleRegion(status: DownloadStatus): CoverageSet {
        val now = Instant.now()
        return CoverageSet(
            id = UUID.randomUUID(),
            name = "Test Coverage",
            createdAt = now,
            updatedAt = now,
            downloadStatus = status,
            downloadProgressPct = 100,
            osmDatasetVersion = null,
            estimatedSizeBytes = 1L,
            entityCount = 1
        )
    }
}
