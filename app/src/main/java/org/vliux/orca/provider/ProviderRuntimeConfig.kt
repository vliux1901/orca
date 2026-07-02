package org.vliux.orca.provider

import org.vliux.orca.BuildConfig
import org.vliux.orca.extraction.CloudExtractionConfig

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
    GRADLE_PROPERTY,
    LOCAL_PROPERTIES,
    ENVIRONMENT,
    UNKNOWN,
    ;

    companion object {
        fun fromRaw(value: String): ProviderAuthSource {
            return when (value.trim().lowercase()) {
                "gradle_property" -> GRADLE_PROPERTY
                "local_properties" -> LOCAL_PROPERTIES
                "environment" -> ENVIRONMENT
                "none",
                "",
                -> NONE
                else -> UNKNOWN
            }
        }
    }
}

object ProviderRuntimeConfigResolver {
    fun fromBuildConfig(): ProviderRuntimeConfig {
        return ProviderRuntimeConfig(
            providerName = BuildConfig.CLOUD_EXTRACTION_PROVIDER_NAME,
            requestUrl = BuildConfig.OPENAI_RESPONSES_URL,
            modelName = BuildConfig.CLOUD_EXTRACTION_MODEL,
            authToken = BuildConfig.CLOUD_EXTRACTION_API_KEY,
            authSource = ProviderAuthSource.fromRaw(BuildConfig.CLOUD_EXTRACTION_AUTH_SOURCE),
        )
    }
}
