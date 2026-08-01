package com.nofar.feature.explore

data class ExploreHereUi(val placeName: String? = null, val peakName: String? = null, val peakElevationM: Int? = null) {
    val isEmpty: Boolean
        get() = placeName == null && peakName == null
}
