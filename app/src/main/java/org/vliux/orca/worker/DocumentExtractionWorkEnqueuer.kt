package org.vliux.orca.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager

interface DocumentExtractionWorkEnqueuer {
    fun enqueue(documentId: String)
}

class WorkManagerDocumentExtractionWorkEnqueuer(
    private val workManager: WorkManager,
) : DocumentExtractionWorkEnqueuer {
    override fun enqueue(documentId: String) {
        workManager.enqueueUniqueWork(
            uniqueWorkName(documentId),
            ExistingWorkPolicy.KEEP,
            DocumentExtractionWorker.buildRequest(documentId),
        )
    }

    internal fun uniqueWorkName(documentId: String): String = "$UNIQUE_WORK_PREFIX$documentId"

    companion object {
        private const val UNIQUE_WORK_PREFIX = "document-extraction:"
    }
}
