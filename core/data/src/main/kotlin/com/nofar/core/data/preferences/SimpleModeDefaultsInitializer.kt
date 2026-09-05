package com.nofar.core.data.preferences

import com.nofar.core.data.repository.CoverageSetRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class SimpleModeDefaultsInitializer
@Inject
constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val coverageSetRepository: CoverageSetRepository
) {
    suspend fun ensureApplied() {
        if (userPreferencesRepository.simpleModeDefaultsApplied.first()) return
        val hasCoverageSets = coverageSetRepository.observeAllCoverageSets().first().isNotEmpty()
        userPreferencesRepository.setSimpleModeEnabled(!hasCoverageSets)
        userPreferencesRepository.markSimpleModeDefaultsApplied()
    }
}
