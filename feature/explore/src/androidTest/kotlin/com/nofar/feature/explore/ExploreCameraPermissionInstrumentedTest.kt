package com.nofar.feature.explore

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExploreCameraPermissionInstrumentedTest {
    @Test
    fun cameraPermission_canBeQueriedWithoutNetworkAccess() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val status = context.checkSelfPermission(Manifest.permission.CAMERA)
        assertThat(status).isAnyOf(
            android.content.pm.PackageManager.PERMISSION_GRANTED,
            android.content.pm.PackageManager.PERMISSION_DENIED
        )
    }

    @Test
    fun exploreGateReflectsCameraPermissionState() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val granted =
            context.checkSelfPermission(Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        val gate =
            ExplorePreconditions.resolveGate(
                locationAccessState = com.nofar.core.model.LocationAccessState.GRANTED,
                waitingForGpsFix = false,
                cameraGranted = granted,
                calibrationState = com.nofar.core.model.CompassCalibrationState.OK,
                activeRegion = null,
                graceExpired = false,
                simpleModeEnabled = false,
                regionDownloadNeeded = false,
                regionDownloading = false,
                downloadPromptDismissed = false
            )
        if (granted) {
            assertThat(gate).isEqualTo(ExploreGate.REGION_MISSING)
        } else {
            assertThat(gate).isEqualTo(ExploreGate.CAMERA_DENIED)
        }
    }
}
