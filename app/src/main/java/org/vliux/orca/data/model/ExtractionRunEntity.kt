package org.vliux.orca.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

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
    val startedAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant,
)
