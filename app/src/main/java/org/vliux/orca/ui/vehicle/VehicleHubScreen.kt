package org.vliux.orca.ui.vehicle

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.time.LocalDate
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vliux.orca.R
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.data.DocumentDetail
import org.vliux.orca.data.FactReplacement
import org.vliux.orca.data.FactRepository
import org.vliux.orca.data.SaveServiceItemRequest
import org.vliux.orca.data.ServiceItemRepository
import org.vliux.orca.data.VehicleRepository
import org.vliux.orca.data.model.DocumentListItem
import org.vliux.orca.data.model.DocumentSourceEntity
import org.vliux.orca.importing.AppFileStorage
import org.vliux.orca.data.model.DocumentFactEntity
import org.vliux.orca.data.model.ServiceItemEntity
import org.vliux.orca.data.model.VehicleEntity
import org.vliux.orca.importing.SourceImportRequest
import org.vliux.orca.normalization.NormalizationRules
import org.vliux.orca.worker.DocumentExtractionScheduler

@Composable
fun VehicleHubScreen(
    vehicleRepository: VehicleRepository,
    documentRepository: DocumentRepository,
    documentExtractionScheduler: DocumentExtractionScheduler,
    appFileStorage: AppFileStorage,
    factRepository: FactRepository,
    serviceItemRepository: ServiceItemRepository,
    modifier: Modifier = Modifier,
) {
    var route by remember { mutableStateOf<VehicleRoute>(VehicleRoute.List) }
    BackHandler(enabled = route != VehicleRoute.List) {
        route = route.navigateBack()
    }

    when (val currentRoute = route) {
        VehicleRoute.List -> VehicleListScreen(
            vehicleRepository = vehicleRepository,
            modifier = modifier,
            onCreateVehicle = { route = VehicleRoute.Editor(vehicleId = null) },
            onOpenVehicle = { route = VehicleRoute.Detail(vehicleId = it) },
        )
        is VehicleRoute.Editor -> VehicleEditorScreen(
            vehicleRepository = vehicleRepository,
            modifier = modifier,
            vehicleId = currentRoute.vehicleId,
            onBack = { route = route.navigateBack() },
            onSaved = { vehicleId ->
                route = VehicleRoute.Detail(vehicleId = vehicleId)
            },
        )
        is VehicleRoute.Detail -> VehicleDetailScreen(
            vehicleRepository = vehicleRepository,
            documentRepository = documentRepository,
            documentExtractionScheduler = documentExtractionScheduler,
            modifier = modifier,
            vehicleId = currentRoute.vehicleId,
            onBack = { route = route.navigateBack() },
            onEditVehicle = {
                route = VehicleRoute.Editor(
                    vehicleId = currentRoute.vehicleId,
                    parent = currentRoute,
                )
            },
            onOpenDocument = { documentId ->
                route = VehicleRoute.DocumentDetail(
                    vehicleId = currentRoute.vehicleId,
                    documentId = documentId,
                )
            },
        )
        is VehicleRoute.DocumentDetail -> DocumentDetailScreen(
            documentRepository = documentRepository,
            appFileStorage = appFileStorage,
            factRepository = factRepository,
            serviceItemRepository = serviceItemRepository,
            modifier = modifier,
            vehicleId = currentRoute.vehicleId,
            documentId = currentRoute.documentId,
            onBack = { route = route.navigateBack() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleListScreen(
    vehicleRepository: VehicleRepository,
    onCreateVehicle: () -> Unit,
    onOpenVehicle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vehiclesFlow = remember(vehicleRepository) { vehicleRepository.observeVehicles() }
    val vehicles by vehiclesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Orca",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    AppIcon()
                },
                actions = {
                    OverflowMenu(
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = it },
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateVehicle,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                SearchPlaceholderField()
            }
            if (vehicles.isEmpty()) {
                item {
                    EmptyVehicleState(onCreateVehicle = onCreateVehicle)
                }
            } else {
                items(vehicles, key = { it.vehicleId }) { vehicle ->
                    VehicleSummaryCard(
                        vehicle = vehicle,
                        onOpenVehicle = { onOpenVehicle(vehicle.vehicleId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIcon() {
    androidx.compose.foundation.Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = "Orca app icon",
        modifier = Modifier
            .padding(start = 12.dp)
            .size(28.dp)
            .clip(MaterialTheme.shapes.small),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun SearchPlaceholderField() {
    OutlinedTextField(
        value = "",
        onValueChange = {},
        modifier = Modifier.fillMaxWidth(),
        enabled = false,
        singleLine = true,
        placeholder = {
            Text("Search vehicles and records (coming soon)")
        },
    )
}

@Composable
private fun RowScope.OverflowMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
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
            text = { Text("Settings") },
            onClick = { onExpandedChange(false) },
        )
        DropdownMenuItem(
            text = { Text("Options") },
            onClick = { onExpandedChange(false) },
        )
    }
}

@Composable
private fun VehicleDetailScreen(
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
    val vehicleState by produceState<VehicleEditorLoadState>(
        initialValue = VehicleEditorLoadState(isLoading = true, vehicle = null),
        key1 = vehicleId,
        key2 = vehicleRepository,
    ) {
        value = VehicleEditorLoadState(
            isLoading = false,
            vehicle = vehicleRepository.getVehicle(vehicleId),
        )
    }
    val documentFlow = remember(documentRepository, vehicleId) {
        documentRepository.observeDocumentList(vehicleId)
    }
    val documents by documentFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var importError by remember(vehicleId) { mutableStateOf<String?>(null) }
    var importMessage by remember(vehicleId) { mutableStateOf<String?>(null) }
    var isImporting by remember(vehicleId) { mutableStateOf(false) }
    val loadedVehicle = vehicleState.vehicle

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
                    TextButton(onClick = onEditVehicle) {
                        Text("Edit")
                    }
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
                    vehicleState.isLoading -> CircularProgressIndicator()
                    loadedVehicle == null -> MissingEntityCard(
                        title = "Vehicle not found.",
                        message = "The selected vehicle could not be loaded from the local database.",
                    )
                    else -> VehicleDetailHeaderCard(
                        vehicle = loadedVehicle,
                        extractionConfigured = documentExtractionScheduler.extractionConfigured,
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
                        title = "Documents",
                        subtitle = "Imported files are grouped into one logical document and queued for extraction when configuration is available.",
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = { launcher.launch(SUPPORTED_IMPORT_MIME_TYPES) },
                            enabled = !isImporting,
                        ) {
                            Text(if (isImporting) "Importing..." else "Import Files")
                        }
                    }
                }

                if (documents.isEmpty()) {
                    item {
                        EmptyDocumentState(onImport = { launcher.launch(SUPPORTED_IMPORT_MIME_TYPES) })
                    }
                } else {
                    items(documents, key = { it.documentId }) { document ->
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
private fun DocumentDetailScreen(
    documentRepository: DocumentRepository,
    appFileStorage: AppFileStorage,
    factRepository: FactRepository,
    serviceItemRepository: ServiceItemRepository,
    vehicleId: String,
    documentId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailFlow = remember(documentRepository, documentId) {
        documentRepository.observeDocumentDetail(documentId)
    }
    val scope = rememberCoroutineScope()
    val detail by detailFlow.collectAsStateWithLifecycle(initialValue = null)
    var previewRequest by remember(documentId) { mutableStateOf<SourcePreviewRequest?>(null) }
    var showDeleteConfirmation by remember(documentId) { mutableStateOf(false) }
    var isDeleting by remember(documentId) { mutableStateOf(false) }
    var deleteError by remember(documentId) { mutableStateOf<String?>(null) }
    var saveMessage by remember(documentId) { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VehicleTopBar(
                title = detail?.document?.title ?: "Document Detail",
                onBack = onBack,
                actionContent = {
                    if (detail != null) {
                        TextButton(
                            onClick = { showDeleteConfirmation = true },
                            enabled = !isDeleting,
                        ) {
                            Text(documentDeleteActionLabel(isDeleting = isDeleting))
                        }
                    }
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
            deleteError?.let { message ->
                item {
                    ErrorCard(message = message)
                }
            }

            saveMessage?.let { message ->
                item {
                    InfoCard(title = "Saved", message = message)
                }
            }

            item {
                if (detail == null) {
                    MissingEntityCard(
                        title = "Document not found.",
                        message = "The selected document could not be loaded from the local database.",
                    )
                } else {
                    DocumentDetailHeader(
                        detail = requireNotNull(detail),
                        appFileStorage = appFileStorage,
                        onOpenSourcePreview = { previewRequest = it },
                    )
                }
            }

            detail?.let { loadedDetail ->
                item {
                    CorrectionCard(
                        detail = loadedDetail,
                        onSaveFact = { factType, replacedFactId, valueText, note ->
                            factRepository.replaceFact(
                                documentId = loadedDetail.document.documentId,
                                replacedFactId = replacedFactId,
                                replacement = FactReplacement(
                                    factType = factType,
                                    valueText = valueText,
                                    note = note,
                                ),
                            )
                            saveMessage = "Saved $factType correction."
                        },
                    )
                }
                item {
                    ServiceItemsCard(
                        detail = loadedDetail,
                        appFileStorage = appFileStorage,
                        onOpenSourcePreview = { previewRequest = it },
                        onSaveServiceItem = { serviceItemId, request ->
                            serviceItemRepository.updateServiceItem(
                                documentId = loadedDetail.document.documentId,
                                serviceItemId = serviceItemId,
                                request = request,
                            )
                            saveMessage = "Saved service item correction."
                        },
                        onDeleteServiceItem = { serviceItemId ->
                            serviceItemRepository.deleteServiceItem(
                                documentId = loadedDetail.document.documentId,
                                serviceItemId = serviceItemId,
                            )
                            saveMessage = "Removed service item."
                        },
                    )
                }
                item {
                    Text(
                        text = "All corrections preserve extracted fact history. Active corrected values become the effective review state.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    ExpandableDetailSection(
                        title = "Extracted Facts",
                        collapsedLabel = "Show",
                        expandedLabel = "Hide",
                    ) {
                        FactsSectionContent(detail = loadedDetail)
                    }
                }
                item {
                    ExpandableDetailSection(
                        title = "Source Files",
                        collapsedLabel = "Show",
                        expandedLabel = "Hide",
                    ) {
                        SourceFilesSectionContent(detail = loadedDetail)
                    }
                }
                item {
                    ExpandableDetailSection(
                        title = "Timeline Event",
                        collapsedLabel = "Show",
                        expandedLabel = "Hide",
                    ) {
                        EventSectionContent(detail = loadedDetail)
                    }
                }
            }
        }
    }

    previewRequest?.let { request ->
        FullScreenSourcePreviewDialog(
            request = request,
            appFileStorage = appFileStorage,
            onDismiss = { previewRequest = null },
        )
    }

    if (showDeleteConfirmation && detail != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    showDeleteConfirmation = false
                }
            },
            title = {
                Text("Delete Document")
            },
            text = {
                Text(documentDeleteConfirmationMessage(detail?.document?.title))
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentDetail = detail ?: return@Button
                        isDeleting = true
                        deleteError = null
                        scope.launch {
                            runCatching {
                                documentRepository.deleteDocument(currentDetail.document.documentId)
                            }.onSuccess {
                                showDeleteConfirmation = false
                                onBack()
                            }.onFailure { error ->
                                deleteError = error.message ?: "Unable to delete document."
                            }
                            isDeleting = false
                        }
                    },
                    enabled = !isDeleting,
                ) {
                    Text(if (isDeleting) "Deleting..." else "Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    enabled = !isDeleting,
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun VehicleEditorScreen(
    vehicleRepository: VehicleRepository,
    vehicleId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
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

            if (vehicleId != null && (!initialized || loadState.isLoading)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleTopBar(
    title: String,
    onBack: () -> Unit,
    actionContent: @Composable (RowScope.() -> Unit) = {},
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_back_arrow),
                    contentDescription = "Back",
                )
            }
        },
        actions = actionContent,
    )
}

@Composable
private fun EmptyVehicleState(
    onCreateVehicle: () -> Unit,
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
    onOpenVehicle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenVehicle),
        colors = vehicleCardColors(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
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
                val metadata = listOfNotNull(
                    vehicle.licensePlate?.takeIf { it.isNotBlank() }?.let { "Plate $it" },
                    vehicle.currentMileage?.let { "$it mi" },
                    vehicle.vin?.takeIf { it.isNotBlank() }?.let { "VIN ${it.takeLast(6)}" },
                )
                if (metadata.isNotEmpty()) {
                    Text(
                        text = metadata.joinToString(" | "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Box(modifier = Modifier.padding(top = 4.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_vehicle_car),
                    contentDescription = "Vehicle",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VehicleDetailHeaderCard(
    vehicle: VehicleEntity,
    extractionConfigured: Boolean,
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
                text = vehicle.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${vehicle.year} ${vehicle.make} ${vehicle.model}",
                style = MaterialTheme.typography.bodyMedium,
            )
            val metadata = listOfNotNull(
                vehicle.licensePlate?.takeIf { it.isNotBlank() }?.let { "Plate $it" },
                vehicle.currentMileage?.let { "$it mi" },
                vehicle.vin?.takeIf { it.isNotBlank() }?.let { "VIN ${it.takeLast(6)}" },
            )
            if (metadata.isNotEmpty()) {
                Text(
                    text = metadata.joinToString(" | "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = if (extractionConfigured) {
                    "Imports will be queued for background extraction after files are copied into app storage."
                } else {
                    "Imports are available now, but background extraction is not configured on this build."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onOpenDocument) {
                    Text("Open")
                }
            }
        }
    }
}

@Composable
private fun DocumentDetailHeader(
    detail: DocumentDetail,
    appFileStorage: AppFileStorage,
    onOpenSourcePreview: (SourcePreviewRequest) -> Unit,
) {
    val previewSources = remember(detail.sources) { previewableImageSources(detail.sources) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = vehicleCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = detail.document.title ?: "Imported document",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Import ${detail.document.importStatus} | Extraction ${detail.document.extractionStatus}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Sources ${detail.sources.size} | Updated ${detail.document.updatedAt}",
                style = MaterialTheme.typography.bodySmall,
            )
            detail.latestRun?.let { run ->
                Text(
                    text = "Latest run ${run.status} via ${run.providerName}/${run.modelName}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (previewSources.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Imported Images",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Tap a thumbnail to inspect the original picture full screen.",
                    style = MaterialTheme.typography.bodySmall,
                )
                SourcePreviewGrid(
                    sources = previewSources,
                    appFileStorage = appFileStorage,
                    onOpenSourcePreview = onOpenSourcePreview,
                )
            }
        }
    }
}

@Composable
private fun SourcePreviewGrid(
    sources: List<DocumentSourceEntity>,
    appFileStorage: AppFileStorage,
    onOpenSourcePreview: (SourcePreviewRequest) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sources.chunked(3).forEach { rowSources ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowSources.forEach { source ->
                    Box(
                        modifier = Modifier.weight(1f),
                    ) {
                        SourcePreviewTile(
                            source = source,
                            appFileStorage = appFileStorage,
                            previewPage = null,
                            onClick = {
                                onOpenSourcePreview(
                                    SourcePreviewRequest(source = source),
                                )
                            },
                        )
                    }
                }
                repeat(3 - rowSources.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SourcePreviewTile(
    source: DocumentSourceEntity,
    appFileStorage: AppFileStorage,
    previewPage: Int?,
    onClick: () -> Unit,
) {
    val file = remember(source.relativePath, appFileStorage) { appFileStorage.resolveFile(source.relativePath) }
    val previewBitmap = rememberSourceThumbnailBitmap(
        file = file,
        mimeType = source.mimeType,
        page = previewPage,
        maxDimension = 320,
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (previewBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = previewBitmap,
                contentDescription = source.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (source.mimeType == "application/pdf") "PDF" else source.fileName.take(10),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    previewPage?.let { page ->
                        Text(
                            text = "Page $page",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenSourcePreviewDialog(
    request: SourcePreviewRequest,
    appFileStorage: AppFileStorage,
    onDismiss: () -> Unit,
) {
    val source = request.source
    val file = remember(source.relativePath, appFileStorage) { appFileStorage.resolveFile(source.relativePath) }
    var currentPage by rememberSaveable(source.sourceId, request.focusPage) {
        mutableStateOf(request.focusPage?.coerceAtLeast(1) ?: 1)
    }
    val pdfPreview = rememberPdfPagePreview(
        file = file,
        page = currentPage,
        maxDimension = 2048,
    )
    val imageBitmap = rememberDecodedImageBitmap(file = file, maxDimension = 2048)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                if (source.mimeType == "application/pdf") {
                    if (pdfPreview.bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = pdfPreview.bitmap,
                            contentDescription = source.fileName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        PreviewUnavailableState()
                    }
                } else {
                    if (imageBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = imageBitmap,
                            contentDescription = source.fileName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        PreviewUnavailableState()
                    }
                }

                PreviewContextOverlay(
                    request = request,
                    pdfPageCount = pdfPreview.pageCount,
                    currentPage = currentPage,
                    onPreviousPage = {
                        currentPage = (currentPage - 1).coerceAtLeast(1)
                    },
                    onNextPage = {
                        currentPage = (currentPage + 1).coerceAtMost(pdfPreview.pageCount)
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp, start = 16.dp, end = 16.dp),
                    onClose = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun PreviewUnavailableState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Preview unavailable",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun PreviewContextOverlay(
    request: SourcePreviewRequest,
    pdfPageCount: Int,
    currentPage: Int,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xCC101820),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = request.source.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val contextLabels = buildList {
                        request.focusPage?.let { add("Related page $it") }
                        if (request.source.mimeType == "application/pdf") {
                            add("Viewing page $currentPage/$pdfPageCount")
                        }
                    }
                    if (contextLabels.isNotEmpty()) {
                        Text(
                            text = contextLabels.joinToString(" | "),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                        )
                    }
                }
                TextButton(onClick = onClose) {
                    Text("Close", color = Color.White)
                }
            }
            request.focusText?.takeIf { it.isNotBlank() }?.let { focusText ->
                Text(
                    text = "Related text: $focusText",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
            }
            if (request.source.mimeType == "application/pdf" && pdfPageCount > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onPreviousPage,
                        enabled = currentPage > 1,
                    ) {
                        Text("Prev", color = Color.White)
                    }
                    TextButton(
                        onClick = onNextPage,
                        enabled = currentPage < pdfPageCount,
                    ) {
                        Text("Next", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceFilesSectionContent(
    detail: DocumentDetail,
) {
    if (detail.sources.isEmpty()) {
        Text(
            text = "No source files are linked to this document.",
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        detail.sources.forEach { source ->
            Text(
                text = "${source.fileName} | ${source.mimeType} | ${source.byteSize} bytes",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CorrectionCard(
    detail: DocumentDetail,
    onSaveFact: suspend (factType: String, replacedFactId: String?, valueText: String, note: String?) -> Unit,
) {
    val context = LocalContext.current
    val fields = remember(detail) { correctionFields(detail) }
    val scope = rememberCoroutineScope()
    var editingFactType by rememberSaveable(detail.document.documentId) { mutableStateOf<String?>(null) }
    var draftValue by rememberSaveable(detail.document.documentId) { mutableStateOf("") }
    var draftNote by rememberSaveable(detail.document.documentId) { mutableStateOf("") }
    var isSaving by rememberSaveable(detail.document.documentId) { mutableStateOf(false) }
    var saveError by rememberSaveable(detail.document.documentId) { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = vehicleCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Corrections",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Correct service date, mileage, vendor, or total cost. Saving creates a verified replacement fact without deleting extraction history.",
                style = MaterialTheme.typography.bodySmall,
            )
            saveError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            fields.forEach { field ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = field.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = field.currentValue ?: "Not set yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            TextButton(
                                onClick = {
                                    editingFactType = field.factType
                                    draftValue = field.editableValue ?: ""
                                    draftNote = ""
                                    saveError = null
                                },
                            ) {
                                Text(if (editingFactType == field.factType) "Editing" else "Edit")
                            }
                        }
                        if (editingFactType == field.factType) {
                            if (field.factType == "SERVICE_DATE") {
                                val selectedDate = remember(draftValue) { NormalizationRules.normalizeDate(draftValue) }
                                Button(
                                    onClick = {
                                        showDatePicker(
                                            context = context,
                                            initialDate = selectedDate ?: field.normalizedDate ?: LocalDate.now(),
                                            onSelected = { pickedDate ->
                                                draftValue = pickedDate.toString()
                                                saveError = null
                                            },
                                        )
                                    },
                                ) {
                                    Text(selectedDate?.toString() ?: "Pick Date")
                                }
                            } else {
                                OutlinedTextField(
                                    value = draftValue,
                                    onValueChange = {
                                        draftValue = it
                                        saveError = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text(field.label) },
                                )
                            }
                            OutlinedTextField(
                                value = draftNote,
                                onValueChange = { draftNote = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Correction Note (optional)") },
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(
                                    onClick = {
                                        editingFactType = null
                                        draftValue = ""
                                        draftNote = ""
                                        saveError = null
                                    },
                                    enabled = !isSaving,
                                ) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        isSaving = true
                                        scope.launch {
                                            runCatching {
                                                onSaveFact(
                                                    field.factType,
                                                    field.replacedFactId,
                                                    draftValue,
                                                    draftNote.takeIf { it.isNotBlank() },
                                                )
                                            }.onSuccess {
                                                editingFactType = null
                                                draftValue = ""
                                                draftNote = ""
                                            }.onFailure { error ->
                                                saveError = error.message ?: "Unable to save correction."
                                            }
                                            isSaving = false
                                        }
                                    },
                                    enabled = !isSaving && draftValue.isNotBlank(),
                                ) {
                                    Text(if (isSaving) "Saving..." else "Save")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FactsSectionContent(
    detail: DocumentDetail,
) {
    if (detail.facts.isEmpty()) {
        Text(
            text = placeholderTextFor(detail.document.extractionStatus),
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        detail.facts.forEach { fact ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${fact.factType}: ${fact.valueText}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (fact.isUserEdited) {
                    Text(
                        text = "User corrected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                fact.reviewWarning?.let { warning ->
                    FactReviewWarning(warning = warning)
                }
                fact.userNote?.let { note ->
                    Text(
                        text = "Note: $note",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                fact.evidenceText
                    ?.takeIf { it.isNotBlank() && it != fact.valueText }
                    ?.let { evidenceText ->
                        Text(
                            text = "Evidence: $evidenceText",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
            }
        }
    }
}

@Composable
private fun EventSectionContent(
    detail: DocumentDetail,
) {
    val event = detail.event
    if (event == null) {
        Text(
            text = placeholderTextFor(detail.document.extractionStatus),
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        Text(
            text = "${event.eventDate} | ${event.shopName ?: "Unknown shop"} | ${event.status}",
            style = MaterialTheme.typography.bodyMedium,
        )
        event.mileage?.let { mileage ->
            Text(
                text = "Mileage $mileage mi",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        event.totalCostMinor?.let { totalCostMinor ->
            Text(
                text = "Total ${formatMinorCurrency(totalCostMinor, event.currencyCode)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ServiceItemsCard(
    detail: DocumentDetail,
    appFileStorage: AppFileStorage,
    onOpenSourcePreview: (SourcePreviewRequest) -> Unit,
    onSaveServiceItem: suspend (serviceItemId: String, request: SaveServiceItemRequest) -> Unit,
    onDeleteServiceItem: suspend (serviceItemId: String) -> Unit,
) {
    val serviceItemModels = remember(detail) { resolveServiceItemDisplayModels(detail) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = vehicleCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Service Items",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (detail.serviceItems.isEmpty()) {
                Text(
                    text = placeholderTextFor(detail.document.extractionStatus),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                serviceItemModels.forEach { model ->
                    ServiceItemRow(
                        model = model,
                        appFileStorage = appFileStorage,
                        onOpenSourcePreview = onOpenSourcePreview,
                        onSaveServiceItem = onSaveServiceItem,
                        onDeleteServiceItem = onDeleteServiceItem,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceItemRow(
    model: ServiceItemDisplayModel,
    appFileStorage: AppFileStorage,
    onOpenSourcePreview: (SourcePreviewRequest) -> Unit,
    onSaveServiceItem: suspend (serviceItemId: String, request: SaveServiceItemRequest) -> Unit,
    onDeleteServiceItem: suspend (serviceItemId: String) -> Unit,
) {
    var isExpanded by rememberSaveable(model.serviceItem.serviceItemId) { mutableStateOf(false) }
    var isEditing by rememberSaveable(model.serviceItem.serviceItemId) { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable(model.serviceItem.serviceItemId) { mutableStateOf(false) }
    var editLabel by rememberSaveable(model.serviceItem.serviceItemId) { mutableStateOf(model.serviceItem.label) }
    var editCategory by rememberSaveable(model.serviceItem.serviceItemId) { mutableStateOf(model.serviceItem.category) }
    var editNotes by rememberSaveable(model.serviceItem.serviceItemId) { mutableStateOf(model.serviceItem.notes.orEmpty()) }
    var editCost by rememberSaveable(model.serviceItem.serviceItemId) {
        mutableStateOf(model.serviceItem.costMinor?.let(::formatEditableCost).orEmpty())
    }
    var editError by rememberSaveable(model.serviceItem.serviceItemId) { mutableStateOf<String?>(null) }
    var isSaving by rememberSaveable(model.serviceItem.serviceItemId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val serviceItem = model.serviceItem

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = serviceItem.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val metadata = listOfNotNull(
                        serviceItem.category.takeIf { it.isNotBlank() },
                        serviceItem.costMinor?.let { formatMinorCurrency(it, serviceItem.currencyCode) },
                        serviceItem.sourcePage?.let { "Page $it" },
                    )
                    if (metadata.isNotEmpty()) {
                        Text(
                            text = metadata.joinToString(" | "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    serviceItem.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row {
                    TextButton(onClick = { isEditing = !isEditing }) {
                        Text(if (isEditing) "Hide edit" else "Edit")
                    }
                    TextButton(
                        onClick = { showDeleteConfirmation = true },
                        enabled = !isSaving,
                    ) {
                        Text("Remove")
                    }
                    TextButton(onClick = { isExpanded = !isExpanded }) {
                        Text(if (isExpanded) "Hide line item" else "Show line item")
                    }
                }
            }

            if (isEditing) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    editError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    OutlinedTextField(
                        value = editLabel,
                        onValueChange = {
                            editLabel = it
                            editError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Service Item") },
                    )
                    OutlinedTextField(
                        value = editCategory,
                        onValueChange = { editCategory = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Category") },
                    )
                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Notes") },
                    )
                    OutlinedTextField(
                        value = editCost,
                        onValueChange = { editCost = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Cost") },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                isEditing = false
                                editLabel = serviceItem.label
                                editCategory = serviceItem.category
                                editNotes = serviceItem.notes.orEmpty()
                                editCost = serviceItem.costMinor?.let(::formatEditableCost).orEmpty()
                                editError = null
                            },
                            enabled = !isSaving,
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    runCatching {
                                        onSaveServiceItem(
                                            serviceItem.serviceItemId,
                                            SaveServiceItemRequest(
                                                label = editLabel,
                                                category = editCategory,
                                                notes = editNotes.takeIf { it.isNotBlank() },
                                                costText = editCost.takeIf { it.isNotBlank() },
                                            ),
                                        )
                                    }.onSuccess {
                                        isEditing = false
                                    }.onFailure { error ->
                                        editError = error.message ?: "Unable to save service item."
                                    }
                                    isSaving = false
                                }
                            },
                            enabled = !isSaving && editLabel.isNotBlank(),
                        ) {
                            Text(if (isSaving) "Saving..." else "Save")
                        }
                    }
                }
            }

            if (isExpanded) {
                val linkedLineItem = model.linkedLineItem
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Linked Receipt Line",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (linkedLineItem == null) {
                            Text(
                                text = "No linked raw LINE_ITEM could be resolved from the current extraction data.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(
                                text = linkedLineItem.valueText,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            linkedLineItem.reviewWarning?.let { warning ->
                                FactReviewWarning(warning = warning)
                            }
                            linkedLineItem.evidenceText
                                ?.takeIf { it.isNotBlank() && it != linkedLineItem.valueText }
                                ?.let { evidenceText ->
                                    Text(
                                        text = evidenceText,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            lineItemCitationText(linkedLineItem)?.let { citation ->
                                Text(
                                    text = citation,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        model.linkedSource?.let { linkedSource ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Linked Source",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Box(modifier = Modifier.size(96.dp)) {
                                SourcePreviewTile(
                                    source = linkedSource,
                                    appFileStorage = appFileStorage,
                                    previewPage = serviceItem.sourcePage,
                                    onClick = {
                                        onOpenSourcePreview(
                                            SourcePreviewRequest(
                                                source = linkedSource,
                                                focusPage = serviceItem.sourcePage,
                                                focusText = model.linkedLineItem?.evidenceText ?: model.linkedLineItem?.valueText,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!isSaving) {
                    showDeleteConfirmation = false
                }
            },
            title = { Text("Remove Service Item") },
            text = { Text("Remove \"${serviceItem.label}\" from this document?") },
            confirmButton = {
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            runCatching {
                                onDeleteServiceItem(serviceItem.serviceItemId)
                            }.onSuccess {
                                showDeleteConfirmation = false
                            }.onFailure { error ->
                                editError = error.message ?: "Unable to remove service item."
                            }
                            isSaving = false
                        }
                    },
                    enabled = !isSaving,
                ) {
                    Text(if (isSaving) "Removing..." else "Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    enabled = !isSaving,
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun FactReviewWarning(
    warning: String,
) {
    Text(
        text = "Review warning: $warning",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

internal fun documentDeleteActionLabel(
    isDeleting: Boolean,
): String = if (isDeleting) "Deleting..." else "Delete"

internal fun documentDeleteConfirmationMessage(
    documentTitle: String?,
): String = buildString {
    append("Delete this document and its imported source files?")
    documentTitle?.takeIf { it.isNotBlank() }?.let { title ->
        append(" \"").append(title).append("\"")
    }
    append(" This cannot be undone.")
}

@Composable
private fun ExpandableDetailSection(
    title: String,
    collapsedLabel: String,
    expandedLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var isExpanded by rememberSaveable(title) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = vehicleCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(if (isExpanded) expandedLabel else collapsedLabel)
                }
            }
            if (isExpanded) {
                content()
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MissingEntityCard(
    title: String,
    message: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = vehicleCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = vehicleCardColors(),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    message: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = vehicleCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun vehicleCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
)

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

private fun Uri.toSourceImportRequest(context: Context): SourceImportRequest {
    val displayName = context.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        }
    val mimeType = context.contentResolver.getType(this)
    return SourceImportRequest(
        uri = this,
        displayName = displayName,
        mimeType = mimeType,
    )
}

private fun placeholderTextFor(extractionStatus: String): String = when (extractionStatus) {
    "QUEUED" -> "Extraction is queued. Placeholder sections will populate after the background worker runs."
    "RUNNING" -> "Extraction is running. Placeholder sections will populate when structured results persist."
    "FAILED" -> "Extraction failed. Placeholder sections remain empty until the document is retried."
    else -> "No extracted content yet. Placeholder sections will populate after extraction."
}

private fun formatMinorCurrency(
    amountMinor: Long,
    currencyCode: String?,
): String {
    val majorUnits = amountMinor / 100
    val minorUnits = kotlin.math.abs(amountMinor % 100)
    val currency = currencyCode?.uppercase()?.takeIf { it.isNotBlank() } ?: "USD"
    return "$currency $majorUnits.${minorUnits.toString().padStart(2, '0')}"
}

private fun formatEditableCost(amountMinor: Long): String {
    val majorUnits = amountMinor / 100
    val minorUnits = kotlin.math.abs(amountMinor % 100)
    return "$majorUnits.${minorUnits.toString().padStart(2, '0')}"
}

private fun showDatePicker(
    context: Context,
    initialDate: LocalDate,
    onSelected: (LocalDate) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onSelected(LocalDate.of(year, month + 1, dayOfMonth))
        },
        initialDate.year,
        initialDate.monthValue - 1,
        initialDate.dayOfMonth,
    ).show()
}

private fun lineItemCitationText(fact: DocumentFactEntity): String? {
    val sourceLabel = fact.sourceId?.takeLast(6)?.let { "Source $it" }
    val pageLabel = fact.sourcePage?.let { "Page $it" }
    return listOfNotNull(sourceLabel, pageLabel).takeIf { it.isNotEmpty() }?.joinToString(" | ")
}

internal data class ServiceItemDisplayModel(
    val serviceItem: ServiceItemEntity,
    val linkedLineItem: DocumentFactEntity?,
    val linkedSource: DocumentSourceEntity?,
)

internal data class CorrectionFieldModel(
    val factType: String,
    val label: String,
    val currentValue: String?,
    val editableValue: String?,
    val replacedFactId: String?,
    val normalizedDate: LocalDate? = null,
)

internal fun correctionFields(detail: DocumentDetail): List<CorrectionFieldModel> {
    val activeFactsByType = detail.facts.associateBy { it.factType }
    val event = detail.event
    return listOf(
        CorrectionFieldModel(
            factType = "SERVICE_DATE",
            label = "Service Date",
            currentValue = activeFactsByType["SERVICE_DATE"]?.normalizedValue
                ?: activeFactsByType["SERVICE_DATE"]?.valueText?.let { NormalizationRules.normalizeDate(it)?.toString() }
                ?: event?.eventDate?.toString(),
            editableValue = activeFactsByType["SERVICE_DATE"]?.normalizedValue
                ?: activeFactsByType["SERVICE_DATE"]?.valueText?.let { NormalizationRules.normalizeDate(it)?.toString() }
                ?: event?.eventDate?.toString(),
            replacedFactId = activeFactsByType["SERVICE_DATE"]?.factId,
            normalizedDate = activeFactsByType["SERVICE_DATE"]?.normalizedValue?.let(NormalizationRules::normalizeDate)
                ?: activeFactsByType["SERVICE_DATE"]?.valueText?.let(NormalizationRules::normalizeDate)
                ?: event?.eventDate,
        ),
        CorrectionFieldModel(
            factType = "ODOMETER",
            label = "Mileage",
            currentValue = activeFactsByType["ODOMETER"]?.valueText ?: event?.mileage?.toString()?.let { "$it mi" },
            editableValue = activeFactsByType["ODOMETER"]?.valueText ?: event?.mileage?.toString(),
            replacedFactId = activeFactsByType["ODOMETER"]?.factId,
        ),
        CorrectionFieldModel(
            factType = "SHOP_NAME",
            label = "Vendor",
            currentValue = activeFactsByType["SHOP_NAME"]?.valueText ?: event?.shopName,
            editableValue = activeFactsByType["SHOP_NAME"]?.valueText ?: event?.shopName,
            replacedFactId = activeFactsByType["SHOP_NAME"]?.factId,
        ),
        CorrectionFieldModel(
            factType = "TOTAL_COST",
            label = "Total Cost",
            currentValue = activeFactsByType["TOTAL_COST"]?.valueText
                ?: event?.totalCostMinor?.let { formatMinorCurrency(it, event.currencyCode) },
            editableValue = activeFactsByType["TOTAL_COST"]?.valueText
                ?: event?.totalCostMinor?.let(::formatEditableCost),
            replacedFactId = activeFactsByType["TOTAL_COST"]?.factId,
        ),
    )
}

internal fun resolveServiceItemDisplayModels(detail: DocumentDetail): List<ServiceItemDisplayModel> {
    val rawLineItems = detail.facts.filter { it.factType.trim().uppercase() == "LINE_ITEM" }
    val sourcesById = detail.sources.associateBy { it.sourceId }
    return detail.serviceItems.map { serviceItem ->
        val linkedLineItem = resolveLinkedLineItem(serviceItem, rawLineItems)
        ServiceItemDisplayModel(
            serviceItem = serviceItem,
            linkedLineItem = linkedLineItem,
            linkedSource = resolveLinkedSource(serviceItem, linkedLineItem, sourcesById),
        )
    }
}

private fun resolveLinkedSource(
    serviceItem: ServiceItemEntity,
    linkedLineItem: DocumentFactEntity?,
    sourcesById: Map<String, DocumentSourceEntity>,
): DocumentSourceEntity? {
    val sourceId = serviceItem.sourceId ?: linkedLineItem?.sourceId ?: return null
    return sourcesById[sourceId]
}

private fun resolveLinkedLineItem(
    serviceItem: ServiceItemEntity,
    rawLineItems: List<DocumentFactEntity>,
): DocumentFactEntity? {
    val exactSourceMatches = rawLineItems.filter { fact ->
        fact.sourceId == serviceItem.sourceId && fact.sourcePage == serviceItem.sourcePage
    }
    if (exactSourceMatches.isNotEmpty()) {
        return exactSourceMatches.maxByOrNull { scoreLineItemMatch(serviceItem, it) }
    }

    val sourceOnlyMatches = rawLineItems.filter { fact ->
        fact.sourceId != null && fact.sourceId == serviceItem.sourceId
    }
    if (sourceOnlyMatches.isNotEmpty()) {
        return sourceOnlyMatches.maxByOrNull { scoreLineItemMatch(serviceItem, it) }
    }

    val pageOnlyMatches = rawLineItems.filter { fact ->
        fact.sourcePage != null && fact.sourcePage == serviceItem.sourcePage
    }
    if (pageOnlyMatches.isNotEmpty()) {
        return pageOnlyMatches.maxByOrNull { scoreLineItemMatch(serviceItem, it) }
    }

    return null
}

private fun scoreLineItemMatch(
    serviceItem: ServiceItemEntity,
    fact: DocumentFactEntity,
): Int {
    val lineText = normalizeMatchingText(listOfNotNull(fact.valueText, fact.evidenceText).joinToString(" "))
    val labelText = normalizeMatchingText(serviceItem.label)
    val notesText = normalizeMatchingText(serviceItem.notes.orEmpty())

    var score = 0
    if (labelText.isNotBlank() && lineText.isNotBlank() && (lineText.contains(labelText) || labelText.contains(lineText))) {
        score += 10
    }
    if (notesText.isNotBlank() && (lineText.contains(notesText) || notesText.contains(lineText))) {
        score += 6
    }
    score += sharedTokenScore(labelText, lineText) * 2
    score += sharedTokenScore(notesText, lineText)
    return score
}

private fun normalizeMatchingText(value: String): String = value
    .lowercase()
    .replace(Regex("[^a-z0-9 ]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun sharedTokenScore(
    left: String,
    right: String,
): Int {
    if (left.isBlank() || right.isBlank()) {
        return 0
    }
    val leftTokens = left.split(" ").filter { it.length >= 3 }.toSet()
    val rightTokens = right.split(" ").filter { it.length >= 3 }.toSet()
    return leftTokens.intersect(rightTokens).size
}

@Composable
private fun rememberDecodedImageBitmap(
    file: File,
    maxDimension: Int,
) = produceState<androidx.compose.ui.graphics.ImageBitmap?>(
    initialValue = null,
    key1 = file.absolutePath,
    key2 = maxDimension,
) {
    value = withContext(Dispatchers.IO) {
        decodeScaledBitmap(file, maxDimension)?.asImageBitmap()
    }
}.value

internal fun previewableImageSources(
    sources: List<DocumentSourceEntity>,
): List<DocumentSourceEntity> = sources.filter { source ->
    source.mimeType.startsWith("image/")
}

private data class PdfPagePreview(
    val bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    val pageCount: Int,
)

private data class SourcePreviewRequest(
    val source: DocumentSourceEntity,
    val focusPage: Int? = null,
    val focusText: String? = null,
)

@Composable
private fun rememberSourceThumbnailBitmap(
    file: File,
    mimeType: String,
    page: Int?,
    maxDimension: Int,
): androidx.compose.ui.graphics.ImageBitmap? = produceState(
    initialValue = null as androidx.compose.ui.graphics.ImageBitmap?,
    file.absolutePath,
    mimeType,
    page,
    maxDimension,
) {
    value = withContext(Dispatchers.IO) {
        if (mimeType == "application/pdf") {
            decodePdfPageBitmap(file, page ?: 1, maxDimension)?.asImageBitmap()
        } else {
            decodeScaledBitmap(file, maxDimension)?.asImageBitmap()
        }
    }
}.value

@Composable
private fun rememberPdfPagePreview(
    file: File,
    page: Int,
    maxDimension: Int,
) = produceState(
    initialValue = PdfPagePreview(bitmap = null, pageCount = 0),
    key1 = file.absolutePath,
    key2 = page,
    key3 = maxDimension,
) {
    value = withContext(Dispatchers.IO) {
        decodePdfPagePreview(file, page, maxDimension)
    }
}.value

private fun decodeScaledBitmap(
    file: File,
    maxDimension: Int,
): Bitmap? {
    if (!file.exists()) {
        return null
    }

    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return null
    }

    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return runCatching {
        BitmapFactory.decodeFile(file.absolutePath, options)
    }.getOrNull()
}

private fun decodePdfPagePreview(
    file: File,
    page: Int,
    maxDimension: Int,
): PdfPagePreview {
    if (!file.exists()) {
        return PdfPagePreview(bitmap = null, pageCount = 0)
    }
    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) {
                    return@runCatching PdfPagePreview(bitmap = null, pageCount = 0)
                }
                val clampedPage = page.coerceIn(1, renderer.pageCount)
                val bitmap = renderer.openPage(clampedPage - 1).use { pdfPage ->
                    renderPdfPage(pdfPage, maxDimension)?.asImageBitmap()
                }
                PdfPagePreview(bitmap = bitmap, pageCount = renderer.pageCount)
            }
        }
    }.getOrElse {
        PdfPagePreview(bitmap = null, pageCount = 0)
    }
}

private fun decodePdfPageBitmap(
    file: File,
    page: Int,
    maxDimension: Int,
): Bitmap? {
    if (!file.exists()) {
        return null
    }
    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) {
                    return@runCatching null
                }
                val clampedPage = page.coerceIn(1, renderer.pageCount)
                renderer.openPage(clampedPage - 1).use { pdfPage ->
                    renderPdfPage(pdfPage, maxDimension)
                }
            }
        }
    }.getOrNull()
}

private fun renderPdfPage(
    page: PdfRenderer.Page,
    maxDimension: Int,
): Bitmap? {
    if (page.width <= 0 || page.height <= 0) {
        return null
    }
    val scale = (maxDimension.toFloat() / maxOf(page.width, page.height).toFloat()).coerceAtMost(1f)
    val width = (page.width * scale).toInt().coerceAtLeast(1)
    val height = (page.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    }
}

private fun calculateInSampleSize(
    width: Int,
    height: Int,
    maxDimension: Int,
): Int {
    var sampleSize = 1
    var currentWidth = width
    var currentHeight = height
    while (currentWidth > maxDimension || currentHeight > maxDimension) {
        currentWidth /= 2
        currentHeight /= 2
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

private sealed interface VehicleRoute {
    data object List : VehicleRoute
    data class Editor(
        val vehicleId: String?,
        val parent: VehicleRoute? = null,
    ) : VehicleRoute
    data class Detail(val vehicleId: String) : VehicleRoute
    data class DocumentDetail(
        val vehicleId: String,
        val documentId: String,
    ) : VehicleRoute
}

private fun VehicleRoute.navigateBack(): VehicleRoute = when (this) {
    VehicleRoute.List -> VehicleRoute.List
    is VehicleRoute.Detail -> VehicleRoute.List
    is VehicleRoute.DocumentDetail -> VehicleRoute.Detail(vehicleId)
    is VehicleRoute.Editor -> when (val parent = parent) {
        null -> VehicleRoute.List
        is VehicleRoute.Detail -> parent
        is VehicleRoute.DocumentDetail -> parent
        VehicleRoute.List -> VehicleRoute.List
        is VehicleRoute.Editor -> VehicleRoute.List
    }
}

private data class VehicleEditorLoadState(
    val isLoading: Boolean,
    val vehicle: VehicleEntity?,
)

private data class ImportResult(
    val documentId: String,
    val queued: Boolean,
)

private val SUPPORTED_IMPORT_MIME_TYPES = arrayOf("image/*", "application/pdf")
