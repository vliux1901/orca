package org.vliux.orca.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.vliux.orca.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VehicleTopBar(
    title: String,
    onBack: () -> Unit,
    applyStatusBarPadding: Boolean = true,
    actionContent: @Composable (RowScope.() -> Unit) = {},
) {
    TopAppBar(
        modifier = if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_back_arrow),
                    contentDescription = "Back",
                )
            }
        },
        actions = actionContent,
    )
}

@Composable
internal fun RowScope.OverflowMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
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
            text = { Text("Settings") },
            onClick = { onExpandedChange(false) },
        )
        DropdownMenuItem(
            text = { Text("Options") },
            onClick = { onExpandedChange(false) },
        )
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun MissingEntityCard(
    title: String,
    message: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = vehicleCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun ErrorCard(
    message: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = vehicleCardColors(),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
internal fun InfoCard(
    title: String,
    message: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = vehicleCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun vehicleCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
)

@Composable
internal fun VehicleTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Default,
        ),
    )
}

@Composable
internal fun ExpandableDetailSection(
    title: String,
    collapsedLabel: String,
    expandedLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var isExpanded by rememberSaveable(title) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = vehicleCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(if (isExpanded) expandedLabel else collapsedLabel)
                }
            }
            if (isExpanded) {
                content()
            }
        }
    }
}
