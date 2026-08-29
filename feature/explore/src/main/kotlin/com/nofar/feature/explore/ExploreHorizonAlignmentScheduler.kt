package com.nofar.feature.explore

import com.nofar.core.model.AppConfig
import com.nofar.core.model.DeviceOrientation
import com.nofar.core.visibility.HorizonAlignmentGates

/**
 * Tracks stillness dwell and cooldown gates for automatic horizon alignment attempts.
 */
internal class ExploreHorizonAlignmentScheduler {
    private var stillAnchor: DeviceOrientation? = null
    private var stillSinceMs: Long? = null
    private var lastAttemptMs: Long? = null

    fun onOrientation(
        orientation: DeviceOrientation,
        nowMs: Long = System.currentTimeMillis()
    ): HorizonAlignmentAttemptGate {
        if (!HorizonAlignmentGates.isNearHorizon(orientation.cameraElevationDeg)) {
            resetStillness()
            return HorizonAlignmentAttemptGate.NotReady
        }
        return evaluateStillAttempt(orientation, nowMs)
    }

    fun resetSession() {
        resetStillness()
        lastAttemptMs = null
    }

    private fun evaluateStillAttempt(orientation: DeviceOrientation, nowMs: Long): HorizonAlignmentAttemptGate {
        val anchor = stillAnchor
        if (anchor == null || !HorizonAlignmentGates.isOrientationStable(anchor, orientation)) {
            stillAnchor = orientation
            stillSinceMs = nowMs
            return HorizonAlignmentAttemptGate.NotReady
        }

        val dwellStart = stillSinceMs ?: nowMs
        val readyToAttempt =
            nowMs - dwellStart >= AppConfig.HORIZON_ALIGN_STILL_DWELL_MS &&
                (
                    lastAttemptMs == null ||
                        nowMs - lastAttemptMs!! >= AppConfig.HORIZON_ALIGN_ATTEMPT_COOLDOWN_MS
                    )
        return if (readyToAttempt) {
            lastAttemptMs = nowMs
            resetStillness()
            HorizonAlignmentAttemptGate.AttemptNow
        } else {
            HorizonAlignmentAttemptGate.NotReady
        }
    }

    private fun resetStillness() {
        stillAnchor = null
        stillSinceMs = null
    }
}

internal enum class HorizonAlignmentAttemptGate {
    NotReady,
    AttemptNow
}
