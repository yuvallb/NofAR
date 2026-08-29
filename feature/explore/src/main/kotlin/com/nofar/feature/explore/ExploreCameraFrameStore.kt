package com.nofar.feature.explore

import com.nofar.core.visibility.GrayscaleFrame
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the latest camera Y-plane frame from [ExploreCameraPreview] ImageAnalysis for skyline matching.
 * Frames are copied on write and read; never persisted.
 */
@Singleton
class ExploreCameraFrameStore
@Inject
constructor() {
    private val lock = Any()
    private var latest: GrayscaleFrame? = null

    fun update(frame: GrayscaleFrame) {
        synchronized(lock) {
            latest = frame.copyYPlane()
        }
    }

    fun snapshot(): GrayscaleFrame? = synchronized(lock) {
        latest?.copyYPlane()
    }

    fun clear() {
        synchronized(lock) {
            latest = null
        }
    }

    private fun GrayscaleFrame.copyYPlane(): GrayscaleFrame = GrayscaleFrame(
        yPlane = yPlane.copyOf(),
        width = width,
        height = height
    )
}
