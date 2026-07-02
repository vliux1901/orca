package org.vliux.orca.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

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
