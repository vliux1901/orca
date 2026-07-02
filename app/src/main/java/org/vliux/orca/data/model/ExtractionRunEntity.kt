package org.vliux.orca.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
