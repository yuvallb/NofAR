package com.nofar.core.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nofar.core.model.UserLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationControllerInstrumentedTest {
    @Test
    fun locationStopsWhenExploreTokenReleased() {
        val provider = RecordingLocationProvider()
        val repository = DefaultLocationRepository(provider)
        val controller = LocationController(repository)

        controller.acquire("explore")
        assertTrue(provider.updatesActive)

        controller.release("explore")
        assertFalse(provider.updatesActive)
        assertFalse(repository.isActive)
    }

    private class RecordingLocationProvider : LocationProvider {
        var updatesActive = false
        private val locationEvents = MutableSharedFlow<UserLocation>(extraBufferCapacity = 1)
        private val lastLocationState = MutableStateFlow<UserLocation?>(null)

        override val locationFlow: Flow<UserLocation> = locationEvents.asSharedFlow()
        override val lastLocation: UserLocation?
            get() = lastLocationState.value

        override fun startUpdates() {
            updatesActive = true
        }

        override fun stopUpdates() {
            updatesActive = false
        }

        override fun clearLastLocation() {
            lastLocationState.value = null
        }
    }
}
