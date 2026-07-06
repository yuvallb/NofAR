package com.nofar.core.ui.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nofar.core.model.LocationAccessState
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
    val locationAccessState: LocationAccessState,
    val requestFineLocation: () -> Unit,
    val requestCamera: () -> Unit,
    val openAppSettings: () -> Unit
)

@Composable
fun rememberNofARPermissionState(): PermissionState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? android.app.Activity

    var fineLocationGranted by remember { mutableStateOf(isFineLocationGranted(context)) }
    var locationRequested by remember { mutableStateOf(false) }
    var locationDeniedAfterRequest by remember { mutableStateOf(false) }
    var cameraGranted by remember { mutableStateOf(isCameraGranted(context)) }
    val internetGranted = remember { isInternetGranted(context) }

    DisposableEffect(lifecycleOwner, context) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    fineLocationGranted = isFineLocationGranted(context)
                    cameraGranted = isCameraGranted(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            locationRequested = true
            fineLocationGranted = granted
            if (!granted) {
                locationDeniedAfterRequest = isLocationPermanentlyDenied(activity)
            }
        }

    val cameraLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> cameraGranted = granted }

    val locationAccessState =
        resolveLocationAccessState(
            fineLocationGranted = fineLocationGranted,
            locationRequested = locationRequested,
            permanentlyDenied = locationDeniedAfterRequest
        )

    return PermissionState(
        fineLocationGranted = fineLocationGranted,
        cameraGranted = cameraGranted,
        internetGranted = internetGranted,
        locationAccessState = locationAccessState,
        requestFineLocation = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
        requestCamera = { cameraLauncher.launch(Manifest.permission.CAMERA) },
        openAppSettings = {
            val intent =
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    )
}

private fun resolveLocationAccessState(
    fineLocationGranted: Boolean,
    locationRequested: Boolean,
    permanentlyDenied: Boolean
): LocationAccessState = when {
    fineLocationGranted -> LocationAccessState.GRANTED
    permanentlyDenied -> LocationAccessState.DENIED_PERMANENTLY
    locationRequested -> LocationAccessState.DENIED
    else -> LocationAccessState.NOT_REQUESTED
}
