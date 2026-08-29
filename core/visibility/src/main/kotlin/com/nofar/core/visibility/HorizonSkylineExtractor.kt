package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import kotlin.math.roundToInt

/**
 * Extracts a normalized horizontal skyline profile from a grayscale camera frame.
 * Output Y is in 0..1 (top → bottom); [Float.NaN] marks columns with no reliable edge.
 */
object HorizonSkylineExtractor {
    fun extractNormalizedProfile(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        columnCount: Int = AppConfig.HORIZON_ALIGN_PROFILE_COLUMNS
    ): FloatArray {
        if (!isValidFrame(yPlane, width, height, columnCount)) {
            return FloatArray(columnCount) { Float.NaN }
        }

        val profile = FloatArray(columnCount)
        for (columnIndex in 0 until columnCount) {
            val x = ((columnIndex + 0.5f) / columnCount * width).roundToInt().coerceIn(0, width - 1)
            profile[columnIndex] = skylineYNormalizedAtColumn(yPlane, width, height, x)
        }
        return profile
    }

    private fun skylineYNormalizedAtColumn(yPlane: ByteArray, width: Int, height: Int, x: Int): Float {
        var bestRow = -1
        var bestContrast = 0
        val minContrast = AppConfig.HORIZON_ALIGN_MIN_EDGE_CONTRAST
        val topScanLimit = (height * 0.85f).roundToInt().coerceAtMost(height - 2)

        for (row in 1 until topScanLimit) {
            val above = yPlane[rowIndex(row - 1, x, width)].toInt() and 0xFF
            val below = yPlane[rowIndex(row, x, width)].toInt() and 0xFF
            val contrast = above - below
            if (contrast >= minContrast && contrast > bestContrast) {
                bestContrast = contrast
                bestRow = row
            }
        }

        return if (bestRow < 0) {
            Float.NaN
        } else {
            bestRow.toFloat() / (height - 1).coerceAtLeast(1)
        }
    }

    internal fun normalizedVariance(values: FloatArray): Float {
        val valid = values.filter { it.isFinite() }
        if (valid.size < 2) return 0f
        val mean = valid.average().toFloat()
        return valid.map { value -> (value - mean) * (value - mean) }.average().toFloat()
    }

    private fun rowIndex(row: Int, column: Int, width: Int): Int = row * width + column

    private fun isValidFrame(yPlane: ByteArray, width: Int, height: Int, columnCount: Int): Boolean =
        width > 0 && height > 0 && columnCount > 0 && yPlane.size >= width * height
}
