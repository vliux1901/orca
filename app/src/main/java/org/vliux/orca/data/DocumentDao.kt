package org.vliux.orca.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

@Dao
interface DocumentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSources(sources: List<DocumentSourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtractionRun(run: ExtractionRunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFacts(facts: List<DocumentFactEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: MaintenanceEventEntity)

    @Query(
        """
        UPDATE maintenance_events
        SET eventDate = :eventDate,
            mileage = :mileage,
            shopName = :shopName,
            totalCostMinor = :totalCostMinor,
            currencyCode = :currencyCode,
            status = :status,
            updatedAt = :updatedAt
        WHERE eventId = :eventId
        """,
    )
    suspend fun updateEvent(
        eventId: String,
        eventDate: LocalDate?,
        mileage: Int?,
        shopName: String?,
        totalCostMinor: Long?,
        currencyCode: String?,
        status: String,
        updatedAt: Instant,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceItems(items: List<ServiceItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchEntries(entries: List<SearchEntryEntity>)

    @Query("DELETE FROM document_facts WHERE documentId = :documentId")
    suspend fun deleteFactsByDocument(documentId: String)

    @Query("DELETE FROM maintenance_events WHERE documentId = :documentId")
    suspend fun deleteEventByDocument(documentId: String)

    @Query("DELETE FROM service_items WHERE eventId = :eventId")
    suspend fun deleteServiceItemsByEvent(eventId: String)

    @Query("DELETE FROM search_entries WHERE documentId = :documentId")
    suspend fun deleteSearchEntriesByDocument(documentId: String)

    @Query("DELETE FROM service_items WHERE serviceItemId = :serviceItemId")
    suspend fun deleteServiceItemById(serviceItemId: String)

    @Query("DELETE FROM documents WHERE documentId = :documentId")
    suspend fun deleteDocument(documentId: String)

    @Query(
        """
        UPDATE extraction_runs
        SET status = :status,
            errorCode = :errorCode,
            startedAt = COALESCE(:startedAt, startedAt),
            completedAt = :completedAt
        WHERE runId = :runId
        """,
    )
    suspend fun updateExtractionRun(
        runId: String,
        status: String,
        errorCode: String?,
        startedAt: Instant?,
        completedAt: Instant?,
    )

    @Query(
        """
        UPDATE documents
        SET sourceCount = :sourceCount,
            importStatus = :importStatus,
            extractionStatus = :extractionStatus,
            latestRunId = :latestRunId,
            occurredAt = :occurredOn,
            updatedAt = :updatedAt
        WHERE documentId = :documentId
        """,
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

    @Query(
        """
        UPDATE documents
        SET title = :title,
            extractionStatus = :extractionStatus,
            latestRunId = :latestRunId,
            occurredAt = :occurredOn,
            updatedAt = :updatedAt
        WHERE documentId = :documentId
        """,
    )
    suspend fun updateDocumentExtractionResult(
        documentId: String,
        title: String?,
        extractionStatus: String,
        latestRunId: String?,
        occurredOn: LocalDate?,
        updatedAt: Instant,
    )

    @Query("SELECT * FROM documents WHERE documentId = :documentId")
    suspend fun getDocument(documentId: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE documentId = :documentId")
    fun observeDocument(documentId: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM document_sources WHERE documentId = :documentId ORDER BY sourceOrder ASC")
    suspend fun getSources(documentId: String): List<DocumentSourceEntity>

    @Query("SELECT * FROM document_sources WHERE documentId = :documentId ORDER BY sourceOrder ASC")
    fun observeSources(documentId: String): Flow<List<DocumentSourceEntity>>

    @Query("SELECT * FROM extraction_runs WHERE documentId = :documentId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestRun(documentId: String): ExtractionRunEntity?

    @Query("SELECT * FROM extraction_runs WHERE documentId = :documentId ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestRun(documentId: String): Flow<ExtractionRunEntity?>

    @Query("SELECT * FROM extraction_runs WHERE runId = :runId")
    suspend fun getRun(runId: String): ExtractionRunEntity?

    @Query("SELECT * FROM maintenance_events WHERE documentId = :documentId LIMIT 1")
    suspend fun getEvent(documentId: String): MaintenanceEventEntity?

    @Query("SELECT * FROM document_facts WHERE factId = :factId LIMIT 1")
    suspend fun getFact(factId: String): DocumentFactEntity?

    @Query("SELECT * FROM document_facts WHERE documentId = :documentId AND isActive = 1 ORDER BY createdAt ASC")
    suspend fun getActiveFacts(documentId: String): List<DocumentFactEntity>

    @Query("UPDATE document_facts SET isActive = 0 WHERE factId = :factId")
    suspend fun deactivateFact(factId: String)

    @Query("UPDATE document_facts SET isActive = 0 WHERE documentId = :documentId AND factType = :factType AND isActive = 1")
    suspend fun deactivateActiveFactsByType(documentId: String, factType: String)

    @Query("SELECT * FROM document_facts WHERE documentId = :documentId AND isActive = 1 ORDER BY createdAt ASC")
    fun observeActiveFacts(documentId: String): Flow<List<DocumentFactEntity>>

    @Query("SELECT * FROM maintenance_events WHERE documentId = :documentId LIMIT 1")
    fun observeEvent(documentId: String): Flow<MaintenanceEventEntity?>

    @Query("SELECT * FROM service_items WHERE eventId = :eventId ORDER BY createdAt ASC")
    suspend fun getServiceItems(eventId: String): List<ServiceItemEntity>

    @Query("SELECT * FROM service_items WHERE serviceItemId = :serviceItemId LIMIT 1")
    suspend fun getServiceItem(serviceItemId: String): ServiceItemEntity?

    @Query("SELECT * FROM service_items WHERE eventId = :eventId ORDER BY createdAt ASC")
    fun observeServiceItems(eventId: String): Flow<List<ServiceItemEntity>>

    @Query(
        """
        SELECT documentId, title, sourceCount, importStatus, extractionStatus, occurredAt AS occurredOn, createdAt, updatedAt
        FROM documents
        WHERE vehicleId = :vehicleId
        ORDER BY createdAt DESC
        """,
    )
    fun observeDocumentList(vehicleId: String): Flow<List<DocumentListItem>>

    @Query(
        """
        SELECT
            si.category AS category,
            si.label AS label,
            me.eventDate AS eventDate,
            me.documentId AS documentId
        FROM maintenance_events me
        JOIN service_items si ON si.eventId = me.eventId
        WHERE me.vehicleId = :vehicleId
        ORDER BY
            CASE WHEN me.eventDate IS NULL THEN 1 ELSE 0 END,
            me.eventDate DESC,
            si.createdAt DESC,
            si.label ASC
        """,
    )
    fun observeVehicleMaintenanceHistory(vehicleId: String): Flow<List<VehicleMaintenanceHistoryEntry>>
}
