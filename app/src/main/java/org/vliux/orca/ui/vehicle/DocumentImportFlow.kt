package org.vliux.orca.ui.vehicle

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import org.vliux.orca.importing.ImportCaptureFileStore
import org.vliux.orca.importing.SourceImportRequest

internal enum class ImportAcquisitionMethod {
    FILES,
    SCAN_DOCUMENT,
    CAMERA_CAPTURE,
}

internal data class DocumentImportFlow(
    val chooserOpen: Boolean,
    val pendingReviewSources: List<PendingImportSource>?,
    val pendingScannerReviewSources: List<PendingImportSource>?,
    val beginImport: () -> Unit,
    val dismissChooser: () -> Unit,
    val chooseFiles: () -> Unit,
    val chooseScan: () -> Unit,
    val dismissReview: () -> Unit,
    val completeReview: (List<SourceImportRequest>) -> Unit,
    val dismissScannerReview: () -> Unit,
    val completeScannerReview: () -> Unit,
    val addMoreScannerPages: () -> Unit,
)

internal enum class DocumentImportOverlayStep {
    NONE,
    CHOOSER,
    IMAGE_REVIEW,
    SCANNER_REVIEW,
}

internal fun documentImportOverlayStepFor(
    chooserOpen: Boolean,
    pendingReviewSources: List<PendingImportSource>?,
    pendingScannerReviewSources: List<PendingImportSource>?,
): DocumentImportOverlayStep = when {
    chooserOpen -> DocumentImportOverlayStep.CHOOSER
    pendingReviewSources != null -> DocumentImportOverlayStep.IMAGE_REVIEW
    pendingScannerReviewSources != null -> DocumentImportOverlayStep.SCANNER_REVIEW
    else -> DocumentImportOverlayStep.NONE
}

internal sealed interface PendingImportStage {
    data object Cancel : PendingImportStage
    data class ImageReview(val sources: List<PendingImportSource>) : PendingImportStage
    data class ScannerReview(val sources: List<PendingImportSource>) : PendingImportStage
    data class ImportReady(val sources: List<PendingImportSource>) : PendingImportStage
}

internal enum class PendingImportStageType {
    CANCEL,
    IMAGE_REVIEW,
    SCANNER_REVIEW,
    IMPORT_READY,
}

internal fun pendingImportStageTypeFor(
    mimeTypesAndMethods: List<Pair<String?, ImportAcquisitionMethod>>,
): PendingImportStageType {
    if (mimeTypesAndMethods.isEmpty()) {
        return PendingImportStageType.CANCEL
    }
    return when {
        mimeTypesAndMethods.all { it.second == ImportAcquisitionMethod.SCAN_DOCUMENT } ->
            PendingImportStageType.SCANNER_REVIEW
        mimeTypesAndMethods.any { requiresImageReview(it.first, it.second) } ->
            PendingImportStageType.IMAGE_REVIEW
        else -> PendingImportStageType.IMPORT_READY
    }
}

internal fun pendingImportStageFor(
    sources: List<PendingImportSource>,
): PendingImportStage {
    return when (pendingImportStageTypeFor(sources.map { it.mimeType to it.acquisitionMethod })) {
        PendingImportStageType.CANCEL -> PendingImportStage.Cancel
        PendingImportStageType.IMAGE_REVIEW -> PendingImportStage.ImageReview(sources)
        PendingImportStageType.SCANNER_REVIEW -> PendingImportStage.ScannerReview(sources)
        PendingImportStageType.IMPORT_READY -> PendingImportStage.ImportReady(sources)
    }
}

