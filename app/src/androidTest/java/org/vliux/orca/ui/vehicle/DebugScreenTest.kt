package org.vliux.orca.ui.vehicle

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DebugScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun debugScreenShowsWizardAction() {
        var wizardOpened = false

        composeRule.setContent {
            MaterialTheme {
                DebugScreen(
                    onBack = {},
                    onShowNewUserWizard = {
                        wizardOpened = true
                    },
                )
            }
        }

        composeRule.onNodeWithText("Show new user wizard").assertIsDisplayed()
        composeRule.onNodeWithText("Show new user wizard").performClick()

        assertTrue(wizardOpened)
    }
}
