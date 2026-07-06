package com.nofar.core.sensors

import kotlin.math.roundToInt

data class CompassRibbonLabels(val headings: List<String>, val centerHeading: String)

object CompassRibbonFormatter {
    private val cardinalLabels =
        listOf(
            0 to "N",
            45 to "NE",
            90 to "E",
            135 to "SE",
            180 to "S",
            225 to "SW",
            270 to "W",
            315 to "NW"
        )

    fun fromAzimuth(azimuthDeg: Float): CompassRibbonLabels {
        val center = azimuthDeg.roundToInt()
        val normalizedCenter = ((center % 360) + 360) % 360
        val offsets = listOf(-45, -30, -15, 0, 15, 30, 45)
        val headings =
            offsets.map { offset ->
                val angle = (normalizedCenter + offset + 360) % 360
                cardinalLabel(angle) ?: angle.toString()
            }
        val centerHeading = cardinalLabel(normalizedCenter) ?: normalizedCenter.toString()
        return CompassRibbonLabels(headings = headings, centerHeading = centerHeading)
    }

    private fun cardinalLabel(angle: Int): String? = cardinalLabels.firstOrNull { (deg, _) -> deg == angle }?.second
}
