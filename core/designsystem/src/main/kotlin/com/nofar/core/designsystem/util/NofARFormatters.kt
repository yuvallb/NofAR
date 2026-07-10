package com.nofar.core.designsystem.util

import android.content.Context
import com.nofar.core.designsystem.R
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

object NofARFormatters {
    fun formatCount(context: Context, value: Int): String {
        val locale = context.resources.configuration.locales[0]
        return NumberFormat.getIntegerInstance(locale).format(value)
    }

    fun formatMegabytes(context: Context, bytes: Long): String {
        val locale = context.resources.configuration.locales[0]
        val numberFormat =
            NumberFormat.getNumberInstance(locale).apply {
                minimumFractionDigits = 0
                maximumFractionDigits = 1
            }
        val mb = bytes / (1024.0 * 1024.0)
        return when {
            mb >= 1024.0 -> {
                val gb = mb / 1024.0
                context.getString(R.string.format_size_gb, numberFormat.format(gb))
            }
            mb >= 10.0 -> {
                context.getString(R.string.format_size_mb_whole, numberFormat.format(mb.roundToInt()))
            }
            else -> {
                context.getString(R.string.format_size_mb_fraction, "%.1f".format(locale, mb))
            }
        }
    }

    fun formatCoordinate(context: Context, value: Double): String {
        val locale = context.resources.configuration.locales[0]
        return "%.2f".format(locale, value)
    }

    fun formatRadiusKm(context: Context, radiusM: Double): String =
        context.getString(R.string.format_radius_km, (radiusM / 1000.0).roundToInt())

    fun formatTimestamp(context: Context, instant: Instant?): String {
        if (instant == null) return context.getString(R.string.format_timestamp_unknown)
        val locale = context.resources.configuration.locales[0]
        val formatter =
            DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }
}
