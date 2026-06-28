package org.vliux.orca.prototype.cloudflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.vliux.orca.prototype.cloudflow.model.DocumentEntity
import org.vliux.orca.prototype.cloudflow.model.DocumentFactEntity
import org.vliux.orca.prototype.cloudflow.model.DocumentListItem
import org.vliux.orca.prototype.cloudflow.model.DocumentSourceEntity
import org.vliux.orca.prototype.cloudflow.model.ExtractionRunEntity
import org.vliux.orca.prototype.cloudflow.model.MaintenanceEventEntity
import org.vliux.orca.prototype.cloudflow.model.ServiceItemEntity

@Dao
interface PrototypeDocumentDao {
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceItems(items: List<ServiceItemEntity>)

    @Query(
        """
        UPDATE documents
        SET sourceCount = :sourceCount,
            importStatus = :importStatus,
            extractionStatus = :extractionStatus,
            latestRunId = :latestRunId,
            occurredAt = :occurredAt,
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
        occurredAt: String?,
        updatedAt: String,
    )

    @Query(
        """
        UPDATE documents
        SET title = :title,
            extractionStatus = :extractionStatus,
            latestRunId = :latestRunId,
            occurredAt = :occurredAt,
            updatedAt = :updatedAt
        WHERE documentId = :documentId
        """,
    )
    suspend fun updateDocumentExtractionResult(
        documentId: String,
        title: String?,
        extractionStatus: String,
        latestRunId: String?,
        occurredAt: String?,
        updatedAt: String,
    )

    @Query("SELECT * FROM documents WHERE documentId = :documentId")
    suspend fun getDocument(documentId: String): DocumentEntity?

    @Query("SELECT * FROM document_sources WHERE documentId = :documentId ORDER BY sourceOrder ASC")
    suspend fun getSources(documentId: String): List<DocumentSourceEntity>

    @Query("SELECT * FROM extraction_runs WHERE documentId = :documentId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestRun(documentId: String): ExtractionRunEntity?

    @Query("SELECT * FROM document_facts WHERE documentId = :documentId AND isActive = 1 ORDER BY createdAt ASC")
    fun observeActiveFacts(documentId: String): Flow<List<DocumentFactEntity>>

    @Query("SELECT * FROM maintenance_events WHERE documentId = :documentId LIMIT 1")
    fun observeEvent(documentId: String): Flow<MaintenanceEventEntity?>

    @Query("SELECT * FROM service_items WHERE eventId = :eventId ORDER BY createdAt ASC")
    fun observeServiceItems(eventId: String): Flow<List<ServiceItemEntity>>

    @Query(
        """
        SELECT documentId, title, sourceCount, importStatus, extractionStatus, occurredAt, createdAt, updatedAt
        FROM documents
        WHERE vehicleId = :vehicleId
        ORDER BY createdAt DESC
        """,
    )
    fun observeDocumentList(vehicleId: String): Flow<List<DocumentListItem>>
}
