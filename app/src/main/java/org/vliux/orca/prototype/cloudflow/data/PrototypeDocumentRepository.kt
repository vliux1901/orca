package org.vliux.orca.prototype.cloudflow.data

import androidx.room.withTransaction
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.vliux.orca.prototype.cloudflow.importing.AppFileStorage
import org.vliux.orca.prototype.cloudflow.importing.DocumentImportPlanner
import org.vliux.orca.prototype.cloudflow.importing.SourceImportPlanInput
import org.vliux.orca.prototype.cloudflow.importing.SourceImportRequest
import org.vliux.orca.prototype.cloudflow.model.DocumentEntity
import org.vliux.orca.prototype.cloudflow.model.DocumentImportStatus
import org.vliux.orca.prototype.cloudflow.model.DocumentListItem
import org.vliux.orca.prototype.cloudflow.model.DocumentSourceEntity
import org.vliux.orca.prototype.cloudflow.model.ExtractionRunEntity
import org.vliux.orca.prototype.cloudflow.model.ExtractionStatus

class PrototypeDocumentRepository(
    private val database: PrototypeDatabase,
    private val fileStorage: AppFileStorage,
    private val planner: DocumentImportPlanner = DocumentImportPlanner(),
) {
    private val dao = database.documentDao()

    suspend fun importDocument(
        vehicleId: String,
        imports: List<SourceImportRequest>,
    ): String {
        require(imports.isNotEmpty()) { "At least one import is required." }

        val plan = planner.plan(
            vehicleId = vehicleId,
            imports = imports.map { SourceImportPlanInput(it.displayName, it.mimeType) },
        )

        val storedSources = plan.sources.zip(imports).map { (sourcePlan, request) ->
            fileStorage.copySource(sourcePlan.source, request)
        }

        database.withTransaction {
            dao.insertDocument(plan.document)
            dao.insertSources(storedSources.map { it.source })
            dao.updateDocumentState(
                documentId = plan.document.documentId,
                sourceCount = storedSources.size,
                importStatus = DocumentImportStatus.READY.name,
                extractionStatus = ExtractionStatus.NOT_STARTED.name,
                latestRunId = null,
                occurredAt = null,
                updatedAt = plan.document.updatedAt,
            )
        }

        return plan.document.documentId
    }

    fun observeDocumentList(vehicleId: String): Flow<List<DocumentListItem>> =
        dao.observeDocumentList(vehicleId)

    suspend fun getDocument(documentId: String): DocumentEntity? =
        dao.getDocument(documentId)

    suspend fun getSources(documentId: String): List<DocumentSourceEntity> =
        dao.getSources(documentId)

    suspend fun getDocumentBundle(documentId: String): DocumentBundle? {
        val document = dao.getDocument(documentId) ?: return null
        return DocumentBundle(
            document = document,
            sources = dao.getSources(documentId),
        )
    }

    suspend fun createQueuedRun(
        documentId: String,
        providerName: String,
        modelName: String,
        promptVersion: String,
    ): ExtractionRunEntity {
        val document = requireNotNull(dao.getDocument(documentId))
        val now = Instant.now().toString()
        val run = ExtractionRunEntity(
            runId = UUID.randomUUID().toString(),
            documentId = documentId,
            backendType = "CLOUD_VISION",
            providerName = providerName,
            modelName = modelName,
            promptVersion = promptVersion,
            status = ExtractionStatus.QUEUED.name,
            errorCode = null,
            startedAt = null,
            completedAt = null,
            createdAt = now,
        )

        database.withTransaction {
            dao.insertExtractionRun(run)
            dao.updateDocumentState(
                documentId = documentId,
                sourceCount = document.sourceCount,
                importStatus = document.importStatus,
                extractionStatus = ExtractionStatus.QUEUED.name,
                latestRunId = run.runId,
                occurredAt = document.occurredAt,
                updatedAt = now,
            )
        }
        return run
    }

    suspend fun markRunRunning(runId: String) {
        val run = requireNotNull(dao.getRun(runId))
        val document = requireNotNull(dao.getDocument(run.documentId))
        val now = Instant.now().toString()
        database.withTransaction {
            dao.updateExtractionRun(
                runId = runId,
                status = ExtractionStatus.RUNNING.name,
                errorCode = null,
                startedAt = now,
                completedAt = null,
            )
            dao.updateDocumentState(
                documentId = run.documentId,
                sourceCount = document.sourceCount,
                importStatus = document.importStatus,
                extractionStatus = ExtractionStatus.RUNNING.name,
                latestRunId = runId,
                occurredAt = document.occurredAt,
                updatedAt = now,
            )
        }
    }

    suspend fun markRunFailed(
        runId: String,
        errorCode: String,
    ) {
        val run = requireNotNull(dao.getRun(runId))
        val document = requireNotNull(dao.getDocument(run.documentId))
        val now = Instant.now().toString()
        database.withTransaction {
            dao.updateExtractionRun(
                runId = runId,
                status = ExtractionStatus.FAILED.name,
                errorCode = errorCode,
                startedAt = null,
                completedAt = now,
            )
            dao.updateDocumentState(
                documentId = run.documentId,
                sourceCount = document.sourceCount,
                importStatus = document.importStatus,
                extractionStatus = ExtractionStatus.FAILED.name,
                latestRunId = runId,
                occurredAt = document.occurredAt,
                updatedAt = now,
            )
        }
    }

    suspend fun finishRun(
        runId: String,
        status: String,
        occurredAt: String?,
    ) {
        val run = requireNotNull(dao.getRun(runId))
        val document = requireNotNull(dao.getDocument(run.documentId))
        val now = Instant.now().toString()
        database.withTransaction {
            dao.updateExtractionRun(
                runId = runId,
                status = status,
                errorCode = null,
                startedAt = null,
                completedAt = now,
            )
            dao.updateDocumentExtractionResult(
                documentId = run.documentId,
                title = document.title,
                extractionStatus = status,
                latestRunId = runId,
                occurredAt = occurredAt,
                updatedAt = now,
            )
        }
    }
}

data class DocumentBundle(
    val document: DocumentEntity,
    val sources: List<DocumentSourceEntity>,
)
