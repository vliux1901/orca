package org.vliux.orca.provider

import org.vliux.orca.BuildConfig
import org.vliux.orca.extraction.CloudExtractionConfig
import org.vliux.orca.settings.OpenAiApiKeyStore

data class ProviderRuntimeMetadata(
    val providerName: String,
    val requestUrl: String,
    val modelName: String,
)

data class ProviderRuntimeConfig(
    val providerName: String,
    val requestUrl: String,
    val modelName: String,
    val authToken: String,
    val authSource: ProviderAuthSource,
) {
    fun isConfigured(): Boolean = authToken.isNotBlank()

    fun toCloudExtractionConfig(): CloudExtractionConfig {
        return CloudExtractionConfig(
            responsesUrl = requestUrl,
            apiKey = authToken,
            providerName = providerName,
            modelName = modelName,
        )
    }
}

enum class ProviderAuthSource {
    NONE,
    USER_SETTINGS,
    UNKNOWN,
    ;

    companion object {
        fun fromRaw(value: String): ProviderAuthSource {
            return when (value.trim().lowercase()) {
                "user_settings" -> USER_SETTINGS
                "none",
                "",
                -> NONE
                else -> UNKNOWN
            }
        }
    }
}

object ProviderRuntimeConfigResolver {
    fun metadataFromBuildConfig(): ProviderRuntimeMetadata {
        return ProviderRuntimeMetadata(
            providerName = BuildConfig.CLOUD_EXTRACTION_PROVIDER_NAME,
            requestUrl = BuildConfig.OPENAI_RESPONSES_URL,
            modelName = BuildConfig.CLOUD_EXTRACTION_MODEL,
        )
    }
}

class ProviderRuntimeConfigStore(
    private val metadata: ProviderRuntimeMetadata,
    private val openAiApiKeyStore: OpenAiApiKeyStore,
) {
    fun currentConfig(): ProviderRuntimeConfig {
        val authToken = openAiApiKeyStore.currentApiKey().trim()
        return ProviderRuntimeConfig(
            providerName = metadata.providerName,
            requestUrl = metadata.requestUrl,
            modelName = metadata.modelName,
            authToken = authToken,
            authSource = if (authToken.isBlank()) ProviderAuthSource.NONE else ProviderAuthSource.USER_SETTINGS,
        )
    }
}
