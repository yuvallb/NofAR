package com.nofar.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.nofar.core.model.AppConfig
import com.nofar.core.model.UserLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * GPS-only updates via [LocationManager.GPS_PROVIDER] — no network or Wi-Fi fusion (AC-3.2).
 */
@Singleton
class GpsOnlyLocationProvider
@Inject
constructor(@ApplicationContext private val context: Context) : LocationProvider {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _lastLocation = MutableStateFlow<UserLocation?>(null)
    private val _locationFlow = MutableSharedFlow<UserLocation>(extraBufferCapacity = 1)
    private var listener: LocationListener? = null

    override val lastLocation: UserLocation?
        get() = _lastLocation.value

    override val locationFlow: Flow<UserLocation> = _locationFlow.asSharedFlow()

    override fun startUpdates() {
        if (listener != null) return
        registerListener()
    }

    override fun stopUpdates() {
        unregisterListener()
    }

    override fun clearLastLocation() {
        _lastLocation.value = null
    }

    private fun registerListener() {
        if (!hasLocationPermission()) return
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return

        val locationListener =
            LocationListener { location ->
                if (location.provider == LocationManager.GPS_PROVIDER) {
                    onLocation(location)
                }
            }
        listener = locationListener
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            AppConfig.GPS_UPDATE_INTERVAL_MS,
            0f,
            locationListener,
            Looper.getMainLooper()
        )
        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { last ->
            if (last.provider == LocationManager.GPS_PROVIDER) {
                onLocation(last)
            }
        }
    }

    private fun unregisterListener() {
        listener?.let { locationManager.removeUpdates(it) }
        listener = null
    }

    private fun onLocation(location: Location) {
        val mapped = location.toUserLocation()
        _lastLocation.value = mapped
        _locationFlow.tryEmit(mapped)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun Location.toUserLocation(): UserLocation = UserLocation(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = if (hasAltitude()) altitude else null,
        accuracyMeters = accuracy,
        timestampMillis = time
    )
}
