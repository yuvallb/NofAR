package com.nofar.core.data.preferences

import com.nofar.core.data.repository.RegionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class SimpleModeDefaultsInitializer
@Inject
constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val regionRepository: RegionRepository
) {
    suspend fun ensureApplied() {
        if (userPreferencesRepository.simpleModeDefaultsApplied.first()) return
        val hasRegions = regionRepository.observeAllRegions().first().isNotEmpty()
        userPreferencesRepository.setSimpleModeEnabled(!hasRegions)
        userPreferencesRepository.markSimpleModeDefaultsApplied()
    }
}
