package org.vliux.orca.prototype

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.vliux.orca.OrcaApplication
import org.vliux.orca.data.DocumentDetail
import org.vliux.orca.ui.theme.OrcaTheme

class DocumentDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val documentId = intent.getStringExtra(EXTRA_DOCUMENT_ID) ?: run {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            OrcaTheme {
                DocumentDebugRoute(documentId = documentId)
            }
        }
    }

    companion object {
        private const val EXTRA_DOCUMENT_ID = "document_id"

        fun createIntent(context: Context, documentId: String): Intent =
            Intent(context, DocumentDebugActivity::class.java)
                .putExtra(EXTRA_DOCUMENT_ID, documentId)
    }
}

@Composable
private fun DocumentDebugRoute(
    documentId: String,
    modifier: Modifier = Modifier,
) {
    val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as OrcaApplication
    val viewModel: DocumentDebugViewModel = viewModel(
        factory = remember(application, documentId) { DocumentDebugViewModel.Factory(application, documentId) },
    )
    val detail by viewModel.detail.collectAsStateWithLifecycle(initialValue = null)

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        detail?.let { loaded ->
            DocumentDebugScreen(
                detail = loaded,
                modifier = Modifier.padding(innerPadding),
            )
        } ?: Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Loading document details...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DocumentDebugScreen(
    detail: DocumentDetail,
    modifier: Modifier = Modifier,
) {
    val mappingItems = remember(detail) { buildMappingItems(detail) }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            SummaryCard(detail = detail)
        }
        item {
            MappingCard(mappingItems = mappingItems)
        }
        item {
            SourcesCard(detail = detail)
        }
        item {
            SavedTablesCard(detail = detail)
        }
        item {
            DebugArtifactsCard(detail = detail)
        }
    }
}

