package org.vliux.orca.ui.vehicle

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import org.vliux.orca.BuildConfig
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.R
import org.vliux.orca.data.SearchRepository
import org.vliux.orca.data.VehicleRepository
import org.vliux.orca.data.model.VehicleEntity
import org.vliux.orca.search.SearchAnswer
import org.vliux.orca.search.SearchCitation
import org.vliux.orca.search.SearchResultItem
import org.vliux.orca.settings.ImportDocumentGuidancePreference
import org.vliux.orca.worker.DocumentExtractionQueueResult
import org.vliux.orca.worker.DocumentExtractionScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VehicleListScreen(
    vehicleRepository: VehicleRepository,
    documentRepository: DocumentRepository,
    documentExtractionScheduler: DocumentExtractionScheduler,
    importDocumentGuidancePreference: ImportDocumentGuidancePreference,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenSearch: (String) -> Unit,
    onCreateVehicle: () -> Unit,
    onOpenVehicle: (String) -> Unit,
    onOpenDocument: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current as? FragmentActivity
    val context = LocalContext.current
    val vehiclesFlow = remember(vehicleRepository) { vehicleRepository.observeVehicles() }
    val vehicles by vehiclesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val importFailedMessage = stringResource(R.string.vehicle_import_failed)
    var menuExpanded by remember { mutableStateOf(false) }
    var fabMenuOpen by remember { mutableStateOf(false) }
    var vehiclePickerOpen by remember { mutableStateOf(false) }
    var importGuidanceOpen by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var selectedImportVehicleId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris ->
        val vehicleId = selectedImportVehicleId
        selectedImportVehicleId = null
        if (uris.isEmpty() || vehicleId == null) {
            isImporting = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            importError = null
            runCatching {
                val requests = uris.map { uri -> uri.toSourceImportRequest(context) }
                val documentId = documentRepository.importDocument(vehicleId, requests)
                val queueResult = documentExtractionScheduler.queueDocumentExtraction(documentId, activity)
                VehicleListImportResult(vehicleId = vehicleId, documentId = documentId, queueResult = queueResult)
            }.onSuccess { result ->
                when (result.queueResult) {
                    DocumentExtractionQueueResult.QUEUED,
                    DocumentExtractionQueueResult.NOT_CONFIGURED,
                    DocumentExtractionQueueResult.AUTH_CANCELLED,
                    DocumentExtractionQueueResult.AUTH_UNAVAILABLE,
                    DocumentExtractionQueueResult.ERROR,
                    -> onOpenDocument(result.vehicleId, result.documentId)
                }
            }.onFailure { error ->
                importError = error.message ?: importFailedMessage
            }
            isImporting = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppIcon()
                    SearchPlaceholderField(
                        onOpenSearch = onOpenSearch,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .widthIn(max = 520.dp),
                    )
                    Box(
                        modifier = Modifier.wrapContentSize(Alignment.TopEnd),
                    ) {
                        OverflowMenuButton(
                            expanded = menuExpanded,
                            onExpandedChange = { menuExpanded = it },
                        )
                        OverflowMenuDropdown(
                            expanded = menuExpanded,
                            onExpandedChange = { menuExpanded = it },
                            onOpenSettings = onOpenSettings,
                            onOpenDebug = onOpenDebug,
                            showDebugMenu = BuildConfig.DEBUG,
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            Box(
                modifier = Modifier.wrapContentSize(Alignment.BottomEnd),
            ) {
                DropdownMenu(
                    expanded = fabMenuOpen,
                    onDismissRequest = { fabMenuOpen = false },
                    offset = DpOffset(x = 0.dp, y = (-8).dp),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.create_vehicle_action)) },
                        onClick = {
                            fabMenuOpen = false
                            onCreateVehicle()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_document)) },
                        onClick = {
                            fabMenuOpen = false
                            vehiclePickerOpen = true
                        },
                    )
                }
                FloatingActionButton(
                    onClick = {
                        if (vehicles.isEmpty()) {
                            onCreateVehicle()
                        } else {
                            fabMenuOpen = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            importError?.let { message ->
                item {
                    ErrorCard(message = message)
                }
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

    if (vehiclePickerOpen) {
        ImportVehiclePickerDialog(
            vehicles = vehicles,
            isImporting = isImporting,
            onDismiss = {
                if (!isImporting) {
                    vehiclePickerOpen = false
                }
            },
            onSelectVehicle = { vehicleId ->
                vehiclePickerOpen = false
                selectedImportVehicleId = vehicleId
                if (importDocumentGuidancePreference.shouldShowGuidance()) {
                    importGuidanceOpen = true
                } else {
                    isImporting = true
                    launcher.launch(SUPPORTED_IMPORT_MIME_TYPES)
                }
            },
        )
    }

    if (importGuidanceOpen) {
        ImportDocumentGuidanceDialog(
            onDismiss = {
                importGuidanceOpen = false
                selectedImportVehicleId = null
                isImporting = false
            },
            onContinue = { dontShowAgain ->
                if (dontShowAgain) {
                    importDocumentGuidancePreference.disableGuidance()
                }
                importGuidanceOpen = false
                isImporting = true
                launcher.launch(SUPPORTED_IMPORT_MIME_TYPES)
            },
        )
    }
}

@Composable
internal fun ImportVehiclePickerDialog(
    vehicles: List<VehicleEntity>,
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onSelectVehicle: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_existing_vehicle_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.import_existing_vehicle_message))
                vehicles.forEach { vehicle ->
                    TextButton(
                        onClick = { onSelectVehicle(vehicle.vehicleId) },
                        enabled = !isImporting,
                    ) {
                        Text(vehiclePickerLabel(vehicle))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isImporting,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

internal fun vehiclePickerLabel(vehicle: VehicleEntity): String = buildString {
    append(vehicle.displayName)
    append(" | ")
    append(vehicle.year)
    append(' ')
    append(vehicle.make)
    append(' ')
    append(vehicle.model)
}

internal data class VehicleListImportResult(
    val vehicleId: String,
    val documentId: String,
    val queueResult: DocumentExtractionQueueResult,
)

@Composable
private fun AppIcon() {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = stringResource(R.string.orca_app_icon_content_description),
        modifier = Modifier
            .size(28.dp)
            .clip(MaterialTheme.shapes.small),
        contentScale = ContentScale.Crop,
    )
}

@Composable
internal fun SearchPlaceholderField(
    onOpenSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }

    SearchInputField(
        value = query,
        onValueChange = { query = it },
        modifier = modifier.testTag(SearchPlaceholderFieldTestTag),
        placeholder = stringResource(R.string.search_records_placeholder),
        onSearch = { onOpenSearch(query.trim()) },
    )
}

internal const val SearchPlaceholderFieldTestTag = "searchPlaceholderField"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchScreen(
    searchRepository: SearchRepository,
    initialQuery: String,
    onBack: () -> Unit,
    onOpenDocument: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchViewModel: SearchScreenViewModel = viewModel(
        key = "search:$initialQuery",
        factory = remember(initialQuery, searchRepository) {
            viewModelFactory {
                initializer {
                    SearchScreenViewModel(
                        initialQuery = initialQuery,
                        searchRepository = searchRepository,
                    )
                }
            }
        },
    )
    val screenState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                VehicleTopBar(
                    title = stringResource(R.string.search_and_ask_title),
                    onBack = onBack,
                    applyStatusBarPadding = false,
                )
                SearchInputField(
                    value = screenState.query,
                    onValueChange = searchViewModel::updateQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .focusRequester(focusRequester),
                    placeholder = stringResource(R.string.search_hint_last_service),
                    onSearch = {},
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            if (screenState.query.isBlank()) {
                item {
                    InfoCard(
                        title = stringResource(R.string.supported_questions_title),
                        message = stringResource(R.string.supported_questions_message),
                    )
                }
            } else {
                item {
                    when {
                        screenState.answerLoading -> CircularProgressIndicator()
                        screenState.answer != null -> SearchAnswerCard(
                            answer = requireNotNull(screenState.answer),
                            onOpenDocument = onOpenDocument,
                        )
                    }
                }
                item {
                    SectionHeader(
                        title = stringResource(R.string.matching_records_title),
                        subtitle = stringResource(R.string.matching_records_subtitle),
                    )
                }
                if (!screenState.resultsLoaded) {
                    item {
                        CircularProgressIndicator()
                    }
                } else if (screenState.results.isEmpty()) {
                    item {
                        InfoCard(
                            title = stringResource(R.string.no_matches_title),
                            message = stringResource(R.string.no_matches_message),
                        )
                    }
                } else {
                    items(screenState.results, key = { it.entryId }) { result ->
                        SearchResultCard(
                            result = result,
                            onOpenDocument = {
                                onOpenDocument(result.vehicleId, result.documentId)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    val textStyle = MaterialTheme.typography.bodyLarge
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(
                onSearch = { onSearch() },
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )
    }
}

@Composable
private fun OverflowMenuButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    IconButton(
        onClick = { onExpandedChange(!expanded) },
        modifier = Modifier.width(32.dp),
    ) {
        Text(
            text = "\u22ee",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OverflowMenuDropdown(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
    showDebugMenu: Boolean,
) {
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = { onExpandedChange(false) },
    ) {
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(stringResource(R.string.settings)) },
            onClick = {
                onExpandedChange(false)
                onOpenSettings()
            },
        )
        if (showDebugMenu) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.debug_menu_title)) },
                onClick = {
                    onExpandedChange(false)
                    onOpenDebug()
                },
            )
        }
    }
}

@Composable
private fun SearchAnswerCard(
    answer: SearchAnswer,
    onOpenDocument: (String, String) -> Unit,
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
                text = stringResource(R.string.answer_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = answer.answer,
                style = MaterialTheme.typography.bodyMedium,
            )
            answer.relatedDate?.let { relatedDate ->
                Text(
                    text = stringResource(
                        R.string.answer_date,
                        relatedDate.format(vehicleSummaryDateFormatter),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            answer.relatedMileage?.let { mileage ->
                Text(
                    text = stringResource(R.string.answer_mileage, mileage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            answer.uncertaintyNote?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (answer.citations.isNotEmpty()) {
                androidx.compose.material3.HorizontalDivider()
                Text(
                    text = stringResource(R.string.citations_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                answer.citations.forEach { citation ->
                    TextButton(
                        onClick = { onOpenDocument(citation.vehicleId, citation.documentId) },
                    ) {
                        Text(citationLabel(citation))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResultItem,
    onOpenDocument: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDocument),
        colors = vehicleCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = result.body,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = buildString {
                    append(result.documentTitle)
                    result.occurredOn?.let { append(" | ${it.format(vehicleSummaryDateFormatter)}") }
                    result.sourcePage?.let { append(" | ${stringResource(R.string.search_result_page, it)}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun citationLabel(citation: SearchCitation): String = buildString {
    append(citation.documentTitle)
    citation.sourcePage?.let { append(" ${stringResource(R.string.citation_page, it)}") }
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
                text = stringResource(R.string.no_vehicles_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.no_vehicles_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            androidx.compose.material3.Button(onClick = onCreateVehicle) {
                Text(stringResource(R.string.create_first_vehicle))
            }
        }
    }
}

@Composable
private fun VehicleSummaryCard(
    vehicle: VehicleEntity,
    onOpenVehicle: () -> Unit,
) {
    VehicleProfileCard(
        vehicle = vehicle,
        onClick = onOpenVehicle,
        imageSize = 88.dp,
    )
}
