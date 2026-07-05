package com.nofar.core.ui.compass

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.model.CompassCalibrationState
import com.nofar.core.ui.R

@Composable
fun CompassCalibrationHint(calibrationState: CompassCalibrationState, modifier: Modifier = Modifier) {
    if (calibrationState != CompassCalibrationState.NEEDS_CALIBRATION) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NofARColors.ArOverlayBackground
    ) {
        Text(
            text = stringResource(R.string.compass_calibration_hint),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = NofARColors.ArAccent,
            textAlign = TextAlign.Center
        )
    }
}
