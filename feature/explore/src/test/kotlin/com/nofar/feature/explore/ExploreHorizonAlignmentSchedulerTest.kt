package com.nofar.feature.explore

import com.google.common.truth.Truth.assertThat
import com.nofar.core.model.DeviceOrientation
import org.junit.Test

class ExploreHorizonAlignmentSchedulerTest {
    private val scheduler = ExploreHorizonAlignmentScheduler()

    @Test
    fun nearHorizonAndStable_orientationEventuallyAttemptsOnce() {
        val orientation =
            DeviceOrientation(
                trueAzimuthDeg = 90f,
                pitchDeg = 0f,
                rollDeg = 0f,
                cameraElevationDeg = 5f,
                accuracy = 3,
                timestampNanos = 0L
            )
        var nowMs = 0L
        assertThat(scheduler.onOrientation(orientation, nowMs)).isEqualTo(HorizonAlignmentAttemptGate.NotReady)

        nowMs += 500L
        assertThat(scheduler.onOrientation(orientation, nowMs)).isEqualTo(HorizonAlignmentAttemptGate.NotReady)

        nowMs += 600L
        assertThat(scheduler.onOrientation(orientation, nowMs)).isEqualTo(HorizonAlignmentAttemptGate.AttemptNow)

        nowMs += 600L
        assertThat(scheduler.onOrientation(orientation, nowMs)).isEqualTo(HorizonAlignmentAttemptGate.NotReady)
    }

    @Test
    fun lookingUp_resetsStillnessWithoutAttempt() {
        val level =
            DeviceOrientation(
                trueAzimuthDeg = 90f,
                pitchDeg = 0f,
                rollDeg = 0f,
                cameraElevationDeg = 5f,
                accuracy = 3,
                timestampNanos = 0L
            )
        val lookingUp = level.copy(cameraElevationDeg = 35f)

        scheduler.onOrientation(level, nowMs = 0L)
        assertThat(scheduler.onOrientation(lookingUp, nowMs = 2_000L))
            .isEqualTo(HorizonAlignmentAttemptGate.NotReady)
    }
}
