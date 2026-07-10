package org.vliux.orca.ui.vehicle

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.vliux.orca.settings.OpenAiApiKeyAccessState
import org.vliux.orca.settings.OpenAiApiKeyStore
import org.vliux.orca.settings.OpenAiApiKeyMutationResult

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsScreenShowsGuideAndReportsAuthUnavailableWithoutActivityHost() {
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
        composeRule.onNodeWithText("Set up biometric or device credentials to unlock the stored API key.").assertIsDisplayed()
    }
}

private class FakeOpenAiApiKeyStore : OpenAiApiKeyStore {
    private val state = MutableStateFlow("")
    private val accessState = MutableStateFlow(OpenAiApiKeyAccessState.NOT_STORED)

    override fun observeApiKey() = state

    override fun observeAccessState() = accessState

    override fun currentApiKey(): String = state.value

    override suspend fun saveApiKey(activity: FragmentActivity, apiKey: String): OpenAiApiKeyMutationResult {
        state.value = apiKey
        accessState.value = OpenAiApiKeyAccessState.UNLOCKED
        return OpenAiApiKeyMutationResult.SAVED
    }

    override suspend fun unlockApiKey(activity: FragmentActivity): OpenAiApiKeyMutationResult {
        accessState.value = if (state.value.isBlank()) OpenAiApiKeyAccessState.NOT_STORED else OpenAiApiKeyAccessState.UNLOCKED
        return OpenAiApiKeyMutationResult.UNLOCKED
    }

    override suspend fun clearApiKey(): OpenAiApiKeyMutationResult {
        state.value = ""
        accessState.value = OpenAiApiKeyAccessState.NOT_STORED
        return OpenAiApiKeyMutationResult.CLEARED
    }

    override fun hasStoredApiKey(): Boolean = state.value.isNotBlank()
}
