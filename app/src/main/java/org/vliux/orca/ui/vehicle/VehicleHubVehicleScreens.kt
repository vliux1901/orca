package org.vliux.orca.ui.vehicle

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
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
import org.vliux.orca.settings.ImportDocumentGuidancePreference
import org.vliux.orca.worker.DocumentExtractionQueueResult
import org.vliux.orca.worker.DocumentExtractionScheduler

internal const val VehicleDetailHeaderImageTestTag = "vehicleDetailHeaderImage"

@Composable
internal fun VehicleDetailScreen(
    vehicleRepository: VehicleRepository,
    documentRepository: DocumentRepository,
    documentExtractionScheduler: DocumentExtractionScheduler,
    importDocumentGuidancePreference: ImportDocumentGuidancePreference,
    vehicleId: String,
    onBack: () -> Unit,
    onEditVehicle: () -> Unit,
    onOpenDocument: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current as? FragmentActivity
    val context = LocalContext.current
    val unableDeleteVehicleMessage = stringResource(R.string.unable_delete_vehicle)
    val importQueuedMessage = stringResource(R.string.vehicle_import_queued)
    val importNotConfiguredMessage = stringResource(R.string.vehicle_import_not_configured)
    val importAuthCancelledMessage = stringResource(R.string.vehicle_import_auth_cancelled)
    val importAuthUnavailableMessage = stringResource(R.string.vehicle_import_auth_unavailable)
    val importKeyErrorMessage = stringResource(R.string.vehicle_import_key_error)
    val importFailedMessage = stringResource(R.string.vehicle_import_failed)
    val retryQueuedMessage = stringResource(R.string.vehicle_retry_queued)
    val retryNotConfiguredMessage = stringResource(R.string.vehicle_retry_not_configured)
    val retryAuthCancelledMessage = stringResource(R.string.vehicle_retry_auth_cancelled)
    val retryAuthUnavailableMessage = stringResource(R.string.vehicle_retry_auth_unavailable)
    val retryKeyErrorMessage = stringResource(R.string.vehicle_retry_key_error)
    val retryFailedMessage = stringResource(R.string.vehicle_retry_failed)
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
    var retryingDocumentId by remember(vehicleId) { mutableStateOf<String?>(null) }
    var actionMenuExpanded by remember(vehicleId) { mutableStateOf(false) }
    var importGuidanceOpen by remember(vehicleId) { mutableStateOf(false) }
    var showDeleteConfirmation by remember(vehicleId) { mutableStateOf(false) }
    var isDeletingVehicle by remember(vehicleId) { mutableStateOf(false) }
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
                val queueResult = documentExtractionScheduler.queueDocumentExtraction(documentId, activity)
                ImportResult(documentId = documentId, queueResult = queueResult)
            }.onSuccess { result ->
                importMessage = when (result.queueResult) {
                    DocumentExtractionQueueResult.QUEUED -> importQueuedMessage
                    DocumentExtractionQueueResult.NOT_CONFIGURED -> importNotConfiguredMessage
                    DocumentExtractionQueueResult.AUTH_CANCELLED -> importAuthCancelledMessage
                    DocumentExtractionQueueResult.AUTH_UNAVAILABLE -> importAuthUnavailableMessage
                    DocumentExtractionQueueResult.ERROR -> importKeyErrorMessage
                }
                onOpenDocument(result.documentId)
            }.onFailure { error ->
                importError = error.message ?: importFailedMessage
            }
            isImporting = false
        }
    }

    fun beginImportFlow() {
        if (isImporting) {
            return
        }
        if (importDocumentGuidancePreference.shouldShowGuidance()) {
            importGuidanceOpen = true
        } else {
            launcher.launch(SUPPORTED_IMPORT_MIME_TYPES)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VehicleTopBar(
                title = stringResource(R.string.vehicle_details_title),
                onBack = onBack,
                actionContent = {
                    VehicleDetailActionsMenu(
                        expanded = actionMenuExpanded,
                        onExpandedChange = { actionMenuExpanded = it },
                        onEditVehicle = onEditVehicle,
                        onImportFiles = ::beginImportFlow,
                        isImporting = isImporting,
                        onDeleteVehicle = { showDeleteConfirmation = true },
                        isDeletingVehicle = isDeletingVehicle,
                    )
                },
            )
        },
        floatingActionButton = {
            if (loadedVehicle != null) {
                FloatingActionButton(
                    onClick = {
                        beginImportFlow()
                    },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
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
                        title = stringResource(R.string.vehicle_not_found_title),
                        message = stringResource(R.string.vehicle_not_found_message),
                    )
                    else -> VehicleDetailHeaderCard(vehicle = loadedVehicle)
                }
            }

            importMessage?.let { message ->
                item {
                    InfoCard(title = stringResource(R.string.extraction_update_title), message = message)
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
                        title = stringResource(R.string.maintenance_summary_title),
                        subtitle = stringResource(R.string.maintenance_summary_subtitle),
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
                        title = stringResource(R.string.documents_title),
                        subtitle = stringResource(R.string.documents_subtitle),
                    )
                }

                if (!screenState.documentsLoaded) {
                    item {
                        CircularProgressIndicator()
                    }
                } else if (screenState.documents.isEmpty()) {
                    item {
                        EmptyDocumentState(onImport = ::beginImportFlow)
                    }
                } else {
                    items(screenState.documents, key = { it.documentId }) { document ->
                        DocumentSummaryCard(
                            document = document,
                            onOpenDocument = { onOpenDocument(document.documentId) },
                            onRetryExtraction = if (supportsManualExtractionRetry(document.extractionStatus)) {
                                {
                                    scope.launch {
                                        retryingDocumentId = document.documentId
                                        importError = null
                                        importMessage = null
                                        runCatching {
                                            documentExtractionScheduler.queueDocumentExtraction(document.documentId, activity)
                                        }.onSuccess { queueResult ->
                                            importMessage = when (queueResult) {
                                                DocumentExtractionQueueResult.QUEUED -> retryQueuedMessage
                                                DocumentExtractionQueueResult.NOT_CONFIGURED -> retryNotConfiguredMessage
                                                DocumentExtractionQueueResult.AUTH_CANCELLED -> retryAuthCancelledMessage
                                                DocumentExtractionQueueResult.AUTH_UNAVAILABLE -> retryAuthUnavailableMessage
                                                DocumentExtractionQueueResult.ERROR -> retryKeyErrorMessage
                                            }
                                        }.onFailure { error ->
                                            importError = error.message ?: retryFailedMessage
                                        }
                                        retryingDocumentId = null
                                    }
                                }
                            } else {
                                null
                            },
                            isRetrying = retryingDocumentId == document.documentId,
                        )
                    }
                }
            }
        }
    }

    if (importGuidanceOpen) {
        ImportDocumentGuidanceDialog(
            onDismiss = { importGuidanceOpen = false },
            onContinue = { dontShowAgain ->
                if (dontShowAgain) {
                    importDocumentGuidancePreference.disableGuidance()
                }
                importGuidanceOpen = false
                launcher.launch(SUPPORTED_IMPORT_MIME_TYPES)
            },
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingVehicle) {
                    showDeleteConfirmation = false
                }
            },
            title = {
                Text(stringResource(R.string.delete_vehicle_title))
            },
            text = {
                Text(vehicleDeleteConfirmationMessage(loadedVehicle?.displayName))
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDeletingVehicle = true
                        importError = null
                        scope.launch {
                            runCatching {
                                documentRepository.deleteDocumentsForVehicle(vehicleId)
                                vehicleRepository.deleteVehicle(vehicleId)
                            }.onSuccess {
                                showDeleteConfirmation = false
                                onBack()
                            }.onFailure { error ->
                                importError = error.message ?: unableDeleteVehicleMessage
                            }
                            isDeletingVehicle = false
                        }
                    },
                    enabled = !isDeletingVehicle,
                ) {
                    Text(stringResource(if (isDeletingVehicle) R.string.deleting else R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    enabled = !isDeletingVehicle,
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
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
    val context = LocalContext.current
    val saveVehicleFailedMessage = stringResource(R.string.save_vehicle_failed)
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
    val photoPickerLauncher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        formDraft = formDraft.copy(photoUri = uri.toString())
        saveError = null
    }

    LaunchedEffect(vehicleId, loadState) {
        if (!initialized && vehicleId != null && loadState.loaded) {
            formDraft = loadState.vehicle?.let(VehicleFormDraft::fromEntity) ?: VehicleFormDraft()
            initialized = true
        }
    }

    val isCreateMode = vehicleId == null
    val validation = formDraft.validate(requireYear = !isCreateMode)
    val screenTitle = if (vehicleId == null) {
        stringResource(R.string.new_vehicle_title)
    } else {
        stringResource(R.string.edit_vehicle_title)
    }

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
                text = stringResource(R.string.vehicle_editor_intro),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (vehicleId != null && (!initialized || !loadState.loaded)) {
                CircularProgressIndicator()
            } else if (vehicleId != null && loadState.vehicle == null) {
                MissingEntityCard(
                    title = stringResource(R.string.vehicle_not_found_title),
                    message = stringResource(R.string.vehicle_not_found_message),
                )
            } else {
                VehicleForm(
                    draft = formDraft,
                    isCreateMode = isCreateMode,
                    showCatalogSelectors = isCreateMode,
                    onDraftChange = {
                        formDraft = it
                        saveError = null
                    },
                    onPickPhoto = { photoPickerLauncher.launch(arrayOf("image/*")) },
                    onRemovePhoto = { formDraft = formDraft.copy(photoUri = null) },
                )
                validation.errorMessage
                    ?.takeUnless { it == "Display name, make, and model are required." }
                    ?.let { message ->
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
                                saveError = error.message ?: saveVehicleFailedMessage
                            }
                            isSaving = false
                        }
                    },
                    modifier = if (isCreateMode) Modifier.fillMaxWidth() else Modifier,
                    enabled = !isSaving && validation.request != null,
                ) {
                    Text(
                        stringResource(
                            if (isSaving) R.string.saving else R.string.save_vehicle,
                        ),
                    )
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
    onDeleteVehicle: () -> Unit,
    isDeletingVehicle: Boolean,
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
            text = {
                Text(
                    stringResource(
                        if (isImporting) R.string.importing else R.string.import_document,
                    ),
                )
            },
            onClick = {
                onExpandedChange(false)
                onImportFiles()
            },
            enabled = !isImporting && !isDeletingVehicle,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.edit_vehicle_action)) },
            onClick = {
                onExpandedChange(false)
                onEditVehicle()
            },
            enabled = !isDeletingVehicle,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete_vehicle_action)) },
            onClick = {
                onExpandedChange(false)
                onDeleteVehicle()
            },
            enabled = !isDeletingVehicle,
        )
    }
}

