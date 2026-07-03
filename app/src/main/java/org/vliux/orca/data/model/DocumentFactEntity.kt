package org.vliux.orca.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import kotlinx.serialization.Serializable
import org.vliux.orca.serialization.InstantIso8601Serializer

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
    @Serializable(with = InstantIso8601Serializer::class)
    val createdAt: Instant,
)
