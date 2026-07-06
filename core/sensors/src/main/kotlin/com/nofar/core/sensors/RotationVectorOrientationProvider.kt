package com.nofar.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.nofar.core.model.DeviceOrientation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Raw magnetic orientation from [Sensor.TYPE_ROTATION_VECTOR] at display refresh rate.
 * Values are magnetic azimuth — apply [TrueNorthOrientationProvider] before use (AC-3.5).
 */
@Singleton
class RotationVectorOrientationProvider
@Inject
constructor(@ApplicationContext context: Context) :
    OrientationProvider {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val _orientationFlow = MutableSharedFlow<DeviceOrientation>(extraBufferCapacity = 1)
    private var listener: SensorEventListener? = null
    private var latestAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE

    override val orientationFlow: Flow<DeviceOrientation> = _orientationFlow.asSharedFlow()

    override fun start() {
        if (listener != null) return
        registerListener()
    }

    override fun stop() {
        unregisterListener()
    }

    private fun registerListener() {
        val sensor = rotationSensor ?: return
        val sensorListener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val orientation = event.toDeviceOrientation(latestAccuracy) ?: return
                    _orientationFlow.tryEmit(orientation)
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    latestAccuracy = accuracy
                }
            }
        listener = sensorListener
        sensorManager.registerListener(
            sensorListener,
            sensor,
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    private fun unregisterListener() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
    }

    private fun SensorEvent.toDeviceOrientation(accuracy: Int): DeviceOrientation? {
        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val azimuthRad = orientationAngles[0]
        val pitchRad = orientationAngles[1]
        val rollRad = orientationAngles[2]

        val magneticAzimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
        val normalizedAzimuth =
            if (magneticAzimuthDeg < 0f) magneticAzimuthDeg + 360f else magneticAzimuthDeg

        return DeviceOrientation(
            trueAzimuthDeg = normalizedAzimuth,
            pitchDeg = Math.toDegrees(pitchRad.toDouble()).toFloat(),
            rollDeg = Math.toDegrees(rollRad.toDouble()).toFloat(),
            accuracy = accuracy,
            timestampNanos = timestamp
        )
    }
}
