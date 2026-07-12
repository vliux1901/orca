package org.vliux.orca.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.vliux.orca.data.model.VehicleEntity

@Dao
interface VehicleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVehicle(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicles WHERE vehicleId = :vehicleId")
    suspend fun deleteVehicle(vehicleId: String)

    @Query("SELECT * FROM vehicles WHERE vehicleId = :vehicleId")
    suspend fun getVehicle(vehicleId: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE vehicleId = :vehicleId")
    fun observeVehicle(vehicleId: String): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles ORDER BY updatedAt DESC, createdAt DESC")
    fun observeVehicles(): Flow<List<VehicleEntity>>
}
