package org.vliux.orca.prototype.cloudflow.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import java.time.Instant
import org.vliux.orca.PrototypeGraph
import org.vliux.orca.prototype.cloudflow.extraction.ExtractionRequest
import org.vliux.orca.prototype.cloudflow.extraction.SourcePayload
import org.vliux.orca.prototype.cloudflow.model.ExtractionStatus

class PrototypeExtractionWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
    private val graph: PrototypeGraph,
) : CoroutineWorker(appContext, workerParameters) {
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
            graph.extractionEngine.extract(request)
            graph.repository.finishRun(
                runId = run.runId,
                status = ExtractionStatus.SUCCEEDED.name,
                occurredAt = bundle.document.occurredAt,
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
    }
}

class PrototypeWorkerFactory(
    private val graph: PrototypeGraph,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        return when (workerClassName) {
            PrototypeExtractionWorker::class.java.name -> {
                PrototypeExtractionWorker(appContext, workerParameters, graph)
            }

            else -> null
        }
    }
}
