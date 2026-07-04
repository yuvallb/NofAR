package com.nofar.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.component.NofARTopAppBar

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        NofARTopAppBar(
            title = "Settings",
            navigationIcon = {
                TextButton(onClick = onNavigateBack) {
                    Text(text = "Back")
                }
            },
        )
        Text(
            text = "Storage, legal attributions, and advanced options.",
            modifier = Modifier.padding(16.dp),
        )
    }
}
