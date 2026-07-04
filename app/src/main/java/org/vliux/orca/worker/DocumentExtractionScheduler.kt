package org.vliux.orca.worker

import javax.inject.Inject
import javax.inject.Singleton
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.provider.ProviderRuntimeConfig

@Singleton
class DocumentExtractionScheduler @Inject constructor(
    private val repository: DocumentRepository,
    private val providerRuntimeConfig: ProviderRuntimeConfig,
    private val workEnqueuer: DocumentExtractionWorkEnqueuer,
) {
    val extractionConfigured: Boolean
        get() = providerRuntimeConfig.isConfigured()

    suspend fun queueDocumentExtraction(documentId: String): Boolean {
        if (!extractionConfigured) {
            return false
        }
        repository.markDocumentQueued(documentId)
        workEnqueuer.enqueue(documentId)
        return true
    }
}
