package org.vliux.orca.data

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.shareIn
import org.vliux.orca.data.model.VehicleEntity

data class SaveVehicleRequest(
    val displayName: String,
    val make: String,
    val model: String,
    val year: Int,
    val licensePlate: String?,
    val vin: String?,
    val currentMileage: Int?,
)

@Singleton
class VehicleRepository(
    private val dao: VehicleDao,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
    private val repositoryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val vehiclesFlow by lazy {
        dao.observeVehicles().shareIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            replay = 1,
        )
    }
    private val vehicleFlows = ConcurrentHashMap<String, SharedFlow<VehicleEntity?>>()

    fun observeVehicles(): Flow<List<VehicleEntity>> = vehiclesFlow

    fun observeVehicle(vehicleId: String): Flow<VehicleEntity?> = vehicleFlows.getOrPut(vehicleId) {
        dao.observeVehicle(vehicleId).shareIn(
            scope = repositoryScope,
            started = SharingStarted.Eagerly,
            replay = 1,
        )
    }

    suspend fun getVehicle(vehicleId: String): VehicleEntity? = dao.getVehicle(vehicleId)

    suspend fun createVehicle(request: SaveVehicleRequest): String {
        val now = nowProvider()
        val vehicleId = idProvider()
        dao.upsertVehicle(
            VehicleEntity(
                vehicleId = vehicleId,
                displayName = request.displayName,
                make = request.make,
                model = request.model,
                year = request.year,
                licensePlate = request.licensePlate,
                vin = request.vin,
                currentMileage = request.currentMileage,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return vehicleId
    }

    suspend fun updateVehicle(
        vehicleId: String,
        request: SaveVehicleRequest,
    ) {
        val existing = requireNotNull(dao.getVehicle(vehicleId)) { "Unknown vehicleId: $vehicleId" }
        dao.upsertVehicle(
            existing.copy(
                displayName = request.displayName,
                make = request.make,
                model = request.model,
                year = request.year,
                licensePlate = request.licensePlate,
                vin = request.vin,
                currentMileage = request.currentMileage,
                updatedAt = nowProvider(),
            ),
        )
    }
}
