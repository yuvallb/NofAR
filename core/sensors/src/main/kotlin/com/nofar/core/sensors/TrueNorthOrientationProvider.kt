package com.nofar.core.sensors

import com.nofar.core.model.DeviceOrientation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Converts magnetic azimuth from the raw rotation-vector provider to true north.
 */
class TrueNorthOrientationProvider(
    private val delegate: OrientationProvider,
    private val declinationCorrector: DeclinationCorrector
) : OrientationProvider {
    override val orientationFlow: Flow<DeviceOrientation> =
        delegate.orientationFlow.map { raw ->
            raw.copy(
                trueAzimuthDeg = declinationCorrector.magneticToTrueAzimuth(raw.trueAzimuthDeg)
            )
        }

    override fun start() = delegate.start()

    override fun stop() = delegate.stop()
}
