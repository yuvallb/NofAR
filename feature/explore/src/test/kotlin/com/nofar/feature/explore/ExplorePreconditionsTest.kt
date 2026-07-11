package com.nofar.feature.explore

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.AppConfig
import com.nofar.core.model.CompassCalibrationState
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.LocationAccessState
import com.nofar.core.model.Region
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
                activeRegion = sampleRegion(DownloadStatus.READY),
                graceExpired = false,
                simpleModeEnabled = false,
                regionDownloadNeeded = false,
                regionDownloading = false,
                downloadPromptDismissed = false
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
                activeRegion = sampleRegion(DownloadStatus.READY),
                graceExpired = true,
                simpleModeEnabled = false,
                regionDownloadNeeded = false,
                regionDownloading = false,
                downloadPromptDismissed = false
            )
        assertThat(gate).isEqualTo(ExploreGate.GRACE_EXPIRED)
    }

    @Test
    fun simpleModeDownloadNeeded_returnsDownloadNeededGate() {
        val gate =
            ExplorePreconditions.resolveGate(
                locationAccessState = LocationAccessState.GRANTED,
                waitingForGpsFix = false,
                cameraGranted = true,
                calibrationState = CompassCalibrationState.OK,
                activeRegion = null,
                graceExpired = false,
                simpleModeEnabled = true,
                regionDownloadNeeded = true,
                regionDownloading = false,
                downloadPromptDismissed = false
            )
        assertThat(gate).isEqualTo(ExploreGate.REGION_DOWNLOAD_NEEDED)
    }

    @Test
    fun simpleModeDismissedPrompt_returnsDismissedGate() {
        val gate =
            ExplorePreconditions.resolveGate(
                locationAccessState = LocationAccessState.GRANTED,
                waitingForGpsFix = false,
                cameraGranted = true,
                calibrationState = CompassCalibrationState.OK,
                activeRegion = null,
                graceExpired = false,
                simpleModeEnabled = true,
                regionDownloadNeeded = true,
                regionDownloading = false,
                downloadPromptDismissed = true
            )
        assertThat(gate).isEqualTo(ExploreGate.REGION_DOWNLOAD_DISMISSED)
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

    private fun resolve(activeRegion: Region?): ExploreGate = ExplorePreconditions.resolveGate(
        locationAccessState = LocationAccessState.GRANTED,
        waitingForGpsFix = false,
        cameraGranted = true,
        calibrationState = CompassCalibrationState.OK,
        activeRegion = activeRegion,
        graceExpired = false,
        simpleModeEnabled = false,
        regionDownloadNeeded = false,
        regionDownloading = false,
        downloadPromptDismissed = false
    )

    private fun sampleRegion(status: DownloadStatus): Region = Region(
        id = UUID.randomUUID(),
        name = "Test Region",
        centerLat = 32.0,
        centerLon = 35.0,
        radiusM = 10_000.0,
        minLat = 31.9,
        maxLat = 32.1,
        minLon = 34.9,
        maxLon = 35.1,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        downloadStatus = status,
        downloadProgressPct = 100,
        osmDatasetVersion = null,
        estimatedSizeBytes = 1L,
        entityCount = 1
    )
}
