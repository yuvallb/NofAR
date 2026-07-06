package com.nofar.core.sensors

import com.nofar.core.model.AppConfig
import com.nofar.core.model.DeviceOrientation
import com.nofar.core.sensors.filter.AngularOneEuroFilter
import com.nofar.core.sensors.filter.OneEuroFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Decorator that exposes only One Euro filtered orientation to downstream consumers (AC-3.5).
 */
class SmoothedOrientationProvider(private val delegate: OrientationProvider) : OrientationProvider {
    private val azimuthFilter =
        AngularOneEuroFilter(
            minCutoff = AppConfig.ONE_EURO_MIN_CUTOFF_AZIMUTH,
            beta = AppConfig.ONE_EURO_BETA_AZIMUTH,
            dCutoff = AppConfig.ONE_EURO_D_CUTOFF
        )
    private val pitchFilter =
        OneEuroFilter(
            minCutoff = AppConfig.ONE_EURO_MIN_CUTOFF_PITCH,
            beta = AppConfig.ONE_EURO_BETA_PITCH,
            dCutoff = AppConfig.ONE_EURO_D_CUTOFF
        )
    private val rollFilter =
        OneEuroFilter(
            minCutoff = AppConfig.ONE_EURO_MIN_CUTOFF_ROLL,
            beta = AppConfig.ONE_EURO_BETA_ROLL,
            dCutoff = AppConfig.ONE_EURO_D_CUTOFF
        )

    override val orientationFlow: Flow<DeviceOrientation> =
        delegate.orientationFlow.map { raw ->
            val timestampSeconds = raw.timestampNanos / 1_000_000_000.0
            DeviceOrientation(
                trueAzimuthDeg =
                azimuthFilter.filter(
                    raw.trueAzimuthDeg.toDouble(),
                    timestampSeconds
                ).toFloat(),
                pitchDeg =
                pitchFilter.filter(
                    raw.pitchDeg.toDouble(),
                    timestampSeconds
                ).toFloat(),
                rollDeg =
                rollFilter.filter(
                    raw.rollDeg.toDouble(),
                    timestampSeconds
                ).toFloat(),
                accuracy = raw.accuracy,
                timestampNanos = raw.timestampNanos
            )
        }

    override fun start() = delegate.start()

    override fun stop() {
        delegate.stop()
        azimuthFilter.reset()
        pitchFilter.reset()
        rollFilter.reset()
    }
}
