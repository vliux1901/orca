package org.vliux.orca.ui.vehicle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.vliux.orca.data.VehicleRepository
import org.vliux.orca.data.model.VehicleEntity

@Composable
fun VehicleHubScreen(
    vehicleRepository: VehicleRepository,
    modifier: Modifier = Modifier,
) {
    var route by remember { mutableStateOf<VehicleRoute>(VehicleRoute.List) }

    when (val currentRoute = route) {
        VehicleRoute.List -> VehicleListScreen(
            vehicleRepository = vehicleRepository,
            modifier = modifier,
            onCreateVehicle = { route = VehicleRoute.Editor(vehicleId = null) },
            onEditVehicle = { route = VehicleRoute.Editor(vehicleId = it) },
        )
        is VehicleRoute.Editor -> VehicleEditorScreen(
            vehicleRepository = vehicleRepository,
            modifier = modifier,
            vehicleId = currentRoute.vehicleId,
            onBack = { route = VehicleRoute.List },
        )
    }
}

@Composable
private fun VehicleListScreen(
    vehicleRepository: VehicleRepository,
    onCreateVehicle: () -> Unit,
    onEditVehicle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vehiclesFlow = remember(vehicleRepository) { vehicleRepository.observeVehicles() }
    val vehicles by vehiclesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                VehicleHeaderCard(
                    vehicleCount = vehicles.size,
                    onCreateVehicle = onCreateVehicle,
                )
            }

            if (vehicles.isEmpty()) {
                item {
                    EmptyVehicleState(onCreateVehicle = onCreateVehicle)
                }
            } else {
                items(vehicles, key = { it.vehicleId }) { vehicle ->
                    VehicleSummaryCard(
                        vehicle = vehicle,
                        onEditVehicle = { onEditVehicle(vehicle.vehicleId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VehicleEditorScreen(
    vehicleRepository: VehicleRepository,
    vehicleId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val loadState by produceState(
        initialValue = VehicleEditorLoadState(
            isLoading = vehicleId != null,
            vehicle = null,
        ),
        key1 = vehicleId,
        key2 = vehicleRepository,
    ) {
        value = if (vehicleId == null) {
            VehicleEditorLoadState(isLoading = false, vehicle = null)
        } else {
            VehicleEditorLoadState(
                isLoading = false,
                vehicle = vehicleRepository.getVehicle(vehicleId),
            )
        }
    }
    var formDraft by remember(vehicleId) { mutableStateOf(VehicleFormDraft()) }
    var initialized by remember(vehicleId) { mutableStateOf(vehicleId == null) }
    var isSaving by remember(vehicleId) { mutableStateOf(false) }
    var saveError by remember(vehicleId) { mutableStateOf<String?>(null) }

    LaunchedEffect(vehicleId, loadState) {
        if (!initialized && vehicleId != null && !loadState.isLoading) {
            formDraft = loadState.vehicle?.let(VehicleFormDraft::fromEntity) ?: VehicleFormDraft()
            initialized = true
        }
    }

    val validation = formDraft.validate()

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (vehicleId == null) "Create Vehicle" else "Edit Vehicle",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Vehicle profiles own imported maintenance documents and later extraction results.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(
                    onClick = onBack,
                    enabled = !isSaving,
                ) {
                    Text("Back")
                }
            }

            if (vehicleId != null && (!initialized || loadState.isLoading)) {
                CircularProgressIndicator()
            } else if (vehicleId != null && loadState.vehicle == null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Vehicle not found.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "The selected vehicle could not be loaded from the local database.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                VehicleForm(
                    draft = formDraft,
                    onDraftChange = {
                        formDraft = it
                        saveError = null
                    },
                )
                validation.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                saveError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = {
                            val request = validation.request ?: return@Button
                            isSaving = true
                            scope.launch {
                                runCatching {
                                    if (vehicleId == null) {
                                        vehicleRepository.createVehicle(request)
                                    } else {
                                        vehicleRepository.updateVehicle(vehicleId, request)
                                    }
                                }.onSuccess {
                                    onBack()
                                }.onFailure { error ->
                                    saveError = error.message ?: "Failed to save vehicle."
                                }
                                isSaving = false
                            }
                        },
                        enabled = !isSaving && validation.request != null,
                    ) {
                        Text(if (isSaving) "Saving..." else "Save Vehicle")
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleHeaderCard(
    vehicleCount: Int,
    onCreateVehicle: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Vehicles",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (vehicleCount == 0) {
                    "Create a vehicle profile to start phase 2 import and document flows."
                } else {
                    "$vehicleCount vehicle profile(s) ready for document imports."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onCreateVehicle) {
                Text("Add Vehicle")
            }
        }
    }
}

@Composable
private fun EmptyVehicleState(
    onCreateVehicle: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "No vehicles yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "V1 starts by grouping imported maintenance records under a vehicle profile.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onCreateVehicle) {
                Text("Create First Vehicle")
            }
        }
    }
}

@Composable
private fun VehicleSummaryCard(
    vehicle: VehicleEntity,
    onEditVehicle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditVehicle),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = vehicle.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${vehicle.year} ${vehicle.make} ${vehicle.model}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = listOfNotNull(
                    vehicle.licensePlate?.takeIf { it.isNotBlank() }?.let { "Plate $it" },
                    vehicle.currentMileage?.let { "$it mi" },
                    vehicle.vin?.takeIf { it.isNotBlank() }?.let { "VIN ${it.takeLast(6)}" },
                ).joinToString(" | "),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onEditVehicle) {
                    Text("Edit")
                }
            }
        }
    }
}

@Composable
private fun VehicleForm(
    draft: VehicleFormDraft,
    onDraftChange: (VehicleFormDraft) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VehicleTextField(
                label = "Display Name",
                value = draft.displayName,
                onValueChange = { onDraftChange(draft.copy(displayName = it)) },
            )
            VehicleTextField(
                label = "Make",
                value = draft.make,
                onValueChange = { onDraftChange(draft.copy(make = it)) },
            )
            VehicleTextField(
                label = "Model",
                value = draft.model,
                onValueChange = { onDraftChange(draft.copy(model = it)) },
            )
            VehicleTextField(
                label = "Year",
                value = draft.year,
                onValueChange = { onDraftChange(draft.copy(year = it)) },
                keyboardType = KeyboardType.Number,
            )
            VehicleTextField(
                label = "License Plate (Optional)",
                value = draft.licensePlate,
                onValueChange = { onDraftChange(draft.copy(licensePlate = it)) },
            )
            VehicleTextField(
                label = "VIN (Optional)",
                value = draft.vin,
                onValueChange = { onDraftChange(draft.copy(vin = it)) },
            )
            VehicleTextField(
                label = "Current Mileage (Optional)",
                value = draft.currentMileage,
                onValueChange = { onDraftChange(draft.copy(currentMileage = it)) },
                keyboardType = KeyboardType.Number,
            )
        }
    }
}

@Composable
private fun VehicleTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

private sealed interface VehicleRoute {
    data object List : VehicleRoute
    data class Editor(val vehicleId: String?) : VehicleRoute
}

private data class VehicleEditorLoadState(
    val isLoading: Boolean,
    val vehicle: VehicleEntity?,
)
