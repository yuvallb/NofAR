package com.nofar.core.data.preferences

import com.nofar.core.model.LabelLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferredLabelLanguageInitializerTest {
    @Test
    fun freshInstall_hebrewDevice_seedsHebrew() = runTest {
        val prefs = FakeUserPreferencesRepository()
        PreferredLabelLanguageInitializer(prefs).ensureApplied("he")

        assertEquals(LabelLanguage.HEBREW, prefs.preferredLabelLanguage.first())
    }

    @Test
    fun freshInstall_unsupportedDevice_seedsDefault() = runTest {
        val prefs = FakeUserPreferencesRepository()
        PreferredLabelLanguageInitializer(prefs).ensureApplied("fr")

        assertEquals(LabelLanguage.DEFAULT, prefs.preferredLabelLanguage.first())
    }

    @Test
    fun alreadyChosen_doesNotOverwrite() = runTest {
        val prefs = FakeUserPreferencesRepository()
        prefs.setPreferredLabelLanguage(LabelLanguage.ENGLISH)
        PreferredLabelLanguageInitializer(prefs).ensureApplied("he")

        assertEquals(LabelLanguage.ENGLISH, prefs.preferredLabelLanguage.first())
    }

    @Test
    fun seedsOnlyOnce() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val initializer = PreferredLabelLanguageInitializer(prefs)
        initializer.ensureApplied("ru")
        initializer.ensureApplied("he")

        assertEquals(LabelLanguage.RUSSIAN, prefs.preferredLabelLanguage.first())
    }

    private class FakeUserPreferencesRepository : UserPreferencesRepository {
        private val storedLanguage = MutableStateFlow<LabelLanguage?>(null)

        override val wifiOnlyDownloads: Flow<Boolean> = MutableStateFlow(false)
        override val demCacheLimitBytes: Flow<Long> = MutableStateFlow(0L)
        override val showRawSensorOverlay: Flow<Boolean> = MutableStateFlow(false)
        override val keepRawGeoTiff: Flow<Boolean> = MutableStateFlow(false)
        override val simpleModeEnabled: Flow<Boolean> = MutableStateFlow(false)
        override val simpleModeDefaultsApplied: Flow<Boolean> = MutableStateFlow(false)
        override val preferredLabelLanguage: Flow<LabelLanguage> =
            storedLanguage.map { it ?: LabelLanguage.DEFAULT }
        override val showHorizonOutline: Flow<Boolean> = MutableStateFlow(true)
        override val horizonAzimuthOffsetDeg: Flow<Float> = MutableStateFlow(0f)
        override val horizonPitchOffsetDeg: Flow<Float> = MutableStateFlow(0f)
        override val showLabelElevation: Flow<Boolean> = MutableStateFlow(false)

        override suspend fun setWifiOnlyDownloads(enabled: Boolean) = Unit

        override suspend fun setDemCacheLimitBytes(bytes: Long) = Unit

        override suspend fun setShowRawSensorOverlay(enabled: Boolean) = Unit

        override suspend fun setKeepRawGeoTiff(enabled: Boolean) = Unit

        override suspend fun setSimpleModeEnabled(enabled: Boolean) = Unit

        override suspend fun markSimpleModeDefaultsApplied() = Unit

        override suspend fun setPreferredLabelLanguage(language: LabelLanguage) {
            storedLanguage.value = language
        }

        override suspend fun ensurePreferredLabelLanguageInitialized(detected: LabelLanguage) {
            if (storedLanguage.value == null) {
                storedLanguage.value = detected
            }
        }

        override suspend fun setShowHorizonOutline(enabled: Boolean) = Unit

        override suspend fun setHorizonAlignmentOffsets(azimuthOffsetDeg: Float, pitchOffsetDeg: Float) = Unit

        override suspend fun setShowLabelElevation(enabled: Boolean) = Unit
    }
}
