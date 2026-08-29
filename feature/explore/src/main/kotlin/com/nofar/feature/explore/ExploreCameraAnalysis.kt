package com.nofar.feature.explore

import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.nofar.core.visibility.GrayscaleFrame
import java.util.concurrent.Executor

internal fun buildExploreImageAnalysis(
    targetRotation: Int,
    executor: Executor,
    frameStore: ExploreCameraFrameStore
): ImageAnalysis = ImageAnalysis.Builder()
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
    .setTargetRotation(targetRotation)
    .setOutputImageRotationEnabled(true)
    .setTargetResolution(Size(320, 240))
    .build()
    .also { analysis ->
        analysis.setAnalyzer(executor) { image ->
            image.toGrayscaleFrame()?.let(frameStore::update)
            image.close()
        }
    }

internal fun ImageProxy.toGrayscaleFrame(): GrayscaleFrame? {
    if (planes.isEmpty() || width <= 0 || height <= 0) return null
    val plane = planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val frameWidth = width
    val frameHeight = height

    buffer.rewind()
    val yPlane = ByteArray(frameWidth * frameHeight)
    if (rowStride == frameWidth) {
        val toCopy = minOf(buffer.remaining(), yPlane.size)
        buffer.get(yPlane, 0, toCopy)
    } else {
        for (row in 0 until frameHeight) {
            buffer.position(row * rowStride)
            buffer.get(yPlane, row * frameWidth, frameWidth)
        }
    }
    return GrayscaleFrame(yPlane = yPlane, width = frameWidth, height = frameHeight)
}
