package org.vliux.orca.ui.vehicle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.vliux.orca.R
import org.vliux.orca.settings.OpenAiApiKeyStore

internal const val SettingsApiKeyFieldTestTag = "settingsApiKeyField"

@Composable
internal fun SettingsScreen(
    openAiApiKeyStore: OpenAiApiKeyStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val savedApiKey by openAiApiKeyStore.observeApiKey().collectAsStateWithLifecycle()
    var draftApiKey by rememberSaveable(savedApiKey) { mutableStateOf(savedApiKey) }
    var saveMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.openai_api_key_saved)
    val clearedMessage = stringResource(R.string.openai_api_key_cleared)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            VehicleTopBar(
                title = stringResource(R.string.settings),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OpenAiApiKeyGuideCard()
            OutlinedTextField(
                value = draftApiKey,
                onValueChange = {
                    draftApiKey = it
                    saveMessage = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsApiKeyFieldTestTag),
                label = { Text(stringResource(R.string.openai_api_key_label)) },
                placeholder = { Text(stringResource(R.string.openai_api_key_placeholder)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            )
            Text(
                text = stringResource(R.string.openai_api_key_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    scope.launch {
                        openAiApiKeyStore.saveApiKey(draftApiKey)
                        saveMessage = if (draftApiKey.trim().isBlank()) {
                            clearedMessage
                        } else {
                            savedMessage
                        }
                    }
                },
                enabled = draftApiKey.trim() != savedApiKey,
            ) {
                Text(stringResource(R.string.save))
            }
            saveMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun OpenAiApiKeyGuideCard() {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = vehicleCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.openai_api_key_guide_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.openai_api_key_guide_step_1_prefix),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.openai_api_key_guide_link_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://platform.openai.com")
                    },
                )
            }
            Text(
                text = stringResource(R.string.openai_api_key_guide_step_2),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.openai_api_key_guide_step_3),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
