package org.vliux.orca.ui.vehicle

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.vliux.orca.settings.OpenAiApiKeyAccessState
import org.vliux.orca.settings.OpenAiApiKeyStore
import org.vliux.orca.settings.OpenAiApiKeyMutationResult

class OnboardingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun welcomeScreenShowsThirdPartyAiDisclosure() {
        composeRule.setContent {
            MaterialTheme {
                OnboardingWelcomeScreen(
                    onNext = {},
                )
            }
        }

        composeRule.onNodeWithText("Welcome to Orca").assertIsDisplayed()
        composeRule.onNodeWithText("Use third-party AI only when you want document analysis.").assertIsDisplayed()
    }

    @Test
    fun apiKeyScreenShowsLocalStorageAndAuthMessaging() {
        composeRule.setContent {
            MaterialTheme {
                OnboardingApiKeyScreen(
                    openAiApiKeyStore = FakeOnboardingOpenAiApiKeyStore(),
                    onBack = {},
                    onSkip = {},
                    onSaved = {},
                )
            }
        }

        composeRule.onNodeWithTag(OnboardingApiKeyFieldTestTag).assertIsDisplayed()
        composeRule.onNodeWithText("Your key is encrypted and saved in local app storage.").assertIsDisplayed()
        composeRule.onNodeWithText("Skip for now").assertIsDisplayed()
    }
}

private class FakeOnboardingOpenAiApiKeyStore(
    initialApiKey: String = "",
    initialAccessState: OpenAiApiKeyAccessState = if (initialApiKey.isBlank()) {
        OpenAiApiKeyAccessState.NOT_STORED
    } else {
        OpenAiApiKeyAccessState.UNLOCKED
    },
) : OpenAiApiKeyStore {
    private val state = MutableStateFlow(initialApiKey)
    private val accessState = MutableStateFlow(initialAccessState)

    override fun observeApiKey() = state

    override fun observeAccessState() = accessState

    override fun currentApiKey(): String = state.value

    override suspend fun saveApiKey(
        activity: androidx.fragment.app.FragmentActivity,
        apiKey: String,
    ): OpenAiApiKeyMutationResult {
        state.value = apiKey
        accessState.value = OpenAiApiKeyAccessState.UNLOCKED
        return OpenAiApiKeyMutationResult.SAVED
    }

    override suspend fun unlockApiKey(activity: androidx.fragment.app.FragmentActivity): OpenAiApiKeyMutationResult {
        return OpenAiApiKeyMutationResult.UNLOCKED
    }

    override suspend fun clearApiKey(): OpenAiApiKeyMutationResult {
        state.value = ""
        accessState.value = OpenAiApiKeyAccessState.NOT_STORED
        return OpenAiApiKeyMutationResult.CLEARED
    }

    override fun hasStoredApiKey(): Boolean = state.value.isNotBlank()
}
