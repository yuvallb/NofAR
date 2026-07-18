package com.nofar.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val NofARColorScheme =
    darkColorScheme(
        primary = NofARColors.PrimaryYellow,
        onPrimary = NofARColors.OnPrimaryYellow,
        secondary = NofARColors.ArNeonGreen,
        onSecondary = Color.Black,
        tertiary = NofARColors.StatusDownloading,
        background = NofARColors.Background,
        onBackground = NofARColors.TextPrimary,
        surface = NofARColors.Surface,
        onSurface = NofARColors.TextPrimary,
        surfaceVariant = NofARColors.SurfaceVariant,
        onSurfaceVariant = NofARColors.TextSecondary,
        error = NofARColors.ErrorDestructive,
        onError = Color.White,
        outline = NofARColors.PrimaryYellow
    )

@Composable
fun NofARTheme(content: @Composable () -> Unit) {
    // Explore AR overlays use absolute screen coordinates; keep UI LTR regardless of locale.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        MaterialTheme(
            colorScheme = NofARColorScheme,
            typography = NofARTypography,
            content = content
        )
    }
}
