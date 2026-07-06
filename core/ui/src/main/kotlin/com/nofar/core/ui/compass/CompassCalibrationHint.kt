package com.nofar.core.ui.compass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    val transition = rememberInfiniteTransition(label = "figure8")
    val offsetX by transition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec =
        infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "figure8Offset"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NofARColors.ArOverlayBackground
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "∞",
                modifier = Modifier.offset(x = offsetX.dp),
                color = NofARColors.ArAccent
            )
            Text(
                text = stringResource(R.string.compass_calibration_hint),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = NofARColors.ArAccent,
                textAlign = TextAlign.Start
            )
        }
    }
}
