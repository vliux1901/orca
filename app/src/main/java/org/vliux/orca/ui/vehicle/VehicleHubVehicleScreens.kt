package org.vliux.orca.ui.vehicle

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import org.vliux.orca.R
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.data.VehicleMaintenanceSummaryItem
import org.vliux.orca.data.VehicleRepository
import org.vliux.orca.data.model.DocumentListItem
import org.vliux.orca.data.model.VehicleEntity
import org.vliux.orca.worker.DocumentExtractionScheduler

@Composable
internal fun VehicleDetailScreen(
    vehicleRepository: VehicleRepository,
    documentRepository: DocumentRepository,
    documentExtractionScheduler: DocumentExtractionScheduler,
    vehicleId: String,
    onBack: () -> Unit,
    onEditVehicle: () -> Unit,
    onOpenDocument: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vehicleDetailViewModel: VehicleDetailViewModel = viewModel(
        key = "vehicleDetail:$vehicleId",
        factory = remember(vehicleId, vehicleRepository, documentRepository) {
            viewModelFactory {
                initializer {
                    VehicleDetailViewModel(
                        vehicleId = vehicleId,
                        observeVehicle = vehicleRepository::observeVehicle,
                        observeMaintenanceSummary = vehicleRepository::observeVehicleMaintenanceSummary,
                        observeDocuments = documentRepository::observeDocumentList,
                    )
                }
            }
        },
    )
    val screenState by vehicleDetailViewModel.uiState.collectAsStateWithLifecycle()
    var importError by remember(vehicleId) { mutableStateOf<String?>(null) }
    var importMessage by remember(vehicleId) { mutableStateOf<String?>(null) }
    var isImporting by remember(vehicleId) { mutableStateOf(false) }
    var actionMenuExpanded by remember(vehicleId) { mutableStateOf(false) }
    val loadedVehicle = screenState.vehicle

    val launcher = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            isImporting = true
            importError = null
            importMessage = null
            runCatching {
                val requests = uris.map { uri -> uri.toSourceImportRequest(context) }
                val documentId = documentRepository.importDocument(vehicleId, requests)
                val queued = documentExtractionScheduler.queueDocumentExtraction(documentId)
                ImportResult(documentId = documentId, queued = queued)
            }.onSuccess { result ->
                importMessage = if (result.queued) {
                    "Imported document and queued extraction."
                } else {
                    "Imported document. Extraction is not configured on this build."
                }
                onOpenDocument(result.documentId)
            }.onFailure { error ->
                importError = error.message ?: "Failed to import files."
            }
            isImporting = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VehicleTopBar(
                title = loadedVehicle?.displayName ?: "View Vehicle",
                onBack = onBack,
                actionContent = {
                    VehicleDetailActionsMenu(
                        expanded = actionMenuExpanded,
                        onExpandedChange = { actionMenuExpanded = it },
                        onEditVehicle = onEditVehicle,
                        onImportFiles = { launcher.launch(SUPPORTED_IMPORT_MIME_TYPES) },
                        isImporting = isImporting,
                    )
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                when {
                    !screenState.vehicleLoaded -> CircularProgressIndicator()
                    loadedVehicle == null -> MissingEntityCard(
                        title = "Vehicle not found.",
                        message = "The selected vehicle could not be loaded from the local database.",
                    )
                    else -> VehicleDetailHeaderCard(
                        vehicle = loadedVehicle,
                    )
                }
            }

            importMessage?.let { message ->
                item {
                    InfoCard(title = "Import Result", message = message)
                }
            }

            importError?.let { message ->
                item {
                    ErrorCard(message = message)
                }
            }

            if (loadedVehicle != null) {
                item {
                    SectionHeader(
                        title = "Maintenance Summary",
                        subtitle = "Latest known maintenance or repair per vehicle component.",
                    )
                }
                item {
                    if (!screenState.maintenanceSummaryLoaded) {
                        CircularProgressIndicator()
                    } else {
                        VehicleMaintenanceSummaryCard(items = screenState.maintenanceSummary)
                    }
                }
                item {
                    SectionHeader(
                        title = "Documents",
                        subtitle = "Imported files are grouped into logical maintenance documents for this vehicle.",
                    )
                }

                if (!screenState.documentsLoaded) {
                    item {
                        CircularProgressIndicator()
                    }
                } else if (screenState.documents.isEmpty()) {
                    item {
                        EmptyDocumentState(onImport = { launcher.launch(SUPPORTED_IMPORT_MIME_TYPES) })
                    }
                } else {
                    items(screenState.documents, key = { it.documentId }) { document ->
                        DocumentSummaryCard(
                            document = document,
                            onOpenDocument = { onOpenDocument(document.documentId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun VehicleEditorScreen(
    vehicleRepository: VehicleRepository,
    vehicleId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val vehicleEditorViewModel: VehicleEditorViewModel = viewModel(
        key = "vehicleEditor:${vehicleId ?: "new"}",
        factory = remember(vehicleId, vehicleRepository) {
            viewModelFactory {
                initializer {
                    VehicleEditorViewModel(
                        vehicleId = vehicleId,
                        observeVehicle = vehicleRepository::observeVehicle,
                    )
                }
            }
        },
    )
    val loadState by vehicleEditorViewModel.uiState.collectAsStateWithLifecycle()
    var formDraft by rememberSaveable(vehicleId, stateSaver = vehicleFormDraftSaver) {
        mutableStateOf(VehicleFormDraft())
    }
    var initialized by rememberSaveable(vehicleId) { mutableStateOf(vehicleId == null) }
    var isSaving by remember(vehicleId) { mutableStateOf(false) }
    var saveError by remember(vehicleId) { mutableStateOf<String?>(null) }

    LaunchedEffect(vehicleId, loadState) {
        if (!initialized && vehicleId != null && loadState.loaded) {
            formDraft = loadState.vehicle?.let(VehicleFormDraft::fromEntity) ?: VehicleFormDraft()
            initialized = true
        }
    }

    val validation = formDraft.validate()
    val screenTitle = if (vehicleId == null) "New Vehicle" else "Edit Vehicle"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VehicleTopBar(
                title = screenTitle,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Vehicle profiles own imported maintenance documents and later extraction results.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (vehicleId != null && (!initialized || !loadState.loaded)) {
                CircularProgressIndicator()
            } else if (vehicleId != null && loadState.vehicle == null) {
                MissingEntityCard(
                    title = "Vehicle not found.",
                    message = "The selected vehicle could not be loaded from the local database.",
                )
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
                                        vehicleId
                                    }
                                }.onSuccess { savedVehicleId ->
                                    onSaved(savedVehicleId)
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
private fun RowScope.VehicleDetailActionsMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEditVehicle: () -> Unit,
    onImportFiles: () -> Unit,
    isImporting: Boolean,
) {
    IconButton(onClick = { onExpandedChange(!expanded) }) {
        Text(
            text = "\u22ee",
            style = MaterialTheme.typography.titleLarge,
        )
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { onExpandedChange(false) },
    ) {
        DropdownMenuItem(
            text = { Text("Edit Vehicle") },
            onClick = {
                onExpandedChange(false)
                onEditVehicle()
            },
        )
        DropdownMenuItem(
            text = { Text(if (isImporting) "Importing..." else "Import Files") },
            onClick = {
                onExpandedChange(false)
                onImportFiles()
            },
            enabled = !isImporting,
        )
    }
}

@Composable
private fun VehicleDetailHeaderCard(
    vehicle: VehicleEntity,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondary,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = vehicle.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Vehicle Profile",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${vehicle.year} ${vehicle.make} ${vehicle.model}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VehicleProfileChip(
                    label = vehicle.currentMileage?.let { "$it mi" } ?: "Mileage unknown",
                )
                vehicle.licensePlate?.takeIf { it.isNotBlank() }?.let { plate ->
                    VehicleProfileChip(label = "Plate $plate")
                }
                vehicle.vin?.takeIf { it.isNotBlank() }?.let { vin ->
                    VehicleProfileChip(label = "VIN ${vin.takeLast(6)}")
                }
            }
        }
    }
}

@Composable
private fun VehicleProfileChip(
    label: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.onSecondary,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun VehicleMaintenanceSummaryCard(
    items: List<VehicleMaintenanceSummaryItem>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = vehicleCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Component",
                    modifier = Modifier.weight(0.38f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Maintenance / Repair",
                    modifier = Modifier.weight(0.62f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            androidx.compose.material3.HorizontalDivider()
            if (items.isEmpty()) {
                Text(
                    text = "No normalized maintenance items yet. Import a maintenance document to populate this summary.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        androidx.compose.material3.HorizontalDivider()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = item.component,
                            modifier = Modifier.weight(0.38f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Column(
                            modifier = Modifier.weight(0.62f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            item.eventDate?.let { eventDate ->
                                Text(
                                    text = "Last recorded ${eventDate.format(vehicleSummaryDateFormatter)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDocumentState(
    onImport: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = vehicleCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "No documents yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Import one or more PDFs or photos to create the first logical maintenance document for this vehicle.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onImport) {
                Text("Import Files")
            }
        }
    }
}

@Composable
private fun DocumentSummaryCard(
    document: DocumentListItem,
    onOpenDocument: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDocument),
        colors = vehicleCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = document.title ?: "Imported document",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildString {
                    append("Sources ${document.sourceCount}")
                    append(" | Import ${document.importStatus}")
                    append(" | Extraction ${document.extractionStatus}")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            document.occurredOn?.let { occurredOn ->
                Text(
                    text = "Occurred on $occurredOn",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "Updated ${document.updatedAt}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun VehicleForm(
    draft: VehicleFormDraft,
    onDraftChange: (VehicleFormDraft) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = vehicleCardColors(),
    ) {
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
