package com.nofar.core.designsystem.util

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object NofARFormatters {
    private val numberFormat = NumberFormat.getIntegerInstance(Locale.getDefault())

    fun formatCount(value: Int): String = numberFormat.format(value)

    fun formatMegabytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            "%.1f GB".format(Locale.US, mb / 1024.0)
        } else if (mb >= 10.0) {
            "%.0f MB".format(Locale.US, mb)
        } else {
            "%.1f MB".format(Locale.US, mb)
        }
    }

    fun formatCoordinate(value: Double): String = "%.2f".format(Locale.US, value)

    fun formatRadiusKm(radiusM: Double): String = "${(radiusM / 1000.0).roundToInt()} km"

    fun formatTimestamp(instant: Instant?): String {
        if (instant == null) return "—"
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }
}
