package com.nofar.core.designsystem.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.component.NofARPrimaryButton
import com.nofar.core.designsystem.component.NofARSecondaryOutlinedButton
import com.nofar.core.designsystem.component.NofARStatusBadge
import com.nofar.core.designsystem.component.NofARWarningBanner
import com.nofar.core.model.DownloadStatus

@Preview(name = "NofAR Theme", showBackground = true, backgroundColor = 0xFF1E1E1E)
@Composable
private fun NofARThemePreview() {
    NofARTheme {
        NofARThemePreviewContent()
    }
}

@Composable
private fun NofARThemePreviewContent() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "NofAR",
            style = MaterialTheme.typography.headlineSmall,
            color = NofARColors.PrimaryYellow
        )
        NofARStatusBadge(status = DownloadStatus.READY, modifier = Modifier.padding(vertical = 8.dp))
        NofARPrimaryButton(text = "ENTER EXPLORE", onClick = {}, modifier = Modifier.padding(vertical = 4.dp))
        NofARSecondaryOutlinedButton(text = "+ ADD REGION", onClick = {}, modifier = Modifier.padding(vertical = 4.dp))
        NofARWarningBanner(message = "Outside Active Region Boundary")
    }
}
