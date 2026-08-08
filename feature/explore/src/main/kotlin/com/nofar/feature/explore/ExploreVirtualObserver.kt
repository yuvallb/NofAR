package com.nofar.feature.explore

import com.nofar.core.model.AppConfig
import com.nofar.core.model.UserLocation

internal fun virtualObserverLocation(session: VirtualExploreSession): UserLocation = UserLocation(
    latitude = session.observerLat,
    longitude = session.observerLon,
    altitudeMeters = null,
    accuracyMeters = AppConfig.VIRTUAL_OBSERVER_ACCURACY_METERS,
    timestampMillis = System.currentTimeMillis()
)
