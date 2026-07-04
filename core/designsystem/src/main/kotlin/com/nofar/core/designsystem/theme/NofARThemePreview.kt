package com.nofar.core.designsystem.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.component.NofARWarningBanner

@Preview(name = "Light", showBackground = true)
@Composable
private fun NofARThemeLightPreview() {
    NofARTheme(darkTheme = false) {
        NofARThemePreviewContent()
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun NofARThemeDarkPreview() {
    NofARTheme(darkTheme = true) {
        NofARThemePreviewContent()
    }
}

@Composable
private fun NofARThemePreviewContent() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "NofAR Theme",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        NofARWarningBanner(message = "Sample warning banner")
    }
}
