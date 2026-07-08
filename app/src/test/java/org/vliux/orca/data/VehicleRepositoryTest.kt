package org.vliux.orca.data

import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.vliux.orca.data.model.VehicleEntity

class VehicleRepositoryTest {
    private val now = Instant.parse("2026-07-03T10:15:30Z")

    @Test
    fun createVehiclePersistsRequestedFields() = runTest {
        val dao = FakeVehicleDao()
        val repository = VehicleRepository(
            dao = dao,
            nowProvider = { now },
            idProvider = { "vehicle-123" },
        )

        val vehicleId = repository.createVehicle(
            SaveVehicleRequest(
                displayName = "Family SUV",
                make = "Toyota",
                model = "Highlander",
                year = 2020,
                licensePlate = null,
                vin = null,
                currentMileage = 45123,
            ),
        )

        assertEquals("vehicle-123", vehicleId)
        assertEquals(
            VehicleEntity(
                vehicleId = "vehicle-123",
                displayName = "Family SUV",
                make = "Toyota",
                model = "Highlander",
                year = 2020,
                licensePlate = null,
                vin = null,
                currentMileage = 45123,
                createdAt = now,
                updatedAt = now,
            ),
            dao.getVehicle("vehicle-123"),
        )
    }

    @Test
    fun updateVehiclePreservesCreatedAtAndRefreshesUpdatedAt() = runTest {
        val dao = FakeVehicleDao(
            VehicleEntity(
                vehicleId = "vehicle-123",
                displayName = "Old Name",
                make = "Honda",
                model = "Civic",
                year = 2018,
                licensePlate = null,
                vin = "VIN1",
                currentMileage = 22222,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
            ),
        )
        val repository = VehicleRepository(
            dao = dao,
            nowProvider = { now },
        )

        repository.updateVehicle(
            vehicleId = "vehicle-123",
            request = SaveVehicleRequest(
                displayName = "Commuter Car",
                make = "Honda",
                model = "Civic",
                year = 2019,
                licensePlate = null,
                vin = "VIN2",
                currentMileage = 33333,
            ),
        )

        val stored = dao.getVehicle("vehicle-123")
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), stored?.createdAt)
        assertEquals(now, stored?.updatedAt)
        assertEquals("Commuter Car", stored?.displayName)
        assertEquals(2019, stored?.year)
        assertNull(stored?.licensePlate)
        assertEquals("VIN2", stored?.vin)
        assertEquals(33333, stored?.currentMileage)
    }

    @Test
    fun observeVehiclesReturnsUpdatedRows() = runTest {
        val dao = FakeVehicleDao()
        val repository = VehicleRepository(
            dao = dao,
            nowProvider = { now },
            idProvider = { "vehicle-123" },
        )

        repository.createVehicle(
            SaveVehicleRequest(
                displayName = "Family SUV",
                make = "Toyota",
                model = "Highlander",
                year = 2020,
                licensePlate = null,
                vin = null,
                currentMileage = 45123,
            ),
        )

        val vehicle = repository.observeVehicles().first().single()
        assertEquals("Family SUV", vehicle.displayName)
        assertNull(vehicle.vin)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeVehiclesReplaysCachedRowsToNewCollectors() = runTest {
        val dao = SlowVehicleDao(emissionDelayMillis = 1_000)
        val repository = VehicleRepository(
            dao = dao,
            nowProvider = { now },
            idProvider = { "vehicle-123" },
            repositoryScope = backgroundScope,
        )

        repository.createVehicle(
            SaveVehicleRequest(
                displayName = "Family SUV",
                make = "Toyota",
                model = "Highlander",
                year = 2020,
                licensePlate = null,
                vin = null,
                currentMileage = 45123,
            ),
        )

        val warmup = launch {
            repository.observeVehicles().first { it.isNotEmpty() }
        }
        advanceUntilIdle()
        warmup.join()

        val startedAt = currentTime
        val replayed = repository.observeVehicles().first()

        assertEquals(0, currentTime - startedAt)
        assertEquals(listOf("Family SUV"), replayed.map { it.displayName })
        assertTrue(dao.observeVehiclesCollectionCount >= 1)
    }
}

private class FakeVehicleDao(
    initialVehicle: VehicleEntity? = null,
) : VehicleDao {
    private val vehicles = linkedMapOf<String, VehicleEntity>()
    private val state = MutableStateFlow<List<VehicleEntity>>(emptyList())

    init {
        initialVehicle?.let { vehicles[it.vehicleId] = it }
        publish()
    }

    override suspend fun upsertVehicle(vehicle: VehicleEntity) {
        vehicles[vehicle.vehicleId] = vehicle
        publish()
    }

    override suspend fun getVehicle(vehicleId: String): VehicleEntity? = vehicles[vehicleId]

    override fun observeVehicle(vehicleId: String): Flow<VehicleEntity?> =
        MutableStateFlow(vehicles[vehicleId])

    override fun observeVehicles(): Flow<List<VehicleEntity>> = state

    private fun publish() {
        state.value = vehicles.values.sortedWith(
            compareByDescending<VehicleEntity> { it.updatedAt }.thenByDescending { it.createdAt },
        )
    }
}

private class SlowVehicleDao(
    private val emissionDelayMillis: Long,
) : VehicleDao {
    private val vehicles = linkedMapOf<String, VehicleEntity>()
    var observeVehiclesCollectionCount: Int = 0
        private set

    override suspend fun upsertVehicle(vehicle: VehicleEntity) {
        vehicles[vehicle.vehicleId] = vehicle
    }

    override suspend fun getVehicle(vehicleId: String): VehicleEntity? = vehicles[vehicleId]

    override fun observeVehicle(vehicleId: String): Flow<VehicleEntity?> = flow {
        emit(vehicles[vehicleId])
    }

    override fun observeVehicles(): Flow<List<VehicleEntity>> = flow {
        observeVehiclesCollectionCount += 1
        delay(emissionDelayMillis)
        emit(
            vehicles.values.sortedWith(
                compareByDescending<VehicleEntity> { it.updatedAt }.thenByDescending { it.createdAt },
            ),
        )
    }
}
