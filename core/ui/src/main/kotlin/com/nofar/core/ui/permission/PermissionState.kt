package com.nofar.core.ui.permission

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.nofar.core.ui.R

enum class NofARPermission {
    FineLocation,
    Camera,
    Internet
}

fun permissionRationaleResId(permission: NofARPermission): Int = when (permission) {
    NofARPermission.FineLocation -> R.string.permission_rationale_location
    NofARPermission.Camera -> R.string.permission_rationale_camera
    NofARPermission.Internet -> R.string.permission_rationale_internet
}

@Immutable
data class PermissionState(
    val fineLocationGranted: Boolean,
    val cameraGranted: Boolean,
    val internetGranted: Boolean,
    val requestFineLocation: () -> Unit,
    val requestCamera: () -> Unit
)

/**
 * Stub permission helper for Phase 0 — feature modules wire rationale UI in later phases.
 */
@Composable
fun rememberNofARPermissionState(): PermissionState {
    val context = LocalContext.current

    var fineLocationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val internetGranted =
        remember {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.INTERNET
            ) == PackageManager.PERMISSION_GRANTED
        }

    val locationLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> fineLocationGranted = granted }

    val cameraLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> cameraGranted = granted }

    return PermissionState(
        fineLocationGranted = fineLocationGranted,
        cameraGranted = cameraGranted,
        internetGranted = internetGranted,
        requestFineLocation = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
        requestCamera = { cameraLauncher.launch(Manifest.permission.CAMERA) }
    )
}
