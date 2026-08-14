package com.nofar.feature.prepare

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable

/**
 * Insets that bottom-anchored controls must keep clear so the navigation bar, gesture handle,
 * display cutout or software keyboard never covers them on edge-to-edge devices.
 */
@Composable
internal fun bottomControlsInsets(): WindowInsets =
    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
