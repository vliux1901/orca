package org.vliux.orca.prototype

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import androidx.documentfile.provider.DocumentFile
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vliux.orca.OrcaApplication
import org.vliux.orca.prototype.cloudflow.PrototypeLog
import org.vliux.orca.prototype.cloudflow.importing.SourceImportRequest
import org.vliux.orca.prototype.cloudflow.worker.PrototypeExtractionWorker

private const val PROTOTYPE_VEHICLE_ID = "prototype-vehicle"

@Composable
fun CloudPrototypeScreen(
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as OrcaApplication
    val viewModel: CloudPrototypeViewModel = viewModel(
        factory = remember(application) { CloudPrototypeViewModel.Factory(application) },
    )
    val documents by viewModel.documents.collectAsStateWithLifecycle(initialValue = emptyList())
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importSources(uris)
        }
    }

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
                HeaderCard(
                    extractionConfigured = viewModel.extractionConfigured,
                    statusMessage = statusMessage,
                    onImportClick = {
                        picker.launch(arrayOf("image/*", "application/pdf"))
                    },
                )
            }

            items(documents, key = { it.documentId }) { document ->
                DocumentListCard(
                    title = document.title ?: "Untitled document",
                    sourceCount = document.sourceCount,
                    importStatus = document.importStatus,
                    extractionStatus = document.extractionStatus,
                    onOpen = {
                        context.startActivity(DocumentDebugActivity.createIntent(context, document.documentId))
                    },
                )
            }
        }
    }
}

@Composable
private fun HeaderCard(
    extractionConfigured: Boolean,
    statusMessage: String?,
    onImportClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Cloud Vision Prototype",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Import one or more images/PDFs. The app copies them to private storage, queues a worker, sends the original files to the configured cloud extraction endpoint, then persists normalized facts and maintenance events.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (extractionConfigured) {
                    "OpenAI extraction is configured."
                } else {
                    "OpenAI extraction is not configured. Set ORCA_CLOUD_EXTRACTION_API_KEY before testing real extraction."
                },
                color = if (extractionConfigured) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onImportClick, modifier = Modifier.fillMaxWidth()) {
                Text("Import Images Or PDFs")
            }
            statusMessage?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DocumentListCard(
    title: String,
    sourceCount: Int,
    importStatus: String,
    extractionStatus: String,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(text = "$sourceCount source file(s)", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Import: $importStatus", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Extraction: $extractionStatus", style = MaterialTheme.typography.bodySmall)
                }
                Text("Open", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

class CloudPrototypeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val graph = (application as OrcaApplication).graph
    private val repository = graph.repository
    private val workManager = WorkManager.getInstance(application)
    private val _statusMessage = MutableStateFlow<String?>(null)

    val extractionConfigured: Boolean = graph.extractionConfigured
    val documents = repository.observeDocumentList(PROTOTYPE_VEHICLE_ID)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun importSources(uris: List<android.net.Uri>) {
        viewModelScope.launch {
            runCatching {
                val requests = uris.map { uri ->
                    val resolver = getApplication<Application>().contentResolver
                    SourceImportRequest(
                        uri = uri,
                        displayName = DocumentFile.fromSingleUri(getApplication(), uri)?.name
                            ?: (uri.lastPathSegment ?: "imported-file"),
                        mimeType = resolver.getType(uri),
                    )
                }
                val documentId = repository.importDocument(PROTOTYPE_VEHICLE_ID, requests)
                PrototypeLog.info(
                    "Imported ${uris.size} source file(s) for document=${documentId.takeLast(8)}; enqueueing extraction worker.",
                )
                repository.markDocumentQueued(documentId)
                workManager.enqueue(PrototypeExtractionWorker.buildRequest(documentId))
                _statusMessage.value = "Imported ${uris.size} file(s). Extraction queued."
            }.onFailure { error ->
                PrototypeLog.error("Import failed before worker enqueue.", error)
                _statusMessage.value = "Import failed: ${error.message ?: error::class.java.simpleName}"
            }
        }
    }
    class Factory(
        private val application: OrcaApplication,
    ) : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CloudPrototypeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CloudPrototypeViewModel(application) as T
            }
            error("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
