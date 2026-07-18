package com.nofar.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.nofar.core.designsystem.theme.NofARColors
import com.nofar.core.model.LabelLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelLanguageDropdown(
    selected: LabelLanguage,
    enabled: Boolean,
    onSelected: (LabelLanguage) -> Unit,
    modifier: Modifier = Modifier,
    darkForeground: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val foreground = if (darkForeground) PrepareOverlayDarkText else NofARColors.TextPrimary
    val secondary = if (darkForeground) PrepareOverlayDarkTextSecondary else NofARColors.TextSecondary

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = stringResource(selected.labelStringRes()),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NofARColors.PrimaryYellow,
                unfocusedBorderColor = if (darkForeground) PrepareOverlayDarkTextSecondary else NofARColors.TextCaption,
                focusedLabelColor = NofARColors.PrimaryYellow,
                unfocusedLabelColor = secondary,
                cursorColor = NofARColors.PrimaryYellow,
                focusedTextColor = foreground,
                unfocusedTextColor = foreground,
                disabledTextColor = secondary,
                disabledBorderColor = secondary,
                focusedTrailingIconColor = foreground,
                unfocusedTrailingIconColor = foreground,
                disabledTrailingIconColor = secondary
            ),
            modifier =
            Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = enabled)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            LabelLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(stringResource(language.labelStringRes())) },
                    onClick = {
                        onSelected(language)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Dark text for controls drawn over a bright OSM map basemap. */
val PrepareOverlayDarkText = Color(0xFF1A1A1A)
val PrepareOverlayDarkTextSecondary = Color(0xFF333333)
