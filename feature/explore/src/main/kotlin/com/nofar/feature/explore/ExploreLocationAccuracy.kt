package com.nofar.feature.explore

import com.nofar.core.model.AppConfig

object ExploreLocationAccuracy {
    fun isDegraded(
        accuracyMeters: Float,
        thresholdMeters: Float = AppConfig.EXPLORE_LOCATION_ACCURACY_THRESHOLD_METERS
    ): Boolean = accuracyMeters > thresholdMeters
}
