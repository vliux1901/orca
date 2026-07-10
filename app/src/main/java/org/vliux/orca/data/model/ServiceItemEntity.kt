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
    tableName = "service_items",
    foreignKeys = [
        ForeignKey(
            entity = MaintenanceEventEntity::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
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
        Index("eventId"),
        Index("category"),
        Index("sourceId"),
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
    val isUserEdited: Boolean,
    @Serializable(with = InstantIso8601Serializer::class)
    val createdAt: Instant,
)
