package com.nofar.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nofar.core.designsystem.R
import com.nofar.core.designsystem.theme.NofARColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NofARHomeTopBar(modifier: Modifier = Modifier, onSettingsClick: () -> Unit, settingsIcon: @Composable () -> Unit) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.app_brand),
                style = MaterialTheme.typography.headlineSmall,
                color = NofARColors.PrimaryYellow,
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            NofARIconActionButton(onClick = onSettingsClick, content = settingsIcon)
        },
        colors =
        TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = NofARColors.Background,
            titleContentColor = NofARColors.PrimaryYellow
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NofARBackTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable () -> Unit = {}
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = NofARColors.TextPrimary
            )
        },
        navigationIcon = {
            NofARIconActionButton(onClick = onNavigateBack, content = navigationIcon)
        },
        actions = { actions() },
        colors =
        TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = NofARColors.Background,
            titleContentColor = NofARColors.TextPrimary
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NofARTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {}
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = NofARColors.TextPrimary
            )
        },
        navigationIcon = navigationIcon,
        actions = { actions() },
        colors =
        TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = NofARColors.Background,
            titleContentColor = NofARColors.TextPrimary
        )
    )
}

@Composable
fun NofARIconActionButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier =
        Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.IconButton(onClick = onClick) {
            content()
        }
    }
}

@Composable
fun NofARPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors =
        ButtonDefaults.buttonColors(
            containerColor = NofARColors.PrimaryYellow,
            contentColor = NofARColors.OnPrimaryYellow,
            disabledContainerColor = NofARColors.SurfaceVariant,
            disabledContentColor = NofARColors.TextCaption
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun NofARSecondaryOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, NofARColors.PrimaryYellow),
        colors =
        ButtonDefaults.outlinedButtonColors(
            contentColor = NofARColors.PrimaryYellow,
            disabledContentColor = NofARColors.TextCaption
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun NofARLoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = NofARColors.PrimaryYellow)
    }
}

@Composable
fun NofAREmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = NofARColors.TextSecondary
        )
    }
}

@Composable
fun NofARWarningBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NofARColors.WarningBanner
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
