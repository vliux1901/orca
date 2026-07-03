package org.vliux.orca.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable
import org.vliux.orca.serialization.InstantIso8601Serializer
import org.vliux.orca.serialization.LocalDateIso8601Serializer

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
    @Serializable(with = LocalDateIso8601Serializer::class)
    val eventDate: LocalDate?,
    val mileage: Int?,
    val shopName: String?,
    val totalCostMinor: Long?,
    val currencyCode: String?,
    val status: String,
    @Serializable(with = InstantIso8601Serializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantIso8601Serializer::class)
    val updatedAt: Instant,
)
