package org.vliux.orca.prototype.cloudflow.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(
    tableName = "documents",
    indices = [
        Index("vehicleId"),
        Index("extractionStatus"),
        Index("updatedAt"),
    ],
)
data class DocumentEntity(
    @PrimaryKey val documentId: String,
    val vehicleId: String,
    val title: String?,
    val sourceCount: Int,
    val importStatus: String,
    val extractionStatus: String,
    val latestRunId: String?,
    val occurredAt: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "document_sources",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["documentId", "sourceOrder"], unique = true)],
)
data class DocumentSourceEntity(
    @PrimaryKey val sourceId: String,
    val documentId: String,
    val sourceOrder: Int,
    val fileName: String,
    val mimeType: String,
    val relativePath: String,
    val byteSize: Long,
    val checksumSha256: String?,
    val pageCount: Int?,
    val createdAt: String,
)

@Entity(
    tableName = "extraction_runs",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("documentId"),
        Index("status"),
        Index("createdAt"),
    ],
)
data class ExtractionRunEntity(
    @PrimaryKey val runId: String,
    val documentId: String,
    val backendType: String,
    val providerName: String,
    val modelName: String,
    val promptVersion: String,
    val status: String,
    val errorCode: String?,
    val requestPrompt: String?,
    val responseEnvelopeJson: String?,
    val responseOutputJson: String?,
    val normalizationDebugJson: String?,
    val startedAt: String?,
    val completedAt: String?,
    val createdAt: String,
)

@Serializable
@Entity(
    tableName = "document_facts",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("documentId"),
        Index("factType"),
        Index("isActive"),
    ],
)
data class DocumentFactEntity(
    @PrimaryKey val factId: String,
    val documentId: String,
    val runId: String?,
    val factType: String,
    val valueText: String,
    val normalizedValue: String?,
    val currencyCode: String?,
    val sourceId: String?,
    val sourcePage: Int?,
    val evidenceText: String?,
    val confidence: Double?,
    val isUserEdited: Boolean,
    val replacesFactId: String?,
    val isActive: Boolean,
    val createdAt: String,
)

@Serializable
@Entity(
    tableName = "maintenance_events",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["vehicleId", "eventDate"]),
        Index(value = ["documentId"], unique = true),
    ],
)
data class MaintenanceEventEntity(
    @PrimaryKey val eventId: String,
    val vehicleId: String,
    val documentId: String,
    val eventDate: String?,
    val mileage: Int?,
    val shopName: String?,
    val totalCostMinor: Long?,
    val currencyCode: String?,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
@Entity(
    tableName = "service_items",
    foreignKeys = [
        ForeignKey(
            entity = MaintenanceEventEntity::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("eventId"),
        Index("category"),
    ],
)
data class ServiceItemEntity(
    @PrimaryKey val serviceItemId: String,
    val eventId: String,
    val category: String,
    val label: String,
    val notes: String?,
    val costMinor: Long?,
    val currencyCode: String?,
    val sourceId: String?,
    val sourcePage: Int?,
    val createdAt: String,
)

data class DocumentListItem(
    val documentId: String,
    val title: String?,
    val sourceCount: Int,
    val importStatus: String,
    val extractionStatus: String,
    val occurredAt: String?,
    val createdAt: String,
    val updatedAt: String,
)

enum class DocumentImportStatus {
    PENDING,
    READY,
    FAILED,
}

enum class ExtractionStatus {
    NOT_STARTED,
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
}
