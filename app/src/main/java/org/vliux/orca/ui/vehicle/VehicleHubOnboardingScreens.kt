package org.vliux.orca.ui.vehicle

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.vliux.orca.R
import org.vliux.orca.data.DocumentRepository
import org.vliux.orca.data.VehicleRepository
import org.vliux.orca.importing.ImportCaptureFileStore
import org.vliux.orca.settings.ImportDocumentGuidancePreference
import org.vliux.orca.settings.OpenAiApiKeyAccessState
import org.vliux.orca.settings.OpenAiApiKeyMutationResult
import org.vliux.orca.settings.OpenAiApiKeyStore
import org.vliux.orca.worker.DocumentExtractionQueueResult
import org.vliux.orca.worker.DocumentExtractionScheduler

internal const val OnboardingApiKeyFieldTestTag = "onboardingApiKeyField"
private const val OnboardingMaskedApiKeyDisplay = "................................"

@Composable
internal fun OnboardingWelcomeScreen(
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            OnboardingStepHeader(
                step = 1,
                title = stringResource(R.string.onboarding_welcome_title),
                body = stringResource(R.string.onboarding_welcome_message),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = vehicleCardColors(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_welcome_summary_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OnboardingBullet(text = stringResource(R.string.onboarding_welcome_bullet_vehicle))
                    OnboardingBullet(text = stringResource(R.string.onboarding_welcome_bullet_local))
                    OnboardingBullet(text = stringResource(R.string.onboarding_welcome_bullet_ai))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.next))
            }
        }
    }
}

@Composable
internal fun OnboardingVehicleSetupScreen(
    vehicleRepository: VehicleRepository,
    onAddVehicle: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vehicles by remember(vehicleRepository) { vehicleRepository.observeVehicles() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    OnboardingScaffold(
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
        ) {
            item {
                OnboardingStepHeader(
                    step = 2,
                    title = stringResource(R.string.onboarding_vehicle_title),
                    body = stringResource(R.string.onboarding_vehicle_message),
                )
            }
            if (vehicles.isEmpty()) {
                item {
                    InfoCard(
                        title = stringResource(R.string.onboarding_vehicle_empty_title),
                        message = stringResource(R.string.onboarding_vehicle_empty_message),
                    )
                }
            } else {
                items(vehicles, key = { it.vehicleId }) { vehicle ->
                    VehicleProfileCard(
                        vehicle = vehicle,
                        imageSize = 80.dp,
                    )
                }
            }
            item {
                Button(
                    onClick = onAddVehicle,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (vehicles.isEmpty()) R.string.onboarding_vehicle_primary_empty
                            else R.string.onboarding_vehicle_primary_add_another,
                        ),
                    )
                }
            }
            if (vehicles.isNotEmpty()) {
                item {
                    TextButton(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.next))
                    }
                }
            }
        }
    }
}

@Composable
internal fun OnboardingApiKeyScreen(
    openAiApiKeyStore: OpenAiApiKeyStore,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current as? FragmentActivity
    val uriHandler = LocalUriHandler.current
    val savedApiKey by openAiApiKeyStore.observeApiKey().collectAsStateWithLifecycle()
    val accessState by openAiApiKeyStore.observeAccessState().collectAsStateWithLifecycle()
    val hasStoredApiKey = accessState != OpenAiApiKeyAccessState.NOT_STORED
    var draftApiKey by rememberSaveable(savedApiKey, hasStoredApiKey) {
        mutableStateOf(if (hasStoredApiKey) OnboardingMaskedApiKeyDisplay else "")
    }
    var showingMaskedStoredKey by rememberSaveable(savedApiKey, hasStoredApiKey) { mutableStateOf(hasStoredApiKey) }
    var hasEditedDraft by rememberSaveable(savedApiKey, hasStoredApiKey) { mutableStateOf(false) }
    var saveMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.openai_api_key_saved)
    val authCancelledMessage = stringResource(R.string.openai_api_key_auth_cancelled)
    val authUnavailableMessage = stringResource(R.string.openai_api_key_auth_unavailable)
    val errorMessage = stringResource(R.string.openai_api_key_error)

    OnboardingScaffold(
        modifier = modifier,
        onBack = onBack,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OnboardingStepHeader(
                step = 3,
                title = stringResource(R.string.onboarding_api_key_title),
                body = stringResource(R.string.onboarding_api_key_message),
                optional = true,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = vehicleCardColors(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_api_key_get_one),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(
                        onClick = { uriHandler.openUri("https://platform.openai.com") },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(stringResource(R.string.onboarding_api_key_open_platform))
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OnboardingBullet(text = stringResource(R.string.onboarding_api_key_local_only))
                        OnboardingBullet(text = stringResource(R.string.onboarding_api_key_auth_note))
                    }
                }
            }
            OutlinedTextField(
                value = draftApiKey,
                onValueChange = {
                    if (showingMaskedStoredKey) {
                        showingMaskedStoredKey = false
                    }
                    draftApiKey = it
                    hasEditedDraft = true
                    saveMessage = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(OnboardingApiKeyFieldTestTag),
                label = { Text(stringResource(R.string.openai_api_key_label)) },
                placeholder = { Text(stringResource(R.string.openai_api_key_placeholder)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            )
            saveMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
            Button(
                onClick = {
                    val hostActivity = activity
                    if (hostActivity == null) {
                        saveMessage = authUnavailableMessage
                        return@Button
                    }
                    scope.launch {
                        saveMessage = when (
                            openAiApiKeyStore.saveApiKey(
                                activity = hostActivity,
                                apiKey = draftApiKey.trim(),
                            )
                        ) {
                            OpenAiApiKeyMutationResult.SAVED -> {
                                onSaved()
                                savedMessage
                            }
                            OpenAiApiKeyMutationResult.AUTH_CANCELLED -> authCancelledMessage
                            OpenAiApiKeyMutationResult.AUTH_UNAVAILABLE -> authUnavailableMessage
                            else -> errorMessage
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = draftApiKey.trim().isNotBlank() && (!showingMaskedStoredKey || hasEditedDraft),
            ) {
                Text(stringResource(R.string.onboarding_api_key_save_and_continue))
            }
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.skip_for_now))
            }
        }
    }
}

