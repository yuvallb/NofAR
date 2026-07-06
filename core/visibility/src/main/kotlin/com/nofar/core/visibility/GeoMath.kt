package com.nofar.core.visibility

import com.nofar.core.model.AppConfig
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

internal object GeoMath {
    fun initialBearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun destinationPoint(lat: Double, lon: Double, bearingDeg: Double, distanceM: Double): Pair<Double, Double> {
        val angularDistance = distanceM / AppConfig.EARTH_RADIUS_METERS
        val bearing = Math.toRadians(bearingDeg)
        val phi1 = Math.toRadians(lat)
        val lambda1 = Math.toRadians(lon)

        val phi2 =
            asin(
                sin(phi1) * cos(angularDistance) +
                    cos(phi1) * sin(angularDistance) * cos(bearing)
            )
        val lambda2 =
            lambda1 +
                atan2(
                    sin(bearing) * sin(angularDistance) * cos(phi1),
                    cos(angularDistance) - sin(phi1) * sin(phi2)
                )

        return Math.toDegrees(phi2) to Math.toDegrees(lambda2)
    }

    fun buildRaySampleCount(totalDistanceM: Double, stepM: Double): Int {
        if (totalDistanceM <= 0.0) return 1
        return maxOf(1, floor(totalDistanceM / stepM).toInt()) + 1
    }

    /**
     * Earth bulge between observer and target at [distanceFromObserverM] along a path of
     * [totalDistanceM], using effective Earth radius with atmospheric refraction.
     */
    fun earthBulgeM(distanceFromObserverM: Double, totalDistanceM: Double): Double {
        if (totalDistanceM <= 0.0) return 0.0
        val d = distanceFromObserverM.coerceIn(0.0, totalDistanceM)
        return d * (totalDistanceM - d) / (2.0 * AppConfig.EFFECTIVE_EARTH_RADIUS_METERS)
    }

    fun elevationAngleDeg(observerEyeM: Double, targetElevationM: Double, horizontalDistanceM: Double): Double {
        if (horizontalDistanceM <= 0.0) return 0.0
        return Math.toDegrees(atan2(targetElevationM - observerEyeM, horizontalDistanceM))
    }
}
