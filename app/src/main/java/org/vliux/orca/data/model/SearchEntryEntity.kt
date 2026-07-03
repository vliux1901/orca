package org.vliux.orca.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "search_entries",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DocumentSourceEntity::class,
            parentColumns = ["sourceId"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("vehicleId"),
        Index("documentId"),
        Index("entryType"),
        Index("sourceId"),
    ],
)
data class SearchEntryEntity(
    @PrimaryKey val entryId: String,
    val vehicleId: String,
    val documentId: String,
    val entryType: String,
    val title: String,
    val body: String,
    val sourceId: String?,
    val sourcePage: Int?,
    val createdAt: Instant,
)
