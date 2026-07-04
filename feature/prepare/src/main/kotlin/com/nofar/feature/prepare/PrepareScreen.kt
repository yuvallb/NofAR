package com.nofar.feature.prepare

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.component.NofARTopAppBar

@Composable
fun PrepareScreen(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        NofARTopAppBar(title = "Prepare")
        Text(
            text = "Draw a circular region and download OSM + DEM data.",
            modifier = Modifier.padding(16.dp),
        )
    }
}
