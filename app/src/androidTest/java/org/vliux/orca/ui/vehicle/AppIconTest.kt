package org.vliux.orca.ui.vehicle

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test

class AppIconTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appIconRenders() {
        composeRule.setContent {
            MaterialTheme {
                AppIcon()
            }
        }

        composeRule.onNodeWithContentDescription("Orca app icon").assertIsDisplayed()
    }
}
