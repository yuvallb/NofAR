package com.nofar.core.visibility

enum class HorizonAlignmentRejectReason {
    LOW_CONFIDENCE,
    OVER_THRESHOLD,
    FLAT_SKYLINE,
    INSUFFICIENT_SAMPLES,
    NO_CAMERA_FRAME
}

data class HorizonAlignmentResult(
    val azimuthOffsetDeg: Float,
    val pitchOffsetDeg: Float,
    val meanAbsError: Float,
    val accepted: Boolean,
    val rejectReason: HorizonAlignmentRejectReason? = null
)

data class GrayscaleFrame(val yPlane: ByteArray, val width: Int, val height: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GrayscaleFrame) return false
        return width == other.width &&
            height == other.height &&
            yPlane.contentEquals(other.yPlane)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + yPlane.contentHashCode()
        return result
    }
}
