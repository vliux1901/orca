package org.vliux.orca.prototype.cloudflow.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.vliux.orca.OrcaApplication
import org.vliux.orca.prototype.cloudflow.extraction.ExtractionRequest
import org.vliux.orca.prototype.cloudflow.extraction.SourcePayload

class PrototypeExtractionWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    private val graph = (appContext.applicationContext as OrcaApplication).graph

    override suspend fun doWork(): Result {
        val documentId = inputData.getString(KEY_DOCUMENT_ID) ?: return Result.failure()
        val bundle = graph.repository.getDocumentBundle(documentId) ?: return Result.failure()
        val run = graph.repository.createQueuedRun(
            documentId = documentId,
            providerName = graph.extractionEngineProviderName,
            modelName = graph.extractionEngineModelName,
            promptVersion = PROMPT_VERSION,
        )
        return runCatching {
            graph.repository.markRunRunning(run.runId)
            val request = ExtractionRequest(
                document = bundle.document,
                sources = bundle.sources.map { source ->
                    SourcePayload(
                        source = source,
                        file = graph.resolveSourceFile(source.relativePath),
                    )
                },
                promptVersion = PROMPT_VERSION,
            )
            val parsed = graph.extractionEngine.extract(request)
            val normalized = graph.extractionNormalizer.normalize(
                documentId = bundle.document.documentId,
                vehicleId = bundle.document.vehicleId,
                runId = run.runId,
                response = parsed.response,
                sources = bundle.sources,
            )
            graph.repository.applyExtractionResult(
                documentId = documentId,
                runId = run.runId,
                result = normalized,
            )
            Result.success()
        }.getOrElse { error ->
            graph.repository.markRunFailed(
                runId = run.runId,
                errorCode = error::class.java.simpleName,
            )
            Result.failure()
        }
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        const val PROMPT_VERSION = "prototype-v1"

        fun buildRequest(documentId: String) = OneTimeWorkRequestBuilder<PrototypeExtractionWorker>()
            .setInputData(workDataOf(KEY_DOCUMENT_ID to documentId))
            .build()
    }
}
