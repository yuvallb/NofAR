package com.nofar.feature.explore

import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nofar.core.model.AppConfig
import com.nofar.core.visibility.CameraFieldOfView
import kotlin.math.atan

private const val TAG = "ExploreCamera"

@ExperimentalCamera2Interop
@Composable
fun ExploreCameraPreview(modifier: Modifier = Modifier, onFieldOfViewChanged: (CameraFieldOfView) -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).also { view ->
                view.scaleType = PreviewView.ScaleType.FIT_CENTER
                previewView = view
            }
        }
    )

    DisposableEffect(lifecycleOwner, previewView) {
        val view = previewView
        if (view == null) {
            onDispose { }
        } else {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val listener =
                Runnable {
                    runCatching {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(view.surfaceProvider)

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.unbindAll()
                        val camera =
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview
                            )

                        val fov = readFieldOfView(camera.cameraInfo)
                        onFieldOfViewChanged(fov)
                    }.onFailure { error ->
                        Log.e(TAG, "Camera preview bind failed; using fallback FOV", error)
                        onFieldOfViewChanged(CameraFieldOfView.fallback())
                    }
                }
            cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

            onDispose {
                runCatching {
                    cameraProviderFuture.get().unbindAll()
                }
            }
        }
    }
}

@ExperimentalCamera2Interop
internal fun readFieldOfView(cameraInfo: androidx.camera.core.CameraInfo): CameraFieldOfView {
    return runCatching {
        val camera2Info = Camera2CameraInfo.from(cameraInfo)
        val characteristics = camera2Info.getCameraCharacteristic(
            android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
        )
        val sensorSize =
            camera2Info.getCameraCharacteristic(
                android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE
            )

        if (characteristics == null || sensorSize == null || characteristics.isEmpty()) {
            Log.w(TAG, "Camera FOV characteristics unavailable; using fallback for back camera")
            return CameraFieldOfView.fallback()
        }

        val focalLength = characteristics[0]
        val horizontalDeg =
            Math.toDegrees(
                2.0 * atan((sensorSize.width / (2.0 * focalLength)))
            ).toFloat()
        val verticalDeg =
            Math.toDegrees(
                2.0 * atan((sensorSize.height / (2.0 * focalLength)))
            ).toFloat()

        CameraFieldOfView(
            horizontalDeg = horizontalDeg,
            verticalDeg = verticalDeg,
            isFallback = false
        )
    }.getOrElse { error ->
        Log.w(TAG, "Failed to read camera FOV; using fallback", error)
        CameraFieldOfView.fallback()
    }
}

internal fun readFieldOfViewFromSensor(
    focalLengthMm: Float,
    sensorWidthMm: Float,
    sensorHeightMm: Float
): CameraFieldOfView {
    val horizontalDeg =
        Math.toDegrees(
            2.0 * atan((sensorWidthMm / (2.0 * focalLengthMm)))
        ).toFloat()
    val verticalDeg =
        Math.toDegrees(
            2.0 * atan((sensorHeightMm / (2.0 * focalLengthMm)))
        ).toFloat()
    return CameraFieldOfView(
        horizontalDeg = horizontalDeg.coerceAtLeast(AppConfig.CAMERA_HORIZONTAL_FOV_FALLBACK_DEG / 2f),
        verticalDeg = verticalDeg.coerceAtLeast(AppConfig.CAMERA_VERTICAL_FOV_FALLBACK_DEG / 2f),
        isFallback = false
    )
}
