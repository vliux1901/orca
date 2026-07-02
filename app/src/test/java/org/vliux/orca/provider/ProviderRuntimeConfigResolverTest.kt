package org.vliux.orca.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRuntimeConfigResolverTest {
    @Test
    fun authSourceMapsKnownValues() {
        assertEquals(ProviderAuthSource.GRADLE_PROPERTY, ProviderAuthSource.fromRaw("gradle_property"))
        assertEquals(ProviderAuthSource.LOCAL_PROPERTIES, ProviderAuthSource.fromRaw("local_properties"))
        assertEquals(ProviderAuthSource.ENVIRONMENT, ProviderAuthSource.fromRaw("environment"))
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
            authSource = ProviderAuthSource.LOCAL_PROPERTIES,
        )
        val missingToken = configured.copy(authToken = "", authSource = ProviderAuthSource.NONE)

        assertTrue(configured.isConfigured())
        assertFalse(missingToken.isConfigured())
    }
}
