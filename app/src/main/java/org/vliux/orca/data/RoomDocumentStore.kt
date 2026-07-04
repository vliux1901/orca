package org.vliux.orca.data

import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import org.vliux.orca.data.model.DocumentEntity
import org.vliux.orca.data.model.DocumentFactEntity
import org.vliux.orca.data.model.DocumentListItem
import org.vliux.orca.data.model.DocumentSourceEntity
import org.vliux.orca.data.model.ExtractionRunEntity
import org.vliux.orca.data.model.MaintenanceEventEntity
import org.vliux.orca.data.model.SearchEntryEntity
import org.vliux.orca.data.model.ServiceItemEntity

class RoomDocumentStore(
    private val database: OrcaDatabase,
) : DocumentStore {
    private val dao = database.documentDao()

    override suspend fun <T> inTransaction(block: suspend DocumentStore.() -> T): T =
        database.withTransaction { block() }

    override suspend fun insertDocument(document: DocumentEntity) = dao.insertDocument(document)
    override suspend fun insertSources(sources: List<DocumentSourceEntity>) = dao.insertSources(sources)
    override suspend fun insertExtractionRun(run: ExtractionRunEntity) = dao.insertExtractionRun(run)
    override suspend fun insertFacts(facts: List<DocumentFactEntity>) = dao.insertFacts(facts)
    override suspend fun insertEvent(event: MaintenanceEventEntity) = dao.insertEvent(event)
    override suspend fun insertServiceItems(items: List<ServiceItemEntity>) = dao.insertServiceItems(items)
    override suspend fun insertSearchEntries(entries: List<SearchEntryEntity>) = dao.insertSearchEntries(entries)
    override suspend fun deleteFactsByDocument(documentId: String) = dao.deleteFactsByDocument(documentId)
    override suspend fun deleteEventByDocument(documentId: String) = dao.deleteEventByDocument(documentId)
    override suspend fun deleteServiceItemsByEvent(eventId: String) = dao.deleteServiceItemsByEvent(eventId)
    override suspend fun deleteSearchEntriesByDocument(documentId: String) = dao.deleteSearchEntriesByDocument(documentId)
    override suspend fun deleteDocument(documentId: String) = dao.deleteDocument(documentId)

    override suspend fun updateExtractionRun(
        runId: String,
        status: String,
        errorCode: String?,
        startedAt: Instant?,
        completedAt: Instant?,
    ) = dao.updateExtractionRun(runId, status, errorCode, startedAt, completedAt)

    override suspend fun updateDocumentState(
        documentId: String,
        sourceCount: Int,
        importStatus: String,
        extractionStatus: String,
        latestRunId: String?,
        occurredOn: LocalDate?,
        updatedAt: Instant,
    ) = dao.updateDocumentState(documentId, sourceCount, importStatus, extractionStatus, latestRunId, occurredOn, updatedAt)

    override suspend fun updateDocumentExtractionResult(
        documentId: String,
        title: String?,
        extractionStatus: String,
        latestRunId: String?,
        occurredOn: LocalDate?,
        updatedAt: Instant,
    ) = dao.updateDocumentExtractionResult(documentId, title, extractionStatus, latestRunId, occurredOn, updatedAt)

    override suspend fun getDocument(documentId: String): DocumentEntity? = dao.getDocument(documentId)
    override fun observeDocument(documentId: String): Flow<DocumentEntity?> = dao.observeDocument(documentId)
    override suspend fun getSources(documentId: String): List<DocumentSourceEntity> = dao.getSources(documentId)
    override fun observeSources(documentId: String): Flow<List<DocumentSourceEntity>> = dao.observeSources(documentId)
    override suspend fun getLatestRun(documentId: String): ExtractionRunEntity? = dao.getLatestRun(documentId)
    override fun observeLatestRun(documentId: String): Flow<ExtractionRunEntity?> = dao.observeLatestRun(documentId)
    override suspend fun getRun(runId: String): ExtractionRunEntity? = dao.getRun(runId)
    override suspend fun getEvent(documentId: String): MaintenanceEventEntity? = dao.getEvent(documentId)
    override fun observeActiveFacts(documentId: String): Flow<List<DocumentFactEntity>> = dao.observeActiveFacts(documentId)
    override fun observeEvent(documentId: String): Flow<MaintenanceEventEntity?> = dao.observeEvent(documentId)
    override fun observeServiceItems(eventId: String): Flow<List<ServiceItemEntity>> = dao.observeServiceItems(eventId)
    override fun observeDocumentList(vehicleId: String): Flow<List<DocumentListItem>> = dao.observeDocumentList(vehicleId)
}
