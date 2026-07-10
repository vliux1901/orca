package org.vliux.orca.ui.vehicle

import android.app.DatePickerDialog
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import org.vliux.orca.R
import org.vliux.orca.data.DocumentDetail
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.data.FactReplacement
import org.vliux.orca.data.FactRepository
import org.vliux.orca.data.SaveServiceItemRequest
import org.vliux.orca.data.ServiceItemRepository
import org.vliux.orca.data.model.DocumentFactEntity
import org.vliux.orca.data.model.DocumentSourceEntity
import org.vliux.orca.data.model.ServiceItemEntity
import org.vliux.orca.importing.AppFileStorage
import org.vliux.orca.importing.SourceImportRequest
import org.vliux.orca.normalization.NormalizationRules
import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.pow

@Composable
internal fun DocumentDetailScreen(
    documentRepository: DocumentRepository,
    appFileStorage: AppFileStorage,
    factRepository: FactRepository,
    serviceItemRepository: ServiceItemRepository,
    vehicleId: String,
    documentId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val savedFactCorrectionTemplate = stringResource(R.string.saved_fact_correction, "%s")
    val savedServiceItemCorrectionMessage = stringResource(R.string.saved_service_item_correction)
    val removedServiceItemMessage = stringResource(R.string.removed_service_item)
    val unableDeleteDocumentMessage = stringResource(R.string.unable_delete_document)
    val documentDetailViewModel: DocumentDetailViewModel = viewModel(
        key = "documentDetail:$documentId",
        factory = remember(documentId, documentRepository) {
            viewModelFactory {
                initializer {
                    DocumentDetailViewModel(
                        documentId = documentId,
                        observeDetail = documentRepository::observeDocumentDetail,
                    )
                }
            }
        },
    )
    val scope = rememberCoroutineScope()
    val screenState by documentDetailViewModel.uiState.collectAsStateWithLifecycle()
    val detail = screenState.detail
    var previewRequest by remember(documentId) { mutableStateOf<SourcePreviewRequest?>(null) }
    var showDeleteConfirmation by remember(documentId) { mutableStateOf(false) }
    var isDeleting by remember(documentId) { mutableStateOf(false) }
    var deleteError by remember(documentId) { mutableStateOf<String?>(null) }
    var saveMessage by remember(documentId) { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VehicleTopBar(
                title = stringResource(R.string.document_details_title),
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
                    InfoCard(title = stringResource(R.string.saved_title), message = message)
                }
            }

            item {
                if (!screenState.loaded) {
                    CircularProgressIndicator()
                } else if (detail == null) {
                    MissingEntityCard(
                        title = stringResource(R.string.document_not_found_title),
                        message = stringResource(R.string.document_not_found_message),
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
                    DocumentExtractionStatusCard(detail = loadedDetail)
                }
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
                            saveMessage = savedFactCorrectionTemplate.replace("%s", factType)
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
                            saveMessage = savedServiceItemCorrectionMessage
                        },
                        onDeleteServiceItem = { serviceItemId ->
                            serviceItemRepository.deleteServiceItem(
                                documentId = loadedDetail.document.documentId,
                                serviceItemId = serviceItemId,
                            )
                            saveMessage = removedServiceItemMessage
                        },
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.corrections_history_message),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    ExpandableDetailSection(
                        title = stringResource(R.string.extracted_facts_title),
                        collapsedLabel = stringResource(R.string.show),
                        expandedLabel = stringResource(R.string.hide),
                    ) {
                        FactsSectionContent(detail = loadedDetail)
                    }
                }
                item {
                    ExpandableDetailSection(
                        title = stringResource(R.string.source_files_title),
                        collapsedLabel = stringResource(R.string.show),
                        expandedLabel = stringResource(R.string.hide),
                    ) {
                        SourceFilesSectionContent(detail = loadedDetail)
                    }
                }
                item {
                    ExpandableDetailSection(
                        title = stringResource(R.string.timeline_event_title),
                        collapsedLabel = stringResource(R.string.show),
                        expandedLabel = stringResource(R.string.hide),
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
                Text(stringResource(R.string.delete_document_title))
            },
            text = {
                Text(documentDeleteConfirmationMessage(detail.document.title))
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentDetail = detail
                        isDeleting = true
                        deleteError = null
                        scope.launch {
                            runCatching {
                                documentRepository.deleteDocument(currentDetail.document.documentId)
                            }.onSuccess {
                                showDeleteConfirmation = false
                                onBack()
                            }.onFailure { error ->
                                deleteError = error.message ?: unableDeleteDocumentMessage
                            }
                            isDeleting = false
                        }
                    },
                    enabled = !isDeleting,
                ) {
                    Text(stringResource(if (isDeleting) R.string.deleting else R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    enabled = !isDeleting,
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
internal fun DocumentDetailHeader(
    detail: DocumentDetail,
    appFileStorage: AppFileStorage,
    onOpenSourcePreview: (SourcePreviewRequest) -> Unit,
) {
    val previewSources = remember(detail.sources) { previewableImageSources(detail.sources) }
    val sourceSummary = remember(detail.sources) { documentSourceSummary(detail) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = vehicleCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = detail.document.title ?: stringResource(R.string.imported_document_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(
                    R.string.document_detail_status,
                    detail.document.importStatus,
                    detail.document.extractionStatus,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = sourceSummary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.document_updated, detail.document.updatedAt),
                style = MaterialTheme.typography.bodySmall,
            )
            detail.latestRun?.let { run ->
                Text(
                    text = stringResource(
                        R.string.document_latest_run,
                        run.status,
                        run.providerName,
                        run.modelName,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = stringResource(R.string.document_cloud_disclosure),
                style = MaterialTheme.typography.bodySmall,
            )
            if (previewSources.isNotEmpty()) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.imported_images_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
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
private fun DocumentExtractionStatusCard(
    detail: DocumentDetail,
) {
    val statusMessage = remember(detail) { extractionStatusMessage(detail) } ?: return
    val title = if (detail.document.extractionStatus == "FAILED") {
        stringResource(R.string.extraction_issue_title)
    } else {
        stringResource(R.string.extraction_status_title)
    }
    val isFailure = detail.document.extractionStatus == "FAILED"
    if (isFailure) {
        ErrorCard(message = statusMessage)
    } else {
        InfoCard(title = title, message = statusMessage)
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
                text = stringResource(R.string.corrections_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.corrections_subtitle),
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
                                    text = field.currentValue ?: stringResource(R.string.not_set_yet),
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
                                Text(stringResource(if (editingFactType == field.factType) R.string.editing else R.string.edit))
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
                                    Text(selectedDate?.toString() ?: stringResource(R.string.pick_date))
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
                                label = { Text(stringResource(R.string.correction_note_optional)) },
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
                                    Text(stringResource(R.string.cancel))
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
                                    Text(stringResource(if (isSaving) R.string.saving else R.string.save))
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
            text = "${event.eventDate} | ${event.shopName ?: "Unknown shop"} | ${eventVerificationLabel(event.status)}",
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
                    if (model.isVerified) {
                        VerifiedServiceItemBadge()
                    }
                    if (isExpanded) {
                        IconButton(onClick = { isEditing = !isEditing }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_action_edit),
                                contentDescription = if (isEditing) "Hide edit service item" else "Edit service item",
                            )
                        }
                        IconButton(
                            onClick = { showDeleteConfirmation = true },
                            enabled = !isSaving,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_action_delete),
                                contentDescription = "Remove service item",
                            )
                        }
                    }
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            painter = painterResource(
                                if (isExpanded) {
                                    R.drawable.ic_action_expand_less
                                } else {
                                    R.drawable.ic_action_expand_more
                                },
                            ),
                            contentDescription = if (isExpanded) {
                                "Hide linked line item"
                            } else {
                                "Show linked line item"
                            },
                        )
                    }
                }
            }

            if (isExpanded && isEditing) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
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
private fun VerifiedServiceItemBadge() {
    Box(
        modifier = Modifier
            .padding(top = 8.dp, end = 4.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(
                color = VERIFIED_BADGE_GREEN.copy(alpha = 0.16f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_status_check),
            contentDescription = "Verified service item",
            tint = VERIFIED_BADGE_GREEN,
            modifier = Modifier.size(16.dp),
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

internal fun Uri.toSourceImportRequest(context: Context): SourceImportRequest {
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

internal fun eventVerificationLabel(status: String?): String = when (status?.trim()?.uppercase()) {
    "USER_VERIFIED" -> "Verified"
    "EXTRACTED_UNVERIFIED",
    "UNVERIFIED",
    -> "Unverified"

    null -> "Unverified"
    else -> status
}

internal fun extractionStatusMessage(detail: DocumentDetail): String? {
    val run = detail.latestRun
    return when (detail.document.extractionStatus) {
        "NOT_STARTED" -> "Extraction has not started yet. Import a document while cloud extraction is configured to process it."
        "QUEUED" -> when {
            run?.errorCode == "PROVIDER_OFFLINE" -> "Extraction is queued because the device could not reach the cloud provider. Reconnect to the network and the background retry will resume."
            run?.errorCode == "PROVIDER_TIMEOUT" || run?.errorCode == "PROVIDER_HTTP_408" || run?.errorCode == "PROVIDER_HTTP_429" -> "Extraction is queued for retry because the cloud provider timed out or asked Orca to slow down."
            else -> "Extraction is queued. Large PDFs and multi-file documents can take longer because the original source files must upload before processing starts."
        }

        "RUNNING" -> "Extraction is running in the background. Large PDFs and multi-file documents may take a few minutes while the original files upload and the structured result is validated locally."
        "FAILED" -> extractionFailureMessage(run?.errorCode)
        "SUCCEEDED" -> "Extraction completed. Review the facts and service items, especially any fields that still need correction."
        else -> null
    }
}

internal fun extractionFailureMessage(errorCode: String?): String {
    return when (errorCode) {
        "PROVIDER_OFFLINE" -> "The device could not reach the cloud provider. Check connectivity, then retry extraction."
        "PROVIDER_TIMEOUT",
        "PROVIDER_HTTP_408",
        "PROVIDER_HTTP_429",
        -> "The cloud provider timed out or was busy. Retry the document when the network and provider are available."

        "PROVIDER_TLS" -> "A secure connection to the cloud provider could not be established. Retry on a stable network."
        "MALFORMED_PROVIDER_RESPONSE" -> "The cloud provider returned data Orca could not validate. Retry the document or inspect the original files for unusual formatting."
        null -> "Extraction failed before structured maintenance facts were saved. Retry the document after checking the original files and network."
        else -> "Extraction failed with error $errorCode. Retry the document after checking connectivity and the imported files."
    }
}

internal fun documentSourceSummary(detail: DocumentDetail): String {
    val sourceCount = detail.sources.size
    val totalBytes = detail.sources.sumOf { it.byteSize }
    val fileLabel = if (sourceCount == 1) "file" else "files"
    return "$sourceCount $fileLabel | ${formatByteCount(totalBytes)} total"
}

internal fun formatByteCount(bytes: Long): String {
    if (bytes < 1024) {
        return "$bytes B"
    }
    val unitIndex = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(BYTE_UNITS.lastIndex)
    val unitBytes = 1024.0.pow(unitIndex.toDouble())
    val value = bytes / unitBytes
    return String.format("%.1f %s", value, BYTE_UNITS[unitIndex])
}

private val BYTE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

internal data class ServiceItemDisplayModel(
    val serviceItem: ServiceItemEntity,
    val isVerified: Boolean,
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
            isVerified = serviceItem.isUserEdited,
            linkedLineItem = linkedLineItem,
            linkedSource = resolveLinkedSource(serviceItem, linkedLineItem, sourcesById),
        )
    }
}

private val VERIFIED_BADGE_GREEN = Color(0xFF2E7D32)

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