@Composable
internal fun VehicleDetailHeaderCard(
    vehicle: VehicleEntity,
) {
    VehicleProfileCard(
        vehicle = vehicle,
        imageSize = 88.dp,
        imageTestTag = VehicleDetailHeaderImageTestTag,
    )
}

internal fun vehicleDeleteConfirmationMessage(
    vehicleDisplayName: String?,
): String = buildString {
    append("Delete this vehicle")
    vehicleDisplayName?.takeIf { it.isNotBlank() }?.let { name ->
        append(" \"").append(name).append("\"")
    }
    append("? All related documents and extracted maintenance records will also be deleted. This cannot be undone.")
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
                    text = stringResource(R.string.maintenance_summary_component),
                    modifier = Modifier.weight(0.38f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.maintenance_summary_repair),
                    modifier = Modifier.weight(0.62f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            androidx.compose.material3.HorizontalDivider()
            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.maintenance_summary_empty),
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
                                    text = stringResource(
                                        R.string.maintenance_summary_last_recorded,
                                        eventDate.format(vehicleSummaryDateFormatter),
                                    ),
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
                text = stringResource(R.string.no_documents_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.no_documents_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onImport) {
                Text(stringResource(R.string.import_document))
            }
        }
    }
}

@Composable
private fun DocumentSummaryCard(
    document: DocumentListItem,
    onOpenDocument: () -> Unit,
    onRetryExtraction: (() -> Unit)? = null,
    isRetrying: Boolean = false,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDocument),
        colors = extractionCardColors(document.extractionStatus),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = document.title ?: stringResource(R.string.imported_document_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                onRetryExtraction?.let { retryAction ->
                    IconButton(
                        onClick = retryAction,
                        enabled = !isRetrying,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_action_retry),
                            contentDescription = stringResource(R.string.retry_extraction),
                        )
                    }
                }
            }
            Text(
                text = stringResource(
                    R.string.document_summary_status,
                    document.sourceCount,
                    document.importStatus,
                    document.extractionStatus,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            document.occurredOn?.let { occurredOn ->
                Text(
                    text = stringResource(R.string.document_occurred_on, occurredOn),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = stringResource(R.string.document_updated, document.updatedAt),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun VehicleForm(
    draft: VehicleFormDraft,
    isCreateMode: Boolean,
    showCatalogSelectors: Boolean,
    onDraftChange: (VehicleFormDraft) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
                    label = stringResource(R.string.vehicle_display_name_label),
                    value = draft.displayName,
                    onValueChange = { onDraftChange(draft.copy(displayName = it)) },
                )
                if (showCatalogSelectors) {
                    CatalogVehicleFields(
                        draft = draft,
                        onDraftChange = onDraftChange,
                    )
                } else {
                    VehicleTextField(
                        label = stringResource(R.string.vehicle_make_label),
                        value = draft.make,
                        onValueChange = { onDraftChange(draft.copy(make = it)) },
                    )
                    VehicleTextField(
                        label = stringResource(R.string.vehicle_model_label),
                        value = draft.model,
                        onValueChange = { onDraftChange(draft.copy(model = it)) },
                    )
                }
                VehicleTextField(
                    label = stringResource(
                        if (isCreateMode) R.string.vehicle_year_optional_label else R.string.vehicle_year_label,
                    ),
                    value = draft.year,
                    onValueChange = { onDraftChange(draft.copy(year = it)) },
                    keyboardType = KeyboardType.Number,
                )
                VehicleTextField(
                    label = stringResource(R.string.vehicle_license_plate_optional_label),
                    value = draft.licensePlate,
                    onValueChange = { onDraftChange(draft.copy(licensePlate = it)) },
                )
                if (!isCreateMode) {
                    VehicleTextField(
                        label = stringResource(R.string.vehicle_vin_optional_label),
                        value = draft.vin,
                        onValueChange = { onDraftChange(draft.copy(vin = it)) },
                    )
                    VehicleTextField(
                        label = stringResource(R.string.vehicle_color_optional_label),
                        value = draft.color,
                        onValueChange = { onDraftChange(draft.copy(color = it)) },
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = vehicleCardColors(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.vehicle_photo_optional_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.vehicle_photo_picker_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                VehicleArtwork(
                    photoUri = draft.photoUri,
                    contentDescription = stringResource(R.string.vehicle_photo_preview),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )
                if (draft.photoUri == null) {
                    TextButton(onClick = onPickPhoto) {
                        Text(stringResource(R.string.vehicle_choose_photo))
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onPickPhoto) {
                            Text(stringResource(R.string.vehicle_change_photo))
                        }
                        TextButton(onClick = onRemovePhoto) {
                            Text(stringResource(R.string.vehicle_remove_photo))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CatalogVehicleFields(
    draft: VehicleFormDraft,
    onDraftChange: (VehicleFormDraft) -> Unit,
) {
    val makeCatalog = findVehicleMakeCatalog(draft.make)
    val catalogModels = makeCatalog?.models.orEmpty()
    val makeOptions = remember { knownVehicleMakes() }
    var makeExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var isManualMakeEntry by rememberSaveable {
        mutableStateOf(draft.make.isNotBlank() && makeCatalog == null)
    }
    var isManualModelEntry by rememberSaveable {
        mutableStateOf(
            draft.model.isNotBlank() && (isManualMakeEntry || (catalogModels.isNotEmpty() && draft.model !in catalogModels)),
        )
    }

    VehicleCatalogDropdownField(
        label = stringResource(R.string.vehicle_make_label),
        value = if (isManualMakeEntry) stringResource(R.string.vehicle_manual_entry_selected) else draft.make,
        placeholder = stringResource(R.string.vehicle_make_placeholder),
        expanded = makeExpanded,
        onExpandedChange = { makeExpanded = it },
        options = makeOptions,
        manualOptionLabel = stringResource(R.string.vehicle_manual_entry_option),
        onOptionSelected = { selectedMake ->
            isManualMakeEntry = false
            isManualModelEntry = false
            val selectedCatalog = findVehicleMakeCatalog(selectedMake)
            val selectedModel = draft.model.takeIf { model -> selectedCatalog?.models?.contains(model) == true }.orEmpty()
            onDraftChange(draft.copy(make = selectedMake, model = selectedModel))
        },
        onManualEntrySelected = {
            isManualMakeEntry = true
            isManualModelEntry = true
            onDraftChange(draft.copy(make = "", model = ""))
        },
    )
    if (isManualMakeEntry) {
        Text(
            text = stringResource(R.string.vehicle_make_manual_helper),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VehicleTextField(
            label = stringResource(R.string.vehicle_make_label),
            value = draft.make,
            onValueChange = { onDraftChange(draft.copy(make = it, model = draft.model.takeIf { isManualModelEntry }.orEmpty())) },
        )
        VehicleTextField(
            label = stringResource(R.string.vehicle_model_label),
            value = draft.model,
            onValueChange = { onDraftChange(draft.copy(model = it)) },
        )
        return
    }

    if (draft.make.isBlank()) {
        Text(
            text = stringResource(R.string.vehicle_model_choose_make_first),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    if (catalogModels.isEmpty()) {
        Text(
            text = stringResource(R.string.vehicle_model_manual_helper),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VehicleTextField(
            label = stringResource(R.string.vehicle_model_label),
            value = draft.model,
            onValueChange = { onDraftChange(draft.copy(model = it)) },
        )
        return
    }

    VehicleCatalogDropdownField(
        label = stringResource(R.string.vehicle_model_label),
        value = if (isManualModelEntry) stringResource(R.string.vehicle_manual_entry_selected) else draft.model,
        placeholder = stringResource(R.string.vehicle_model_placeholder),
        expanded = modelExpanded,
        onExpandedChange = { modelExpanded = it },
        options = catalogModels,
        manualOptionLabel = stringResource(R.string.vehicle_manual_entry_option),
        onOptionSelected = { selectedModel ->
            isManualModelEntry = false
            onDraftChange(draft.copy(model = selectedModel))
        },
        onManualEntrySelected = {
            isManualModelEntry = true
            onDraftChange(draft.copy(model = ""))
        },
    )
    if (isManualModelEntry) {
        Text(
            text = stringResource(R.string.vehicle_model_manual_helper),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VehicleTextField(
            label = stringResource(R.string.vehicle_model_label),
            value = draft.model,
            onValueChange = { onDraftChange(draft.copy(model = it)) },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun VehicleCatalogDropdownField(
    label: String,
    value: String,
    placeholder: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<String>,
    manualOptionLabel: String,
    onOptionSelected: (String) -> Unit,
    onManualEntrySelected: () -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                )
                .fillMaxWidth(),
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.exposedDropdownSize(),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onExpandedChange(false)
                        onOptionSelected(option)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(manualOptionLabel) },
                onClick = {
                    onExpandedChange(false)
                    onManualEntrySelected()
                },
            )
        }
    }
}
