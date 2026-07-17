package com.nofar.core.data.preferences

import com.nofar.core.data.repository.RegionRepository
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.LabelLanguage
import com.nofar.core.model.Region
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
        val regions = FakeRegionRepository(emptyList())
        SimpleModeDefaultsInitializer(prefs, regions).ensureApplied()

        assertTrue(prefs.simpleModeEnabled.first())
        assertTrue(prefs.simpleModeDefaultsApplied.first())
    }

    @Test
    fun existingRegions_defaultsSimpleModeDisabled() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val regions = FakeRegionRepository(listOf(sampleRegion()))
        SimpleModeDefaultsInitializer(prefs, regions).ensureApplied()

        assertFalse(prefs.simpleModeEnabled.first())
    }

    @Test
    fun defaultsApplied_onlyOnce() = runTest {
        val prefs = FakeUserPreferencesRepository()
        val regions = FakeRegionRepository(listOf(sampleRegion()))
        val initializer = SimpleModeDefaultsInitializer(prefs, regions)
        initializer.ensureApplied()
        prefs.setSimpleModeEnabled(true)
        initializer.ensureApplied()

        assertTrue(prefs.simpleModeEnabled.first())
    }

    private fun sampleRegion(): Region = Region(
        id = UUID.randomUUID(),
        name = "Existing",
        centerLat = 32.0,
        centerLon = 35.0,
        radiusM = 10_000.0,
        minLat = 31.9,
        maxLat = 32.1,
        minLon = 34.9,
        maxLon = 35.1,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        downloadStatus = DownloadStatus.READY,
        downloadProgressPct = 100,
        osmDatasetVersion = null,
        estimatedSizeBytes = 1L,
        entityCount = 1
    )

    private class FakeUserPreferencesRepository : UserPreferencesRepository {
        private val simpleMode = MutableStateFlow(false)
        private val defaultsApplied = MutableStateFlow(false)

        override val wifiOnlyDownloads: Flow<Boolean> = MutableStateFlow(false)
        override val demCacheLimitBytes: Flow<Long> = MutableStateFlow(0L)
        override val showRawSensorOverlay: Flow<Boolean> = MutableStateFlow(false)
        override val keepRawGeoTiff: Flow<Boolean> = MutableStateFlow(false)
        override val simpleModeEnabled: Flow<Boolean> = simpleMode
        override val simpleModeDefaultsApplied: Flow<Boolean> = defaultsApplied
        override val preferredLabelLanguage: Flow<LabelLanguage> = MutableStateFlow(LabelLanguage.DEFAULT)

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

        override suspend fun setPreferredLabelLanguage(language: LabelLanguage) = Unit
    }

    private class FakeRegionRepository(private val regions: List<Region>) : RegionRepository {
        override fun observeAllRegions(): Flow<List<Region>> = MutableStateFlow(regions)

        override suspend fun getRegion(id: UUID): Region? = regions.firstOrNull { it.id == id }

        override suspend fun createRegion(region: Region) = Unit

        override suspend fun updateRegion(region: Region) = Unit

        override suspend fun updateRegionName(id: UUID, name: String) = Unit

        override suspend fun deleteRegion(id: UUID) = Unit

        override suspend fun regionsContainingPoint(lat: Double, lon: Double): List<Region> = emptyList()

        override suspend fun updateDownloadStatus(
            id: UUID,
            status: DownloadStatus,
            progressPct: Int,
            osmDatasetVersion: Instant?,
            entityCount: Int?
        ) = Unit

        override suspend fun hasActiveDownload(): Boolean = false

        override suspend fun findDownloadingRegion(): Region? = null
    }
}