@Composable
internal fun rememberDocumentImportFlow(
    activity: FragmentActivity?,
    importCaptureFileStore: ImportCaptureFileStore,
    onImportRequestsReady: (List<SourceImportRequest>) -> Unit,
    onFlowCancelled: () -> Unit,
): DocumentImportFlow {
    val context = LocalContext.current
    val latestImportRequestsReady by rememberUpdatedState(onImportRequestsReady)
    val latestFlowCancelled by rememberUpdatedState(onFlowCancelled)
    var chooserOpen by remember { mutableStateOf(false) }
    var pendingReviewSources by remember { mutableStateOf<List<PendingImportSource>?>(null) }
    var pendingScannerReviewSources by remember { mutableStateOf<List<PendingImportSource>?>(null) }
    var pendingCaptureTarget by remember { mutableStateOf<ImportCaptureFileStore.CaptureTarget?>(null) }
    var scannerAppendMode by remember { mutableStateOf(false) }
    val scannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(24)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val scanner = remember(scannerOptions) { GmsDocumentScanning.getClient(scannerOptions) }

    fun cleanupSources(sources: List<PendingImportSource>) {
        sources.forEach(PendingImportSource::markConsumed)
    }

    fun cancelFlow() {
        pendingCaptureTarget?.let {
            importCaptureFileStore.deleteCaptureTarget(it.file)
            pendingCaptureTarget = null
        }
        latestFlowCancelled()
    }

    fun handlePendingSources(sources: List<PendingImportSource>) {
        when (val stage = pendingImportStageFor(sources)) {
            PendingImportStage.Cancel -> cancelFlow()
            is PendingImportStage.ImageReview -> {
                pendingReviewSources = stage.sources
            }
            is PendingImportStage.ScannerReview -> {
                pendingScannerReviewSources = stage.sources
            }
            is PendingImportStage.ImportReady -> {
                latestImportRequestsReady(stage.sources.map { it.toSourceImportRequest(context) })
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(TakePicture()) { succeeded ->
        val target = pendingCaptureTarget
        pendingCaptureTarget = null
        if (!succeeded || target == null) {
            target?.let { importCaptureFileStore.deleteCaptureTarget(it.file) }
            cancelFlow()
            return@rememberLauncherForActivityResult
        }
        pendingReviewSources = listOf(
            PendingImportSource(
                selectionIndex = 0,
                uri = target.uri,
                displayName = target.file.name,
                mimeType = "image/jpeg",
                acquisitionMethod = ImportAcquisitionMethod.CAMERA_CAPTURE,
                onConsumed = {
                    importCaptureFileStore.deleteCaptureTarget(target.file)
                },
            ),
        )
    }

    fun startCameraFallback() {
        val target = importCaptureFileStore.createCaptureTarget()
        pendingCaptureTarget = target
        cameraLauncher.launch(target.uri)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris ->
        chooserOpen = false
        if (uris.isEmpty()) {
            cancelFlow()
            return@rememberLauncherForActivityResult
        }
        handlePendingSources(
            uris.mapIndexed { index, uri ->
                uri.toPendingImportSource(
                    context = context,
                    selectionIndex = index,
                    acquisitionMethod = ImportAcquisitionMethod.FILES,
                )
            },
        )
    }

    val scannerLauncher = rememberLauncherForActivityResult(StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            if (!scannerAppendMode) {
                cancelFlow()
            }
            scannerAppendMode = false
            return@rememberLauncherForActivityResult
        }
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val baseIndex = if (scannerAppendMode) pendingScannerReviewSources?.size ?: 0 else 0
        val newSources = scanResult?.pages.orEmpty().mapIndexed { index, page ->
            PendingImportSource(
                selectionIndex = baseIndex + index,
                uri = page.imageUri,
                displayName = scannerPageDisplayName(baseIndex + index),
                mimeType = "image/jpeg",
                acquisitionMethod = ImportAcquisitionMethod.SCAN_DOCUMENT,
            )
        }
        scannerAppendMode = false
        handlePendingSources(
            if (baseIndex == 0) {
                newSources
            } else {
                pendingScannerReviewSources.orEmpty() + newSources
            }.mapIndexed { index, source ->
                PendingImportSource(
                    selectionIndex = index,
                    uri = source.uri,
                    displayName = scannerPageDisplayName(index),
                    mimeType = source.mimeType,
                    acquisitionMethod = source.acquisitionMethod,
                    onConsumed = source.onConsumed,
                )
            },
        )
    }

    fun launchScanner() {
        if (activity == null) {
            startCameraFallback()
        } else {
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener {
                    startCameraFallback()
                }
        }
    }

    return DocumentImportFlow(
        chooserOpen = chooserOpen,
        pendingReviewSources = pendingReviewSources,
        pendingScannerReviewSources = pendingScannerReviewSources,
        beginImport = {
            chooserOpen = true
        },
        dismissChooser = {
            chooserOpen = false
            cancelFlow()
        },
        chooseFiles = {
            chooserOpen = false
            filePickerLauncher.launch(SUPPORTED_IMPORT_MIME_TYPES)
        },
        chooseScan = {
            chooserOpen = false
            scannerAppendMode = false
            launchScanner()
        },
        dismissReview = {
            pendingReviewSources?.let(::cleanupSources)
            pendingReviewSources = null
            cancelFlow()
        },
        completeReview = { requests ->
            pendingReviewSources = null
            latestImportRequestsReady(requests)
        },
        dismissScannerReview = {
            pendingScannerReviewSources?.let(::cleanupSources)
            pendingScannerReviewSources = null
            cancelFlow()
        },
        completeScannerReview = {
            val sources = pendingScannerReviewSources
            pendingScannerReviewSources = null
            if (sources == null) {
                cancelFlow()
            } else {
                latestImportRequestsReady(sources.map { it.toSourceImportRequest(context) })
            }
        },
        addMoreScannerPages = {
            scannerAppendMode = true
            launchScanner()
        },
    )
}

internal fun scannerPageDisplayName(index: Int): String =
    "scan-page-${(index + 1).toString().padStart(3, '0')}.jpg"
