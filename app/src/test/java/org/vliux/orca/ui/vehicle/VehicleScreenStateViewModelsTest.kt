package org.vliux.orca.ui.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vliux.orca.data.DocumentDetail
import org.vliux.orca.data.model.DocumentImportStatus
import org.vliux.orca.data.model.DocumentListItem
import org.vliux.orca.data.model.ExtractionStatus
import org.vliux.orca.data.model.VehicleEntity
import java.time.Instant

class VehicleScreenStateViewModelsTest {
    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun vehicleDetailViewModelKeepsLatestVehicleAndDocuments() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val vehicleFlow = MutableStateFlow<VehicleEntity?>(null)
        val documentFlow = MutableStateFlow(emptyList<DocumentListItem>())
        val viewModel = VehicleDetailViewModel(
            vehicleId = "vehicle-123",
            observeVehicle = { vehicleFlow },
            observeDocuments = { documentFlow },
            coroutineScope = scope,
        )

        vehicleFlow.value = vehicleEntity()
        documentFlow.value = listOf(documentListItem())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.vehicleLoaded)
        assertEquals("Family SUV", viewModel.uiState.value.vehicle?.displayName)
        assertTrue(viewModel.uiState.value.documentsLoaded)
        assertEquals(listOf("Brake service"), viewModel.uiState.value.documents.map { it.title })
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun documentDetailViewModelMarksMissingDocumentAsLoaded() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val detailFlow = MutableStateFlow<DocumentDetail?>(null)
        val viewModel = DocumentDetailViewModel(
            documentId = "document-123",
            observeDetail = { detailFlow },
            coroutineScope = scope,
        )

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.loaded)
        assertNull(viewModel.uiState.value.detail)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun vehicleEditorViewModelLoadsExistingVehicle() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val vehicleFlow = MutableStateFlow<VehicleEntity?>(null)
        val viewModel = VehicleEditorViewModel(
            vehicleId = "vehicle-123",
            observeVehicle = { vehicleFlow },
            coroutineScope = scope,
        )

        vehicleFlow.value = vehicleEntity()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.loaded)
        assertEquals("Family SUV", viewModel.uiState.value.vehicle?.displayName)
    }

    private fun vehicleEntity() = VehicleEntity(
        vehicleId = "vehicle-123",
        displayName = "Family SUV",
        make = "Toyota",
        model = "Highlander",
        year = 2020,
        licensePlate = null,
        vin = null,
        currentMileage = 45123,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
    )

    private fun documentListItem() = DocumentListItem(
        documentId = "document-123",
        title = "Brake service",
        sourceCount = 1,
        importStatus = DocumentImportStatus.READY.name,
        extractionStatus = ExtractionStatus.SUCCEEDED.name,
        occurredOn = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
    )
}
