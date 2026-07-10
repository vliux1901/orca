package org.vliux.orca.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "vehicles",
    indices = [
        Index("updatedAt"),
    ],
)
data class VehicleEntity(
    @PrimaryKey val vehicleId: String,
    val displayName: String,
    val make: String,
    val model: String,
    val year: Int,
    val licensePlate: String?,
    val vin: String?,
    val color: String?,
    val photoUri: String?,
    val currentMileage: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
