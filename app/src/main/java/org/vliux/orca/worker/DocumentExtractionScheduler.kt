package org.vliux.orca.worker

import androidx.work.WorkManager
import javax.inject.Inject
import javax.inject.Singleton
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.provider.ProviderRuntimeConfig

@Singleton
class DocumentExtractionScheduler @Inject constructor(
    private val repository: DocumentRepository,
    private val providerRuntimeConfig: ProviderRuntimeConfig,
    private val workManager: WorkManager,
) {
    val extractionConfigured: Boolean
        get() = providerRuntimeConfig.isConfigured()

    suspend fun queueDocumentExtraction(documentId: String): Boolean {
        if (!extractionConfigured) {
            return false
        }
        repository.markDocumentQueued(documentId)
        workManager.enqueue(DocumentExtractionWorker.buildRequest(documentId))
        return true
    }
}
