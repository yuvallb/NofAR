package com.nofar.core.data.preferences

import com.nofar.core.model.LabelLanguage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On first launch, seeds preferred OSM label language from the device locale when it is one of
 * the supported options (Hebrew, Arabic, English, Russian); otherwise [LabelLanguage.DEFAULT].
 */
@Singleton
class PreferredLabelLanguageInitializer
@Inject
constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend fun ensureApplied(deviceLanguageCode: String) {
        val detected = LabelLanguage.fromDeviceLanguageCode(deviceLanguageCode)
        userPreferencesRepository.ensurePreferredLabelLanguageInitialized(detected)
    }
}
