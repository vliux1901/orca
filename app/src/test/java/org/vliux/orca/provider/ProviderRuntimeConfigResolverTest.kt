package org.vliux.orca.provider

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vliux.orca.settings.OpenAiApiKeyStore

class ProviderRuntimeConfigResolverTest {
    @Test
    fun authSourceMapsKnownValues() {
        assertEquals(ProviderAuthSource.USER_SETTINGS, ProviderAuthSource.fromRaw("user_settings"))
        assertEquals(ProviderAuthSource.NONE, ProviderAuthSource.fromRaw(""))
    }

    @Test
    fun authSourceFallsBackForUnexpectedValue() {
        assertEquals(ProviderAuthSource.UNKNOWN, ProviderAuthSource.fromRaw("vault"))
    }

    @Test
    fun providerRuntimeConfigTracksConfigurationState() {
        val configured = ProviderRuntimeConfig(
            providerName = "openai",
            requestUrl = "https://api.openai.com/v1/responses",
            modelName = "gpt-5.4",
            authToken = "secret",
            authSource = ProviderAuthSource.USER_SETTINGS,
        )
        val missingToken = configured.copy(authToken = "", authSource = ProviderAuthSource.NONE)

        assertTrue(configured.isConfigured())
        assertFalse(missingToken.isConfigured())
    }

    @Test
    fun runtimeConfigStoreBuildsConfigFromStoredApiKey() {
        val store = ProviderRuntimeConfigStore(
            metadata = ProviderRuntimeMetadata(
                providerName = "openai",
                requestUrl = "https://api.openai.com/v1/responses",
                modelName = "gpt-5.4",
            ),
            openAiApiKeyStore = FakeOpenAiApiKeyStore("secret"),
        )

        val config = store.currentConfig()

        assertEquals("secret", config.authToken)
        assertEquals(ProviderAuthSource.USER_SETTINGS, config.authSource)
        assertTrue(config.isConfigured())
    }
}

private class FakeOpenAiApiKeyStore(
    initialValue: String,
) : OpenAiApiKeyStore {
    private val state = MutableStateFlow(initialValue)

    override fun observeApiKey() = state

    override fun currentApiKey(): String = state.value

    override suspend fun saveApiKey(apiKey: String) {
        state.value = apiKey
    }
}
