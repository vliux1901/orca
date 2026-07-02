package org.vliux.orca.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
