package org.vliux.orca.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiKeyAuthenticationPromptStyleTest {
    @Test
    fun `uses legacy device credential compatibility below Android 11`() {
        assertEquals(
            ApiKeyAuthenticationPromptStyle.LEGACY_DEVICE_CREDENTIAL,
            apiKeyAuthenticationPromptStyle(sdkInt = 28),
        )
        assertEquals(
            ApiKeyAuthenticationPromptStyle.LEGACY_DEVICE_CREDENTIAL,
            apiKeyAuthenticationPromptStyle(sdkInt = 29),
        )
    }

    @Test
    fun `uses modern authenticator configuration from Android 11`() {
        assertEquals(
            ApiKeyAuthenticationPromptStyle.MODERN_AUTHENTICATORS,
            apiKeyAuthenticationPromptStyle(sdkInt = 30),
        )
    }
}
