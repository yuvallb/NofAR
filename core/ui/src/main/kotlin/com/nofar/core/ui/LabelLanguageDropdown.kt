package com.nofar.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.model.LabelLanguage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelLanguageDropdown(
    selected: LabelLanguage,
    enabled: Boolean,
    onSelected: (LabelLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LabelLanguage.entries.forEach { language ->
                val isSelected = language == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { if (enabled) onSelected(language) },
                    enabled = enabled,
                    label = { Text(stringResource(language.labelStringRes())) },
                    colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NofARColors.PrimaryYellow,
                        selectedLabelColor = NofARColors.TextPrimary,
                        containerColor = NofARColors.SurfaceVariant,
                        labelColor = NofARColors.TextPrimary,
                        disabledContainerColor = NofARColors.SurfaceVariant,
                        disabledLabelColor = NofARColors.TextSecondary,
                        disabledSelectedContainerColor = NofARColors.PrimaryYellow.copy(alpha = 0.4f)
                    )
                )
            }
        }
    }
}
