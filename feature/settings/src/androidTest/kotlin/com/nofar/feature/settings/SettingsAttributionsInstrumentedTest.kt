package com.nofar.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nofar.core.designsystem.theme.NofARTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAttributionsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun attributionsSection_containsOsmAndCopernicusStrings() {
        composeRule.setContent {
            NofARTheme {
                SettingsLegalSection()
            }
        }

        composeRule.onNodeWithTag("osm_attribution").assertIsDisplayed()
        composeRule.onNodeWithText("© OpenStreetMap contributors").assertIsDisplayed()
        composeRule.onNodeWithTag("copernicus_attribution").assertIsDisplayed()
        composeRule.onNodeWithText("Copernicus DEM, ESA / Airbus").assertIsDisplayed()
    }

    @Test
    fun aboutSection_showsVersionAndApacheNotice() {
        composeRule.setContent {
            NofARTheme {
                SettingsAboutSection()
            }
        }

        composeRule.onNodeWithTag("app_version").assertIsDisplayed()
        composeRule.onNodeWithTag("apache_license").assertIsDisplayed()
        composeRule.onNodeWithText("Licensed under Apache License 2.0").assertIsDisplayed()
    }
}
