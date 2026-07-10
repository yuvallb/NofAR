package com.nofar.core.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.nofar.core.common.text.UiText

@Composable
fun uiTextResource(text: UiText?): String? {
    if (text == null) return null
    return text.asString(LocalContext.current)
}

@Composable
fun uiTextResourceNotNull(text: UiText): String = text.asString(LocalContext.current)
