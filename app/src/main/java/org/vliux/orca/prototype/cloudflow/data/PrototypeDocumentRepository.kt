package org.vliux.orca.prototype.cloudflow.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import org.vliux.orca.prototype.cloudflow.importing.AppFileStorage
import org.vliux.orca.prototype.cloudflow.importing.DocumentImportPlanner
import org.vliux.orca.prototype.cloudflow.importing.SourceImportPlanInput
import org.vliux.orca.prototype.cloudflow.importing.SourceImportRequest
import org.vliux.orca.prototype.cloudflow.model.DocumentEntity
import org.vliux.orca.prototype.cloudflow.model.DocumentImportStatus
import org.vliux.orca.prototype.cloudflow.model.DocumentListItem
import org.vliux.orca.prototype.cloudflow.model.DocumentSourceEntity
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

    suspend fun markExtractionQueued(
        documentId: String,
        latestRunId: String?,
        updatedAt: String,
    ) {
        val document = requireNotNull(dao.getDocument(documentId))
        dao.updateDocumentState(
            documentId = documentId,
            sourceCount = document.sourceCount,
            importStatus = document.importStatus,
            extractionStatus = ExtractionStatus.QUEUED.name,
            latestRunId = latestRunId,
            occurredAt = document.occurredAt,
            updatedAt = updatedAt,
        )
    }
}
