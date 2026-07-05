package com.nofar.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nofar.core.designsystem.theme.NofARColors

@Composable
fun NofARApp() {
    val navController = androidx.navigation.compose.rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NofARColors.Background
    ) {
        NofARNavHost(navController = navController)
    }
}