@Composable
internal fun OnboardingImportScreen(
    vehicleRepository: VehicleRepository,
    documentRepository: DocumentRepository,
    documentExtractionScheduler: DocumentExtractionScheduler,
    importDocumentGuidancePreference: ImportDocumentGuidancePreference,
    importCaptureFileStore: ImportCaptureFileStore,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onOpenDocument: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current as? FragmentActivity
    val vehicles by remember(vehicleRepository) { vehicleRepository.observeVehicles() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val importFailedMessage = stringResource(R.string.vehicle_import_failed)
    var importError by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var vehiclePickerOpen by remember { mutableStateOf(false) }
    var selectedImportVehicleId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun importForVehicle(
        vehicleId: String,
        requests: List<org.vliux.orca.importing.SourceImportRequest>,
    ) {
        scope.launch {
            isImporting = true
            importError = null
            runCatching {
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
    val importFlow = rememberDocumentImportFlow(
        activity = activity,
        importDocumentGuidancePreference = importDocumentGuidancePreference,
        importCaptureFileStore = importCaptureFileStore,
        onImportRequestsReady = { requests ->
            val vehicleId = selectedImportVehicleId
            selectedImportVehicleId = null
            if (vehicleId == null) {
                isImporting = false
            } else {
                importForVehicle(vehicleId, requests)
            }
        },
        onFlowCancelled = {
            selectedImportVehicleId = null
            isImporting = false
        },
    )

    fun beginImport() {
        if (vehicles.isEmpty()) {
            return
        }
        if (vehicles.size == 1) {
            selectedImportVehicleId = vehicles.first().vehicleId
            isImporting = true
            importFlow.beginImport()
        } else {
            vehiclePickerOpen = true
        }
    }

    OnboardingScaffold(
        modifier = modifier,
        onBack = onBack,
    ) { innerPadding ->
        OnboardingImportContent(
            hasVehicles = vehicles.isNotEmpty(),
            importError = importError,
            isImporting = isImporting,
            onImport = ::beginImport,
            onFinish = onFinish,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        )
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
                isImporting = true
                importFlow.beginImport()
            },
        )
    }

    if (importFlow.chooserOpen) {
        ImportDocumentGuidanceDialog(
            showGuidanceEducation = importFlow.chooserShowsGuidance,
            onDismiss = importFlow.dismissChooser,
            onChooseFiles = importFlow.chooseFiles,
            onChooseScan = importFlow.chooseScan,
        )
    }

    importFlow.pendingReviewSources?.let { selectedSources ->
        ImportImageReviewDialog(
            sources = selectedSources,
            onDismiss = importFlow.dismissReview,
            onComplete = importFlow.completeReview,
        )
    }

    importFlow.pendingScannerReviewSources?.let { selectedSources ->
        ScannerImportReviewDialog(
            sources = selectedSources,
            onDismiss = importFlow.dismissScannerReview,
            onAddMorePages = importFlow.addMoreScannerPages,
            onSaveAndAnalyze = importFlow.completeScannerReview,
        )
    }
}

@Composable
internal fun OnboardingImportContent(
    hasVehicles: Boolean,
    importError: String?,
    isImporting: Boolean,
    onImport: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 20.dp),
    ) {
        item {
            OnboardingStepHeader(
                step = 4,
                title = stringResource(R.string.onboarding_import_title),
                body = stringResource(R.string.onboarding_import_message),
                optional = true,
            )
        }
        importError?.let { message ->
            item {
                ErrorCard(message = message)
            }
        }
        if (!hasVehicles) {
            item {
                InfoCard(
                    title = stringResource(R.string.onboarding_import_no_vehicle_title),
                    message = stringResource(R.string.onboarding_import_no_vehicle_message),
                )
            }
        }
        item {
            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasVehicles && !isImporting,
            ) {
                Text(stringResource(if (isImporting) R.string.importing else R.string.import_document))
            }
        }
        item {
            TextButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.finish_setup))
            }
        }
    }
}

@Composable
private fun OnboardingScaffold(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (onBack != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back_arrow),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                }
            } else {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(12.dp),
                )
            }
        },
        content = content,
    )
}

@Composable
private fun OnboardingStepHeader(
    step: Int,
    title: String,
    body: String,
    optional: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(
            text = if (optional) {
                stringResource(R.string.onboarding_step_optional, step)
            } else {
                stringResource(R.string.onboarding_step_required, step)
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun OnboardingBullet(
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "\u2022",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
