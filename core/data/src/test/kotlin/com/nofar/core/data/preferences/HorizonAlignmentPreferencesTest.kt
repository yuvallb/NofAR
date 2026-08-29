package com.nofar.core.data.preferences

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HorizonAlignmentPreferencesTest {
    @Test
    fun setAndReset_horizonOffsets_roundTrip() = runTest {
        val repository = InMemoryUserPreferencesRepository()

        repository.setHorizonAlignmentOffsets(azimuthOffsetDeg = 2.5f, pitchOffsetDeg = -1.25f)
        assertEquals(2.5f, repository.horizonAzimuthOffsetDeg.first())
        assertEquals(-1.25f, repository.horizonPitchOffsetDeg.first())

        repository.resetHorizonAlignmentOffsets()
        assertEquals(0f, repository.horizonAzimuthOffsetDeg.first())
        assertEquals(0f, repository.horizonPitchOffsetDeg.first())
    }

    private class InMemoryUserPreferencesRepository : UserPreferencesRepository {
        private val azimuthOffset = MutableStateFlow(0f)
        private val pitchOffset = MutableStateFlow(0f)

        override val wifiOnlyDownloads = kotlinx.coroutines.flow.flowOf(false)
        override val demCacheLimitBytes = kotlinx.coroutines.flow.flowOf(0L)
        override val showRawSensorOverlay = kotlinx.coroutines.flow.flowOf(false)
        override val keepRawGeoTiff = kotlinx.coroutines.flow.flowOf(false)
        override val simpleModeEnabled = kotlinx.coroutines.flow.flowOf(false)
        override val simpleModeDefaultsApplied = kotlinx.coroutines.flow.flowOf(false)
        override val preferredLabelLanguage =
            kotlinx.coroutines.flow.flowOf(com.nofar.core.model.LabelLanguage.DEFAULT)
        override val showHorizonOutline = kotlinx.coroutines.flow.flowOf(true)
        override val showLabelElevation = kotlinx.coroutines.flow.flowOf(false)
        override val horizonAzimuthOffsetDeg = azimuthOffset
        override val horizonPitchOffsetDeg = pitchOffset

        override suspend fun setWifiOnlyDownloads(enabled: Boolean) = Unit

        override suspend fun setDemCacheLimitBytes(bytes: Long) = Unit

        override suspend fun setShowRawSensorOverlay(enabled: Boolean) = Unit

        override suspend fun setKeepRawGeoTiff(enabled: Boolean) = Unit

        override suspend fun setSimpleModeEnabled(enabled: Boolean) = Unit

        override suspend fun markSimpleModeDefaultsApplied() = Unit

        override suspend fun setPreferredLabelLanguage(language: com.nofar.core.model.LabelLanguage) = Unit

        override suspend fun ensurePreferredLabelLanguageInitialized(detected: com.nofar.core.model.LabelLanguage) =
            Unit

        override suspend fun setShowHorizonOutline(enabled: Boolean) = Unit

        override suspend fun setHorizonAlignmentOffsets(azimuthOffsetDeg: Float, pitchOffsetDeg: Float) {
            azimuthOffset.value = azimuthOffsetDeg
            pitchOffset.value = pitchOffsetDeg
        }

        override suspend fun setShowLabelElevation(enabled: Boolean) = Unit
    }
}
