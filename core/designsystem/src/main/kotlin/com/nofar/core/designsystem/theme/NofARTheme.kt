package com.nofar.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = NofARColors.PrimaryAction,
    onPrimary = Color.White,
    secondary = NofARColors.YouAreHere,
    tertiary = NofARColors.DownloadProgress,
    error = NofARColors.WarningBanner
)

private val DarkColorScheme = darkColorScheme(
    primary = NofARColors.PrimaryActionDark,
    onPrimary = Color(0xFF1B5E20),
    secondary = NofARColors.YouAreHereDark,
    tertiary = NofARColors.DownloadProgressDark,
    error = NofARColors.WarningBannerDark
)

@Composable
fun NofARTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NofARTypography,
        content = content
    )
}
