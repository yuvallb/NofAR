package com.nofar.core.location

import com.nofar.core.model.UserLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationControllerTest {
    @Test
    fun startsOnFirstAcquireAndStopsWhenAllReleased() {
        val provider = FakeLocationProvider()
        val repository = DefaultLocationRepository(provider)
        val controller = LocationController(repository)

        assertFalse(repository.isActive)
        controller.acquire("explore")
        assertTrue(repository.isActive)
        assertTrue(provider.updatesActive)

        controller.release("explore")
        assertFalse(repository.isActive)
        assertFalse(provider.updatesActive)
    }

    @Test
    fun multipleTokensKeepLocationActiveUntilLastRelease() {
        val provider = FakeLocationProvider()
        val repository = DefaultLocationRepository(provider)
        val controller = LocationController(repository)

        controller.acquire("home")
        controller.acquire("explore")
        controller.release("home")
        assertTrue(repository.isActive)

        controller.release("explore")
        assertFalse(repository.isActive)
    }

    private class FakeLocationProvider : LocationProvider {
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
