package com.nofar.core.data.preferences

import com.nofar.core.data.repository.CoverageSetRepository
import com.nofar.core.model.CoverageSet
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.LabelLanguage
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimpleModeDefaultsTest {
    @Test
    fun freshInstall_defaultsSimpleModeEnabled() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val coverageSets = FakeCoverageSetRepository(emptyList())
        SimpleModeDefaultsInitializer(prefs, coverageSets).ensureApplied()

        assertTrue(prefs.simpleModeEnabled.first())
        assertTrue(prefs.simpleModeDefaultsApplied.first())
    }

    @Test
    fun existingCoverageSets_defaultsSimpleModeDisabled() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val coverageSets = FakeCoverageSetRepository(listOf(sampleCoverageSet()))
        SimpleModeDefaultsInitializer(prefs, coverageSets).ensureApplied()

        assertFalse(prefs.simpleModeEnabled.first())
    }

    @Test
    fun defaultsApplied_onlyOnce() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val coverageSets = FakeCoverageSetRepository(listOf(sampleCoverageSet()))
        val initializer = SimpleModeDefaultsInitializer(prefs, coverageSets)
        initializer.ensureApplied()
        prefs.setSimpleModeEnabled(true)
        initializer.ensureApplied()

        assertTrue(prefs.simpleModeEnabled.first())
    }

    private fun sampleCoverageSet(): CoverageSet {
        val now = Instant.now()
        return CoverageSet(
            id = UUID.randomUUID(),
            name = "Existing",
            createdAt = now,
            updatedAt = now,
            downloadStatus = DownloadStatus.READY,
            downloadProgressPct = 100,
            osmDatasetVersion = null,
            estimatedSizeBytes = 1L,
            entityCount = 1
        )
    }

    private class FakeUserPreferencesRepository : UserPreferencesRepository {
        private val simpleMode = MutableStateFlow(false)
        private val defaultsApplied = MutableStateFlow(false)

        override val wifiOnlyDownloads: Flow<Boolean> = MutableStateFlow(false)
        override val demCacheLimitBytes: Flow<Long> = MutableStateFlow(0L)
        override val showRawSensorOverlay: Flow<Boolean> = MutableStateFlow(false)
        override val keepRawGeoTiff: Flow<Boolean> = MutableStateFlow(false)
        override val simpleModeEnabled: Flow<Boolean> = simpleMode
        override val simpleModeDefaultsApplied: Flow<Boolean> = defaultsApplied
        override val demV4UpgradeApplied: Flow<Boolean> = MutableStateFlow(false)
        override val preferredLabelLanguage: Flow<LabelLanguage> = MutableStateFlow(LabelLanguage.DEFAULT)
        override val showHorizonOutline: Flow<Boolean> = MutableStateFlow(true)
        override val horizonAzimuthOffsetDeg: Flow<Float> = MutableStateFlow(0f)
        override val horizonPitchOffsetDeg: Flow<Float> = MutableStateFlow(0f)
        override val showLabelElevation: Flow<Boolean> = MutableStateFlow(false)

        override suspend fun setWifiOnlyDownloads(enabled: Boolean) = Unit

        override suspend fun setDemCacheLimitBytes(bytes: Long) = Unit

        override suspend fun setShowRawSensorOverlay(enabled: Boolean) = Unit

        override suspend fun setKeepRawGeoTiff(enabled: Boolean) = Unit

        override suspend fun setSimpleModeEnabled(enabled: Boolean) {
            simpleMode.value = enabled
        }

        override suspend fun markSimpleModeDefaultsApplied() {
            defaultsApplied.value = true
        }

        override suspend fun markDemV4UpgradeApplied() = Unit

        override suspend fun setPreferredLabelLanguage(language: LabelLanguage) = Unit

        override suspend fun ensurePreferredLabelLanguageInitialized(detected: LabelLanguage) = Unit

        override suspend fun setShowHorizonOutline(enabled: Boolean) = Unit

        override suspend fun setHorizonAlignmentOffsets(azimuthOffsetDeg: Float, pitchOffsetDeg: Float) = Unit

        override suspend fun setShowLabelElevation(enabled: Boolean) = Unit
    }

    private class FakeCoverageSetRepository(private val coverageSets: List<CoverageSet>) : CoverageSetRepository {
        override fun observeAllCoverageSets(): Flow<List<CoverageSet>> = MutableStateFlow(coverageSets)

        override suspend fun getCoverageSet(id: UUID): CoverageSet? = coverageSets.firstOrNull { it.id == id }

        override suspend fun createCoverageSet(coverageSet: CoverageSet) = Unit

        override suspend fun updateCoverageSet(coverageSet: CoverageSet) = Unit

        override suspend fun updateCoverageSetName(id: UUID, name: String) = Unit

        override suspend fun deleteCoverageSet(id: UUID) = Unit

        override suspend fun coverageSetsContainingPoint(lat: Double, lon: Double): List<CoverageSet> = emptyList()

        override suspend fun getCellIdsForCoverageSet(id: UUID): List<String> = emptyList()

        override suspend fun getCellIdsForCoverageSets(ids: List<UUID>): List<String> = emptyList()

        override suspend fun updateDownloadStatus(
            id: UUID,
            status: DownloadStatus,
            progressPct: Int,
            osmDatasetVersion: Instant?,
            entityCount: Int?
        ) = Unit

        override suspend fun hasActiveDownload(): Boolean = false

        override suspend fun findDownloadingCoverageSet(): CoverageSet? = null
    }
}
