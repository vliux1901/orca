package org.vliux.orca.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.vliux.orca.OrcaApplication
import org.vliux.orca.logging.OrcaLog
import org.vliux.orca.extraction.ExtractionRequest
import org.vliux.orca.extraction.SourcePayload

class DocumentExtractionWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    private val graph = (appContext.applicationContext as OrcaApplication).graph

    override suspend fun doWork(): Result {
        val documentId = inputData.getString(KEY_DOCUMENT_ID) ?: return Result.failure()
        OrcaLog.info("Worker started for document=${documentId.takeLast(8)}.")
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
            OrcaLog.info(
                "Sending ${request.sources.size} source file(s) to OpenAI for document=${documentId.takeLast(8)}.",
            )
            val parsed = graph.extractionEngine.extract(request)
            OrcaLog.info(
                "Received structured extraction payload for document=${documentId.takeLast(8)} with ${parsed.response.facts.size} fact(s).",
            )
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
                parsed = parsed,
                result = normalized,
            )
            OrcaLog.info("Worker finished successfully for document=${documentId.takeLast(8)}.")
            Result.success()
        }.getOrElse { error ->
            OrcaLog.error(
                "Worker failed for document=${documentId.takeLast(8)} with ${error::class.java.simpleName}: ${error.message}",
                error,
            )
            graph.repository.markRunFailed(
                runId = run.runId,
                errorCode = error::class.java.simpleName,
            )
            Result.failure()
        }
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        const val PROMPT_VERSION = "phase0-v1"

        fun buildRequest(documentId: String) = OneTimeWorkRequestBuilder<DocumentExtractionWorker>()
            .setInputData(workDataOf(KEY_DOCUMENT_ID to documentId))
            .build()
    }
}
