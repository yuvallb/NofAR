package com.nofar.core.visibility

import kotlin.math.roundToInt

/**
 * Crops a Y-plane buffer to the visible FILL_CENTER rectangle for a given view aspect.
 */
object FillCenterCrop {
    fun cropYPlane(yPlane: ByteArray, imageWidth: Int, imageHeight: Int, viewAspect: Float): GrayscaleFrame? {
        val invalidInput =
            imageWidth <= 0 ||
                imageHeight <= 0 ||
                viewAspect <= 0f ||
                yPlane.size < imageWidth * imageHeight
        if (invalidInput) return null

        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
        val crop =
            when {
                viewAspect > imageAspect -> cropVertical(imageWidth, imageHeight, viewAspect)
                viewAspect < imageAspect -> cropHorizontal(imageWidth, imageHeight, viewAspect)
                else -> CropRect(left = 0, top = 0, width = imageWidth, height = imageHeight)
            }

        val cropped = ByteArray(crop.width * crop.height)
        for (row in 0 until crop.height) {
            val sourceRow = crop.top + row
            System.arraycopy(
                yPlane,
                sourceRow * imageWidth + crop.left,
                cropped,
                row * crop.width,
                crop.width
            )
        }
        return GrayscaleFrame(yPlane = cropped, width = crop.width, height = crop.height)
    }

    private fun cropVertical(imageWidth: Int, imageHeight: Int, viewAspect: Float): CropRect {
        val targetHeight = (imageWidth / viewAspect).roundToInt().coerceIn(1, imageHeight)
        val top = ((imageHeight - targetHeight) / 2f).roundToInt().coerceAtLeast(0)
        return CropRect(left = 0, top = top, width = imageWidth, height = targetHeight)
    }

    private fun cropHorizontal(imageWidth: Int, imageHeight: Int, viewAspect: Float): CropRect {
        val targetWidth = (imageHeight * viewAspect).roundToInt().coerceIn(1, imageWidth)
        val left = ((imageWidth - targetWidth) / 2f).roundToInt().coerceAtLeast(0)
        return CropRect(left = left, top = 0, width = targetWidth, height = imageHeight)
    }

    private data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int)
}
