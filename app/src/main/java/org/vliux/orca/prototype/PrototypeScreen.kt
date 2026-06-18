package org.vliux.orca.prototype

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlinx.coroutines.launch
import org.vliux.orca.prototype.layout.LayoutNormalizer
import org.vliux.orca.prototype.ocr.MlKitOcrEngine
import org.vliux.orca.ui.theme.OrcaTheme

@Composable
fun PrototypeScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageJobs = remember { mutableStateListOf<PrototypeImageJob>() }
    val coroutineScope = rememberCoroutineScope()
    val ocrEngine = remember(context) { MlKitOcrEngine(context.applicationContext) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        uris.forEach { uri ->
            val job = PrototypeImageJob(
                id = UUID.randomUUID().toString(),
                uri = uri,
            )
            imageJobs.add(job)
            coroutineScope.launch {
                imageJobs.updateJob(job.id) { it.copy(status = PrototypeStatus.OcrRunning) }
                runCatching { ocrEngine.recognize(uri) }
                    .onSuccess { result ->
                        val normalizedRows = LayoutNormalizer.normalizeRows(result)
                        imageJobs.updateJob(job.id) {
                            it.copy(
                                status = PrototypeStatus.OcrReady,
                                ocrResult = result,
                                normalizedRows = normalizedRows,
                                errorMessage = null,
                            )
                        }
                    }
                    .onFailure { error ->
                        imageJobs.updateJob(job.id) {
                            it.copy(
                                status = PrototypeStatus.Failed,
                                errorMessage = error.message ?: error::class.java.simpleName,
                            )
                        }
                    }
            }
        }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        PrototypeContent(
            imageJobs = imageJobs,
            onImportPhotosClick = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            contentPadding = innerPadding,
        )
    }
}

@Composable
private fun PrototypeContent(
    imageJobs: List<PrototypeImageJob>,
    onImportPhotosClick: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "OCR Prototype",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Import maintenance document photos to inspect OCR and deterministic extraction results.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = onImportPhotosClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Import Photos")
        }

        Spacer(modifier = Modifier.height(4.dp))

        SelectedImagesCard(imageJobs = imageJobs)
    }
}

@Composable
private fun SelectedImagesCard(
    imageJobs: List<PrototypeImageJob>,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Selected Images",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (imageJobs.isEmpty()) {
                Text(
                    text = "No photos selected yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    imageJobs.forEach { job ->
                        SelectedImageRow(job = job)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedImageRow(
    job: PrototypeImageJob,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = job.uri.lastPathSegment ?: "Selected photo",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Status: ${job.status.name.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
            )
            job.ocrResult?.let { result ->
                Text(
                    text = "OCR: ${result.blocks.size} blocks, ${job.normalizedRows.size} rows in ${result.elapsedMs} ms",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Full OCR Text",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                TextPreview(text = result.fullText)
                Text(
                    text = "Normalized Rows",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                TextPreview(
                    text = job.normalizedRows.joinToString("\n") { row -> row.text },
                    emptyText = "(No OCR rows recognized)",
                )
            }
            job.errorMessage?.let { message ->
                Text(
                    text = "Error: $message",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = job.uri.toString(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TextPreview(
    text: String,
    emptyText: String = "(No text recognized)",
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                text = text.ifBlank { emptyText },
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun MutableList<PrototypeImageJob>.updateJob(
    jobId: String,
    update: (PrototypeImageJob) -> PrototypeImageJob,
) {
    val index = indexOfFirst { it.id == jobId }
    if (index >= 0) {
        this[index] = update(this[index])
    }
}

@Preview(showBackground = true)
@Composable
private fun PrototypeScreenPreview() {
    OrcaTheme {
        PrototypeScreen()
    }
}
