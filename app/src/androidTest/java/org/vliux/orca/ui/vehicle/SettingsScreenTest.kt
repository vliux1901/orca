package org.vliux.orca.ui.vehicle

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.vliux.orca.settings.OpenAiApiKeyStore

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsScreenShowsGuideAndSavesApiKey() {
        val store = FakeOpenAiApiKeyStore()

        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    openAiApiKeyStore = store,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Bring your own OpenAI API key").assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsApiKeyFieldTestTag).performTextReplacement("sk-test")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Saved API key.").assertIsDisplayed()
    }
}

private class FakeOpenAiApiKeyStore : OpenAiApiKeyStore {
    private val state = MutableStateFlow("")

    override fun observeApiKey() = state

    override fun currentApiKey(): String = state.value

    override suspend fun saveApiKey(apiKey: String) {
        state.value = apiKey
    }
}
