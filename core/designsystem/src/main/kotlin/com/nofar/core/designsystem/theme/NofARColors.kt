package com.nofar.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Outdoor-first palette aligned with [internal/ui_ux_guidelines.md] and screen mockups.
 */
object NofARColors {
    val Background = Color(0xFF1E1E1E)
    val Surface = Color(0xFF2A2A2A)
    val SurfaceVariant = Color(0xFF333333)

    val PrimaryYellow = Color(0xFFFFE838)
    val OnPrimaryYellow = Color(0xFF000000)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB0B0B0)
    val TextCaption = Color(0xFF808080)

    val StatusReady = Color(0xFF4CAF50)
    val StatusPartial = Color(0xFFFF9800)
    val StatusNotDownloaded = Color(0xFF808080)
    val StatusDownloading = Color(0xFF2196F3)

    val YouAreHere = PrimaryYellow
    val WarningBanner = Color(0xFFFF6D00)
    val ErrorDestructive = Color(0xFFF44336)

    val ArOverlayBackground = Color(0xCC000000)
    val ArAccent = Color(0xFFFFEB3B)
    val ArNeonGreen = Color(0xFF00E676)

    // Legacy aliases used by existing components
    val PrimaryAction = PrimaryYellow
    val DownloadProgress = StatusDownloading
    val WarningBannerLegacy = WarningBanner
    val YouAreHereLegacy = YouAreHere

    val PrimaryActionDark = PrimaryYellow
    val WarningBannerDark = WarningBanner
    val DownloadProgressDark = StatusDownloading
    val YouAreHereDark = YouAreHere
}
