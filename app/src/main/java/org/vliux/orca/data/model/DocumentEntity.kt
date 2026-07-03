package org.vliux.orca.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

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
    @ColumnInfo(name = "occurredAt")
    val occurredOn: LocalDate?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
