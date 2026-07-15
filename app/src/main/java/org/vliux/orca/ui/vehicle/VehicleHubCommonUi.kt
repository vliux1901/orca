package org.vliux.orca.ui.vehicle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vliux.orca.R
import org.vliux.orca.data.model.VehicleEntity
import org.vliux.orca.ui.theme.StatusErrorDark
import org.vliux.orca.ui.theme.StatusErrorLight
import org.vliux.orca.ui.theme.StatusWarningDark
import org.vliux.orca.ui.theme.StatusWarningLight

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
                    contentDescription = stringResource(R.string.back),
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
            text = { Text(stringResource(R.string.settings)) },
            onClick = { onExpandedChange(false) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.options)) },
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
    colors: androidx.compose.material3.CardColors = vehicleCardColors(),
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = colors,
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
    colors: androidx.compose.material3.CardColors = vehicleCardColors(),
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = colors,
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
internal fun ImportDocumentGuidanceDialog(
    onDismiss: () -> Unit,
    onChooseFiles: () -> Unit,
    onChooseScan: () -> Unit,
) {
    val message = buildImportDocumentGuidanceMessage(
        prefix = stringResource(R.string.import_document_guidance_message_prefix),
        emphasis = stringResource(R.string.import_document_guidance_message_emphasis),
        suffix = stringResource(R.string.import_document_guidance_message_suffix),
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.import_document_guidance_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.import_document_method_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChooseScan() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.import_document_scan_paper),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.import_document_scan_paper_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChooseFiles() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.import_document_choose_files),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.import_document_choose_files_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Box(modifier = Modifier.weight(1f))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

internal fun buildImportDocumentGuidanceMessage(
    prefix: String,
    emphasis: String,
    suffix: String,
): AnnotatedString = AnnotatedString.Builder().apply {
    append(prefix)
    append(" ")
    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
        append(emphasis)
    }
    append(suffix)
}.toAnnotatedString()

@Composable
internal fun vehicleCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
)

internal enum class ExtractionStatusTone {
    Default,
    Warning,
    Error,
}

internal fun extractionStatusTone(status: String?): ExtractionStatusTone = when (status?.trim()?.uppercase()) {
    "NOT_STARTED" -> ExtractionStatusTone.Warning
    "FAILED" -> ExtractionStatusTone.Error
    else -> ExtractionStatusTone.Default
}

@Composable
internal fun extractionCardColors(status: String?): androidx.compose.material3.CardColors {
    val useDarkTone = isSystemInDarkTheme()
    return CardDefaults.cardColors(
        containerColor = when (extractionStatusTone(status)) {
            ExtractionStatusTone.Warning -> if (useDarkTone) StatusWarningDark else StatusWarningLight
            ExtractionStatusTone.Error -> if (useDarkTone) StatusErrorDark else StatusErrorLight
            ExtractionStatusTone.Default -> MaterialTheme.colorScheme.surfaceVariant
        },
    )
}

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
internal fun VehicleProfileCard(
    vehicle: VehicleEntity,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    imageSize: Dp = 80.dp,
    imageTestTag: String? = null,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondary,
    ),
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondary,
    actionContent: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = colors,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = vehicle.displayName,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Text(
                    text = vehicleYearMakeModelText(
                        year = vehicle.year,
                        make = vehicle.make,
                        model = vehicle.model,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    VehicleProfileChip(
                        contentColor = contentColor,
                        label = vehicle.currentMileage?.let {
                            stringResource(R.string.vehicle_mileage_mi, it)
                        } ?: stringResource(R.string.vehicle_mileage_unknown),
                    )
                    vehicle.licensePlate?.takeIf { it.isNotBlank() }?.let { plate ->
                        VehicleProfileChip(
                            contentColor = contentColor,
                            label = stringResource(R.string.vehicle_plate, plate),
                        )
                    }
                    vehicle.color?.takeIf { it.isNotBlank() }?.let { color ->
                        VehicleProfileChip(
                            contentColor = contentColor,
                            label = stringResource(R.string.vehicle_color_chip, color),
                        )
                    }
                    vehicle.vin?.takeIf { it.isNotBlank() }?.let { vin ->
                        VehicleProfileChip(
                            contentColor = contentColor,
                            label = stringResource(R.string.vehicle_vin_suffix, vin.takeLast(6)),
                        )
                    }
                }
                actionContent?.invoke()
            }
            VehicleArtwork(
                photoUri = vehicle.photoUri,
                contentDescription = stringResource(R.string.vehicle_icon_content_description),
                modifier = Modifier.size(imageSize),
                testTag = imageTestTag,
            )
        }
    }
}

internal fun vehicleYearMakeModelText(
    year: Int,
    make: String,
    model: String,
): String = listOfNotNull(
    year.takeIf { it > 0 }?.toString(),
    make.takeIf { it.isNotBlank() },
    model.takeIf { it.isNotBlank() },
).joinToString(" ")

@Composable
internal fun VehicleArtwork(
    photoUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val context = LocalContext.current
    val imageBitmap = rememberVehiclePhotoBitmap(
        context = context,
        photoUri = photoUri,
        maxDimension = 512,
    )
    val imageModifier = (
        if (testTag != null) {
            modifier.testTag(testTag)
        } else {
            modifier
        }
        ).semantics {
            set(VehicleArtworkHasPhotoKey, imageBitmap != null)
        }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            modifier = imageModifier.clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Image(
            painter = painterResource(R.drawable.ic_vehicle_car),
            contentDescription = contentDescription,
            modifier = imageModifier.alpha(0.6f),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun VehicleProfileChip(
    contentColor: androidx.compose.ui.graphics.Color,
    label: String,
) {
    Surface(
        color = contentColor.copy(alpha = 0.12f),
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun rememberVehiclePhotoBitmap(
    context: Context,
    photoUri: String?,
    maxDimension: Int,
) = produceState<androidx.compose.ui.graphics.ImageBitmap?>(
    initialValue = null,
    key1 = photoUri,
    key2 = maxDimension,
) {
    value = withContext(Dispatchers.IO) {
        photoUri
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?.let { decodeScaledBitmap(context, it, maxDimension) }
            ?.asImageBitmap()
    }
}.value

private fun decodeScaledBitmap(
    context: Context,
    uri: Uri,
    maxDimension: Int,
): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            context.contentResolver.loadThumbnail(uri, Size(maxDimension, maxDimension), null)
        }.getOrNull()?.let { return it }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                if (width > 0 && height > 0) {
                    val scale = maxOf(width, height).toFloat() / maxDimension.toFloat()
                    if (scale > 1f) {
                        decoder.setTargetSize(
                            (width / scale).toInt().coerceAtLeast(1),
                            (height / scale).toInt().coerceAtLeast(1),
                        )
                    }
                }
            }
        }.getOrNull()?.let { return it }
    }

    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    } ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return null
    }

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }.getOrNull()
}

internal val VehicleArtworkHasPhotoKey = SemanticsPropertyKey<Boolean>("VehicleArtworkHasPhoto")

private fun calculateInSampleSize(
    width: Int,
    height: Int,
    maxDimension: Int,
): Int {
    var sampleSize = 1
    var currentWidth = width
    var currentHeight = height
    while (currentWidth > maxDimension || currentHeight > maxDimension) {
        currentWidth /= 2
        currentHeight /= 2
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
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
