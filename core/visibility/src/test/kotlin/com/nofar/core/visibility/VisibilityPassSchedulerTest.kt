package com.nofar.core.visibility

import com.google.common.truth.Truth.assertThat
import com.nofar.core.common.DispatcherProvider
import com.nofar.core.data.preferences.UserPreferencesRepository
import com.nofar.core.model.DownloadStatus
import com.nofar.core.model.GeoEntity
import com.nofar.core.model.LabelLanguage
import com.nofar.core.model.Region
import com.nofar.core.model.UserLocation
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * H-P1-17: a computed [HorizonProfile] reaches Explore via the scheduler's Flow, and the horizon
 * preference gates the sweep flag passed to the computer (H-P1-11).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VisibilityPassSchedulerTest {
    private val knownProfile =
        HorizonProfile(azimuthStepDeg = 2f, elevationAnglesDeg = FloatArray(180) { 1f })

    @Test
    fun computedProfileAndEyeSource_arePublishedToFlows() = runTest {
        val computer = FakeComputer(resultWith(knownProfile, ObserverEyeSource.DEM))
        val scheduler = scheduler(computer, showHorizonOutline = true)

        scheduler.start(this)
        scheduler.setActiveRegions(listOf(sampleRegion()))
        advanceUntilIdle()

        assertThat(scheduler.horizonProfile.value).isEqualTo(knownProfile)
        assertThat(scheduler.horizonEyeSource.value).isEqualTo(ObserverEyeSource.DEM)
        assertThat(computer.lastComputeHorizonProfile).isTrue()
        scheduler.stop()
    }

    @Test
    fun horizonPreferenceOff_passesSkipFlagToComputer() = runTest {
        val computer = FakeComputer(resultWith(horizonProfile = null, eyeSource = null))
        val scheduler = scheduler(computer, showHorizonOutline = false)

        scheduler.start(this)
        scheduler.setActiveRegions(listOf(sampleRegion()))
        advanceUntilIdle()

        assertThat(computer.lastComputeHorizonProfile).isFalse()
        assertThat(scheduler.horizonProfile.value).isNull()
        scheduler.stop()
    }

    private fun dispatcherProvider(dispatcher: CoroutineDispatcher): DispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }

    private fun TestScope.scheduler(
        computer: RegionVisibilityComputer,
        showHorizonOutline: Boolean
    ): VisibilityPassScheduler = VisibilityPassScheduler(
        visibilityUseCase = computer,
        userPreferencesRepository = FakeUserPreferencesRepository(showHorizonOutline),
        dispatchers = dispatcherProvider(StandardTestDispatcher(testScheduler))
    ).also { scheduler ->
        scheduler.configureObserverLocation(kotlinx.coroutines.flow.flowOf(sampleLocation()))
        scheduler.seedObserverLocation(sampleLocation())
    }

    @Test
    fun hereContext_isPublishedToFlow() = runTest {
        val place =
            GeoEntity(
                id = "place",
                osmType = com.nofar.core.model.OsmType.NODE,
                name = "Village",
                type = com.nofar.core.model.GeoEntityType.VILLAGE,
                lat = 32.5,
                lon = 35.5,
                elevation = null,
                elevationSource = null,
                lastSeenAt = Instant.EPOCH,
                footprintRadiusM = 1_000.0
            )
        val here = HereContext(place = place)
        val computer = FakeComputer(resultWith(horizonProfile = null, eyeSource = null, hereContext = here))
        val scheduler = scheduler(computer, showHorizonOutline = false)

        scheduler.start(this)
        scheduler.setActiveRegions(listOf(sampleRegion()))
        advanceUntilIdle()

        assertThat(scheduler.hereContext.value).isEqualTo(here)
        scheduler.stop()
    }

    private fun resultWith(
        horizonProfile: HorizonProfile?,
        eyeSource: ObserverEyeSource?,
        hereContext: HereContext = HereContext()
    ): VisibilityResult = VisibilityResult(
        entities = emptyList(),
        computationTimeMs = 1L,
        horizonProfile = horizonProfile,
        horizonEyeSource = eyeSource,
        hereContext = hereContext
    )

    private fun sampleLocation(): UserLocation = UserLocation(
        latitude = 32.5,
        longitude = 35.5,
        altitudeMeters = null,
        accuracyMeters = 5f,
        timestampMillis = 1_000L
    )

    private fun sampleRegion(): Region = Region(
        id = UUID.randomUUID(),
        name = "Region",
        centerLat = 32.5,
        centerLon = 35.5,
        radiusM = 10_000.0,
        minLat = 32.4,
        maxLat = 32.6,
        minLon = 35.4,
        maxLon = 35.6,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        downloadStatus = DownloadStatus.READY,
        downloadProgressPct = 100,
        osmDatasetVersion = null,
        estimatedSizeBytes = 1L,
        entityCount = 1
    )

    @Test
    fun fixedObserverFlow_usesConfiguredLocationForPass() = runTest {
        val virtualLocation =
            UserLocation(
                latitude = 31.0,
                longitude = 35.0,
                altitudeMeters = null,
                accuracyMeters = 5f,
                timestampMillis = 2_000L
            )
        val computer = RecordingComputer()
        val scheduler =
            VisibilityPassScheduler(
                visibilityUseCase = computer,
                userPreferencesRepository = FakeUserPreferencesRepository(showHorizon = false),
                dispatchers = dispatcherProvider(StandardTestDispatcher(testScheduler))
            )
        scheduler.configureObserverLocation(kotlinx.coroutines.flow.flowOf(virtualLocation))
        scheduler.seedObserverLocation(virtualLocation)
        scheduler.start(this)
        scheduler.setActiveRegions(listOf(sampleRegion()))
        advanceUntilIdle()

        assertThat(computer.lastLocation?.latitude).isEqualTo(31.0)
        assertThat(computer.lastLocation?.longitude).isEqualTo(35.0)
        scheduler.stop()
    }

    @Test
    fun requestPass_forcesComputeEvenWithinInterval() = runTest {
        val computer = RecordingComputer()
        val scheduler = scheduler(computer, showHorizonOutline = false)
        scheduler.start(this)
        scheduler.setActiveRegions(listOf(sampleRegion()))
        advanceUntilIdle()
        val afterFirst = computer.computeCount

        scheduler.requestPass(force = true)
        advanceUntilIdle()

        assertThat(computer.computeCount).isEqualTo(afterFirst + 1)
        scheduler.stop()
    }

    @Test
    fun periodicRefresh_ticksStationaryVirtualObserver() = runTest {
        val virtualLocation =
            UserLocation(
                latitude = 31.0,
                longitude = 35.0,
                altitudeMeters = null,
                accuracyMeters = 5f,
                timestampMillis = 1_000L
            )
        val computer = RecordingComputer()
        val scheduler =
            VisibilityPassScheduler(
                visibilityUseCase = computer,
                userPreferencesRepository = FakeUserPreferencesRepository(showHorizon = false),
                dispatchers = dispatcherProvider(StandardTestDispatcher(testScheduler))
            )
        scheduler.configureObserverLocation(
            observerFlow = kotlinx.coroutines.flow.flowOf(virtualLocation),
            periodicRefresh = true
        )
        scheduler.seedObserverLocation(virtualLocation)
        scheduler.start(this)
        scheduler.setActiveRegions(listOf(sampleRegion()))
        testScheduler.runCurrent()
        val afterFirst = computer.computeCount
        assertThat(afterFirst).isAtLeast(1)

        testScheduler.advanceTimeBy(2_100L)
        testScheduler.runCurrent()

        assertThat(computer.computeCount).isAtLeast(afterFirst + 1)
        scheduler.stop()
    }

    private class RecordingComputer : RegionVisibilityComputer {
        var lastLocation: UserLocation? = null
        var computeCount: Int = 0

        override suspend fun computeForRegions(
            regions: List<Region>,
            location: UserLocation,
            computeHorizonProfile: Boolean
        ): VisibilityResult {
            lastLocation = location
            computeCount += 1
            return VisibilityResult(
                entities = emptyList(),
                computationTimeMs = 1L,
                warnings = emptySet(),
                hereContext = HereContext()
            )
        }
    }

    private class FakeComputer(private val result: VisibilityResult) : RegionVisibilityComputer {
        var lastComputeHorizonProfile: Boolean? = null

        override suspend fun computeForRegions(
            regions: List<Region>,
            location: UserLocation,
            computeHorizonProfile: Boolean
        ): VisibilityResult {
            lastComputeHorizonProfile = computeHorizonProfile
            return result
        }
    }

    private class FakeUserPreferencesRepository(showHorizon: Boolean) : UserPreferencesRepository {
        override val wifiOnlyDownloads: Flow<Boolean> = MutableStateFlow(false)
        override val demCacheLimitBytes: Flow<Long> = MutableStateFlow(0L)
        override val showRawSensorOverlay: Flow<Boolean> = MutableStateFlow(false)
        override val keepRawGeoTiff: Flow<Boolean> = MutableStateFlow(false)
        override val simpleModeEnabled: Flow<Boolean> = MutableStateFlow(false)
        override val simpleModeDefaultsApplied: Flow<Boolean> = MutableStateFlow(false)
        override val preferredLabelLanguage: Flow<LabelLanguage> = MutableStateFlow(LabelLanguage.DEFAULT)
        override val showHorizonOutline: Flow<Boolean> = MutableStateFlow(showHorizon)
        override val showLabelElevation: Flow<Boolean> = MutableStateFlow(false)

        override suspend fun setWifiOnlyDownloads(enabled: Boolean) = Unit

        override suspend fun setDemCacheLimitBytes(bytes: Long) = Unit

        override suspend fun setShowRawSensorOverlay(enabled: Boolean) = Unit

        override suspend fun setKeepRawGeoTiff(enabled: Boolean) = Unit

        override suspend fun setSimpleModeEnabled(enabled: Boolean) = Unit

        override suspend fun markSimpleModeDefaultsApplied() = Unit

        override suspend fun setPreferredLabelLanguage(language: LabelLanguage) = Unit

        override suspend fun ensurePreferredLabelLanguageInitialized(detected: LabelLanguage) = Unit

        override suspend fun setShowHorizonOutline(enabled: Boolean) = Unit

        override suspend fun setShowLabelElevation(enabled: Boolean) = Unit
    }
}
