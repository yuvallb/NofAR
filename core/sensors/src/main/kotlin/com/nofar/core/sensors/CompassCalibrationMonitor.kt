package com.nofar.core.sensors

import android.hardware.SensorManager
import com.nofar.core.model.AppConfig
import com.nofar.core.model.CompassCalibrationState
import com.nofar.core.model.DeviceOrientation
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompassCalibrationMonitor
@Inject
constructor() {
    fun calibrationState(orientation: DeviceOrientation?): CompassCalibrationState {
        if (orientation == null) return CompassCalibrationState.UNAVAILABLE
        return when {
            orientation.accuracy <= SensorManager.SENSOR_STATUS_UNRELIABLE ->
                CompassCalibrationState.NEEDS_CALIBRATION
            orientation.accuracy <= AppConfig.COMPASS_ACCURACY_THRESHOLD ->
                CompassCalibrationState.NEEDS_CALIBRATION
            else -> CompassCalibrationState.OK
        }
    }
}
