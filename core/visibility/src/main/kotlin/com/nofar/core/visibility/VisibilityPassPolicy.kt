package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import com.nofar.core.model.RegionBounds
import com.nofar.core.model.UserLocation

internal object VisibilityPassPolicy {
    const val NO_PASS_YET: Long = -1L

    fun shouldSchedulePass(
        location: UserLocation,
        lastPassLocation: UserLocation?,
        lastPassAtMillis: Long,
        force: Boolean
    ): Boolean = when {
        force -> true
        lastPassAtMillis < 0 -> true
        else -> {
            val movedEnough =
                lastPassLocation?.let { previous ->
                    RegionBounds.haversineDistanceM(
                        previous.latitude,
                        previous.longitude,
                        location.latitude,
                        location.longitude
                    ) >= AppConfig.VISIBILITY_REFRESH_DISTANCE_METERS
                } ?: false
            val elapsedEnough =
                location.timestampMillis - lastPassAtMillis >=
                    AppConfig.visibilityRefreshMaxInterval.inWholeMilliseconds
            movedEnough || elapsedEnough
        }
    }
}
