package org.vliux.orca.worker

import androidx.fragment.app.FragmentActivity
import javax.inject.Inject
import javax.inject.Singleton
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.provider.ProviderRuntimeConfigStore
import org.vliux.orca.settings.OpenAiApiKeyMutationResult
import org.vliux.orca.settings.OpenAiApiKeyStore

enum class DocumentExtractionQueueResult {
    QUEUED,
    NOT_CONFIGURED,
    AUTH_CANCELLED,
    AUTH_UNAVAILABLE,
    ERROR,
}

@Singleton
class DocumentExtractionScheduler @Inject constructor(
    private val repository: DocumentRepository,
    private val providerRuntimeConfigStore: ProviderRuntimeConfigStore,
    private val workEnqueuer: DocumentExtractionWorkEnqueuer,
    private val openAiApiKeyStore: OpenAiApiKeyStore,
) {
    val extractionConfigured: Boolean
        get() = providerRuntimeConfigStore.currentConfig().isConfigured()

    suspend fun queueDocumentExtraction(
        documentId: String,
        activity: FragmentActivity? = null,
    ): DocumentExtractionQueueResult = queueDocumentExtraction(documentId) {
        activity?.let { openAiApiKeyStore.unlockApiKey(it) } ?: OpenAiApiKeyMutationResult.AUTH_UNAVAILABLE
    }

    internal suspend fun queueDocumentExtraction(
        documentId: String,
        unlockStoredKey: suspend () -> OpenAiApiKeyMutationResult,
    ): DocumentExtractionQueueResult {
        if (!extractionConfigured) {
            if (!openAiApiKeyStore.hasStoredApiKey()) {
                return DocumentExtractionQueueResult.NOT_CONFIGURED
            }
            when (unlockStoredKey()) {
                OpenAiApiKeyMutationResult.UNLOCKED -> Unit
                OpenAiApiKeyMutationResult.AUTH_CANCELLED -> return DocumentExtractionQueueResult.AUTH_CANCELLED
                OpenAiApiKeyMutationResult.AUTH_UNAVAILABLE -> return DocumentExtractionQueueResult.AUTH_UNAVAILABLE
                else -> return DocumentExtractionQueueResult.ERROR
            }
            if (!extractionConfigured) {
                return DocumentExtractionQueueResult.ERROR
            }
        }
        repository.markDocumentQueued(documentId)
        workEnqueuer.enqueue(documentId)
        return DocumentExtractionQueueResult.QUEUED
    }
}
