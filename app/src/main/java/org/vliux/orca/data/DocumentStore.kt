package org.vliux.orca.data

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

interface DocumentStore {
    suspend fun <T> inTransaction(block: suspend DocumentStore.() -> T): T

    suspend fun insertDocument(document: DocumentEntity)
    suspend fun insertSources(sources: List<DocumentSourceEntity>)
    suspend fun insertExtractionRun(run: ExtractionRunEntity)
    suspend fun insertFacts(facts: List<DocumentFactEntity>)
    suspend fun insertEvent(event: MaintenanceEventEntity)
    suspend fun insertServiceItems(items: List<ServiceItemEntity>)
    suspend fun insertSearchEntries(entries: List<SearchEntryEntity>)

    suspend fun deleteFactsByDocument(documentId: String)
    suspend fun deleteEventByDocument(documentId: String)
    suspend fun deleteServiceItemsByEvent(eventId: String)
    suspend fun deleteSearchEntriesByDocument(documentId: String)

    suspend fun updateExtractionRun(
        runId: String,
        status: String,
        errorCode: String?,
        startedAt: Instant?,
        completedAt: Instant?,
    )

    suspend fun updateExtractionRunDebugArtifacts(
        runId: String,
        requestPrompt: String?,
        responseEnvelopeJson: String?,
        responseOutputJson: String?,
        normalizationDebugJson: String?,
    )

    suspend fun updateDocumentState(
        documentId: String,
        sourceCount: Int,
        importStatus: String,
        extractionStatus: String,
        latestRunId: String?,
        occurredOn: LocalDate?,
        updatedAt: Instant,
    )

    suspend fun updateDocumentExtractionResult(
        documentId: String,
        title: String?,
        extractionStatus: String,
        latestRunId: String?,
        occurredOn: LocalDate?,
        updatedAt: Instant,
    )

    suspend fun getDocument(documentId: String): DocumentEntity?
    fun observeDocument(documentId: String): Flow<DocumentEntity?>

    suspend fun getSources(documentId: String): List<DocumentSourceEntity>
    fun observeSources(documentId: String): Flow<List<DocumentSourceEntity>>

    suspend fun getLatestRun(documentId: String): ExtractionRunEntity?
    fun observeLatestRun(documentId: String): Flow<ExtractionRunEntity?>

    suspend fun getRun(runId: String): ExtractionRunEntity?
    suspend fun getEvent(documentId: String): MaintenanceEventEntity?
    fun observeActiveFacts(documentId: String): Flow<List<DocumentFactEntity>>
    fun observeEvent(documentId: String): Flow<MaintenanceEventEntity?>
    fun observeServiceItems(eventId: String): Flow<List<ServiceItemEntity>>
    fun observeDocumentList(vehicleId: String): Flow<List<DocumentListItem>>
}
