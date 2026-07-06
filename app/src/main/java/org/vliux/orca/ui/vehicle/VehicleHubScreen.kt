package org.vliux.orca.ui.vehicle

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.vliux.orca.R
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.data.DocumentDetail
import org.vliux.orca.data.VehicleRepository
import org.vliux.orca.data.model.DocumentListItem
import org.vliux.orca.data.model.VehicleEntity
import org.vliux.orca.importing.SourceImportRequest
import org.vliux.orca.worker.DocumentExtractionScheduler

@Composable
fun VehicleHubScreen(
    vehicleRepository: VehicleRepository,
    documentRepository: DocumentRepository,
    documentExtractionScheduler: DocumentExtractionScheduler,
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
            FloatingActionButton(onClick = onCreateVehicle) {
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
    vehicleId: String,
    documentId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailFlow = remember(documentRepository, documentId) {
        documentRepository.observeDocumentDetail(documentId)
    }
    val detail by detailFlow.collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VehicleTopBar(
                title = detail?.document?.title ?: "Document Detail",
                onBack = onBack,
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
                if (detail == null) {
                    MissingEntityCard(
                        title = "Document not found.",
                        message = "The selected document could not be loaded from the local database.",
                    )
                } else {
                    DocumentDetailHeader(detail = requireNotNull(detail))
                }
            }

            detail?.let { loadedDetail ->
                item {
                    SourceFilesCard(detail = loadedDetail)
                }
                item {
                    PlaceholderFactsCard(detail = loadedDetail)
                }
                item {
                    PlaceholderEventCard(detail = loadedDetail)
                }
                item {
                    PlaceholderServiceItemsCard(detail = loadedDetail)
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
    onOpenVehicle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenVehicle),
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
    Card(modifier = Modifier.fillMaxWidth()) {
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
    Card(modifier = Modifier.fillMaxWidth()) {
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
) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
        }
    }
}

@Composable
private fun SourceFilesCard(
    detail: DocumentDetail,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Source Files",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            detail.sources.forEach { source ->
                Text(
                    text = "${source.fileName} | ${source.mimeType} | ${source.byteSize} bytes",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PlaceholderFactsCard(
    detail: DocumentDetail,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Extracted Facts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (detail.facts.isEmpty()) {
                Text(
                    text = placeholderTextFor(detail.document.extractionStatus),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                detail.facts.forEach { fact ->
                    Text(
                        text = "${fact.factType}: ${fact.valueText}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceholderEventCard(
    detail: DocumentDetail,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Timeline Event",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
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
            }
        }
    }
}

@Composable
private fun PlaceholderServiceItemsCard(
    detail: DocumentDetail,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                detail.serviceItems.forEach { item ->
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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
    Card(modifier = Modifier.fillMaxWidth()) {
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
    Card(modifier = Modifier.fillMaxWidth()) {
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
    Card(modifier = Modifier.fillMaxWidth()) {
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
