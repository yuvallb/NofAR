package com.nofar.core.sensors

import com.nofar.core.model.DeviceOrientation
import kotlinx.coroutines.flow.Flow

interface OrientationProvider {
    val orientationFlow: Flow<DeviceOrientation>

    fun start()

    fun stop()
}
