package org.vliux.orca.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
