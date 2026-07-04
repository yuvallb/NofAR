package com.nofar.feature.explore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.component.NofARTopAppBar

@Composable
fun ExploreScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        NofARTopAppBar(title = "Explore")
        Text(
            text = "Camera horizon view with terrain-aware labels (coming in Phase 5).",
            modifier = Modifier.padding(16.dp)
        )
    }
}
