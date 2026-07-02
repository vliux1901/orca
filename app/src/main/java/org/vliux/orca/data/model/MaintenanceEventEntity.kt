package org.vliux.orca.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

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