@Composable
private fun SummaryCard(detail: DocumentDetail) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = detail.document.title ?: "Untitled document",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text("Document ID: ${detail.document.documentId}", style = MaterialTheme.typography.bodySmall)
            Text("Import: ${detail.document.importStatus}", style = MaterialTheme.typography.bodyMedium)
            Text("Extraction: ${detail.document.extractionStatus}", style = MaterialTheme.typography.bodyMedium)
            detail.latestRun?.let { run ->
                Text("Run: ${run.runId} · ${run.providerName}/${run.modelName}", style = MaterialTheme.typography.bodySmall)
                Text("Prompt version: ${run.promptVersion} · status=${run.status}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MappingCard(mappingItems: List<MappingItem>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Response -> DB Mapping", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            mappingItems.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${item.responseField} -> ${item.tableName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(item.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SourcesCard(detail: DocumentDetail) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("document_sources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            detail.sources.forEach { source ->
                Text(
                    "sourceId=${source.sourceId} · order=${source.sourceOrder} · ${source.fileName} · ${source.mimeType} · ${source.byteSize} bytes",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SavedTablesCard(detail: DocumentDetail) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Saved DB Rows", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            TableSection(
                tableName = "documents",
                summary = "Logical document row plus latest extraction state.",
                body = listOf(
                    "title=${detail.document.title}",
                    "sourceCount=${detail.document.sourceCount}",
                    "extractionStatus=${detail.document.extractionStatus}",
                    "latestRunId=${detail.document.latestRunId}",
                    "occurredAt=${detail.document.occurredAt}",
                ),
            )
            detail.latestRun?.let { run ->
                TableSection(
                    tableName = "extraction_runs",
                    summary = "Cloud run metadata and debug artifacts.",
                    body = listOf(
                        "providerName=${run.providerName}",
                        "modelName=${run.modelName}",
                        "promptVersion=${run.promptVersion}",
                        "status=${run.status}",
                        "requestPrompt=${if (run.requestPrompt.isNullOrBlank()) "not stored" else "stored"}",
                        "responseEnvelopeJson=${if (run.responseEnvelopeJson.isNullOrBlank()) "not stored" else "stored"}",
                        "responseOutputJson=${if (run.responseOutputJson.isNullOrBlank()) "not stored" else "stored"}",
                        "normalizationDebugJson=${if (run.normalizationDebugJson.isNullOrBlank()) "not stored" else "stored"}",
                    ),
                )
            }
            TableSection(
                tableName = "document_facts",
                summary = "Each fact row that survived normalization and was saved.",
                body = if (detail.facts.isEmpty()) {
                    listOf("No saved fact rows.")
                } else {
                    detail.facts.map { fact ->
                        "${fact.factType} value=${fact.valueText} normalized=${fact.normalizedValue} sourceId=${fact.sourceId} sourcePage=${fact.sourcePage}"
                    }
                },
            )
            TableSection(
                tableName = "maintenance_events",
                summary = "Derived event row used for timeline-style display.",
                body = detail.event?.let { event ->
                    listOf(
                        "eventDate=${event.eventDate}",
                        "mileage=${event.mileage}",
                        "shopName=${event.shopName}",
                        "totalCostMinor=${event.totalCostMinor}",
                        "currencyCode=${event.currencyCode}",
                        "status=${event.status}",
                    )
                } ?: listOf("No maintenance event row was saved."),
            )
            TableSection(
                tableName = "service_items",
                summary = "Itemized maintenance work linked to the saved event.",
                body = if (detail.serviceItems.isEmpty()) {
                    listOf("No service item rows were saved.")
                } else {
                    detail.serviceItems.map { item ->
                        "${item.category} label=${item.label} costMinor=${item.costMinor} sourceId=${item.sourceId} sourcePage=${item.sourcePage}"
                    }
                },
            )
        }
    }
}

@Composable
private fun TableSection(
    tableName: String,
    summary: String,
    body: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(tableName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(summary, style = MaterialTheme.typography.bodySmall)
        body.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun DebugArtifactsCard(detail: DocumentDetail) {
    val run = detail.latestRun
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Debug Artifacts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ExpandableArtifact(
                title = "Prompt sent to AI",
                content = run?.requestPrompt ?: "No prompt was stored for this run.",
            )
            ExpandableArtifact(
                title = "AI raw response",
                content = prettyJson(run?.responseEnvelopeJson),
            )
            ExpandableArtifact(
                title = "Structured output JSON",
                content = prettyJson(run?.responseOutputJson),
            )
            ExpandableArtifact(
                title = "Verification / normalization report",
                content = prettyJson(run?.normalizationDebugJson),
            )
        }
    }
}

@Composable
private fun ExpandableArtifact(
    title: String,
    content: String,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(if (expanded) "Hide" else "Show", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun buildMappingItems(detail: DocumentDetail): List<MappingItem> {
    val items = mutableListOf(
        MappingItem("response.title", "documents.title", "Trimmed title is stored on the logical document row."),
        MappingItem("response.facts[*]", "document_facts", "Each saved fact becomes one row with normalized value, evidence, source_id, and source_page."),
        MappingItem("response.maintenance_event", "maintenance_events", "A normalized event row is created when at least one usable event field survives validation."),
        MappingItem("response.maintenance_event.event_date", "documents.occurredAt", "The saved event date is mirrored onto the document row for sorting and summaries."),
        MappingItem("response.service_items[*]", "service_items", "Each saved service item becomes one row linked to the derived event."),
        MappingItem("request prompt / raw AI response / normalization report", "extraction_runs", "Debug artifacts are stored on the latest extraction run for review."),
    )
    if (detail.event == null) {
        items += MappingItem("response.maintenance_event", "not saved", "This response did not produce a saved maintenance_events row after normalization.")
    }
    if (detail.serviceItems.isEmpty()) {
        items += MappingItem("response.service_items[*]", "not saved", "This response did not produce any saved service_items rows.")
    }
    return items
}

private fun prettyJson(raw: String?): String {
    if (raw.isNullOrBlank()) return "No data stored."
    return runCatching {
        PrettyJson.encodeToString(
            JsonElement.serializer(),
            PrettyJson.parseToJsonElement(raw),
        )
    }.getOrElse { raw }
}

private data class MappingItem(
    val responseField: String,
    val tableName: String,
    val detail: String,
)

private val PrettyJson = Json { prettyPrint = true }

class DocumentDebugViewModel(
    application: Application,
    documentId: String,
) : AndroidViewModel(application) {
    private val repository = (application as OrcaApplication).graph.repository
    val detail: Flow<DocumentDetail?> = repository.observeDocumentDetail(documentId)

    class Factory(
        private val application: OrcaApplication,
        private val documentId: String,
    ) : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DocumentDebugViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DocumentDebugViewModel(application, documentId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
