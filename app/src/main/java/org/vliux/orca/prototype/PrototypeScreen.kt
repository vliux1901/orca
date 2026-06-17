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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.util.UUID
import org.vliux.orca.ui.theme.OrcaTheme

@Composable
fun PrototypeScreen(
    modifier: Modifier = Modifier,
) {
    val imageJobs = remember { mutableStateListOf<PrototypeImageJob>() }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        imageJobs.addAll(
            uris.map { uri ->
                PrototypeImageJob(
                    id = UUID.randomUUID().toString(),
                    uri = uri,
                )
            },
        )
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
            Text(
                text = job.uri.toString(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PrototypeScreenPreview() {
    OrcaTheme {
        PrototypeScreen()
    }
}
