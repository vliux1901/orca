package org.vliux.orca.ui.vehicle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vliux.orca.R
import org.vliux.orca.importing.SourceImportRequest

internal data class PendingImportSource(
    val selectionIndex: Int,
    val uri: Uri,
    val displayName: String?,
    val mimeType: String?,
)

internal val PendingImportSource.requiresImageReview: Boolean
    get() = mimeType?.startsWith("image/") == true

@Composable
internal fun ImportImageReviewDialog(
    sources: List<PendingImportSource>,
    onDismiss: () -> Unit,
    onComplete: (List<SourceImportRequest>) -> Unit,
) {
    val context = LocalContext.current
    val reviewableSources = remember(sources) { sources.filter { it.requiresImageReview } }
    val rotations = remember(sources) {
        mutableStateListOf<Int>().apply {
            repeat(sources.size) { add(0) }
        }
    }
    var currentReviewIndex by rememberSaveable(sources) { mutableIntStateOf(0) }
    val currentSource = reviewableSources[currentReviewIndex]
    val currentRotation = rotations[currentSource.selectionIndex]
    val previewBitmap = rememberImportReviewBitmap(
        context = context,
        uri = currentSource.uri,
        maxDimension = 2200,
    )
    val isLastImage = currentReviewIndex == reviewableSources.lastIndex

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
                            text = stringResource(
                                R.string.import_image_review_title,
                                currentReviewIndex + 1,
                                reviewableSources.size,
                            ),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        currentSource.displayName?.takeIf { it.isNotBlank() }?.let { fileName ->
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.import_image_review_message_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.import_image_review_message_body),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (sources.size > reviewableSources.size) {
                            Text(
                                text = stringResource(R.string.import_image_review_mixed_sources_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.large,
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.import_image_review_preview),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = currentRotation * 90f
                                },
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.preview_unavailable),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.import_image_review_preview_unavailable_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            rotations[currentSource.selectionIndex] =
                                normalizedQuarterTurns(currentRotation + 1)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.rotate_image_action))
                    }
                    Button(
                        onClick = {
                            if (isLastImage) {
                                onComplete(
                                    sources.map { source ->
                                        source.toSourceImportRequest(
                                            context = context,
                                            quarterTurns = rotations[source.selectionIndex],
                                        )
                                    },
                                )
                            } else {
                                currentReviewIndex += 1
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (isLastImage) {
                                    R.string.confirm_images_and_start_extraction
                                } else {
                                    R.string.confirm_image_action
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberImportReviewBitmap(
    context: Context,
    uri: Uri,
    maxDimension: Int,
) = produceState<Bitmap?>(
    initialValue = null,
    key1 = uri,
    key2 = maxDimension,
) {
    value = withContext(Dispatchers.IO) {
        decodeScaledBitmap(context, uri, maxDimension)
    }
}.value

internal fun Uri.toPendingImportSource(
    context: Context,
    selectionIndex: Int,
): PendingImportSource {
    val displayName = context.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        }
    return PendingImportSource(
        selectionIndex = selectionIndex,
        uri = this,
        displayName = displayName,
        mimeType = context.contentResolver.getType(this),
    )
}

internal fun PendingImportSource.toSourceImportRequest(
    context: Context,
    quarterTurns: Int = 0,
): SourceImportRequest {
    val normalizedQuarterTurns = normalizedQuarterTurns(quarterTurns)
    if (!requiresImageReview || normalizedQuarterTurns == 0) {
        return SourceImportRequest.fromUri(
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            openInputStream = context.contentResolver::openInputStream,
        )
    }

    val targetMimeType = transformedImageMimeType(mimeType)
    return SourceImportRequest.fromByteArray(
        displayName = reviewedImageDisplayName(displayName, targetMimeType),
        mimeType = targetMimeType,
    ) {
        val bitmap = decodeScaledBitmap(
            context = context,
            uri = uri,
            maxDimension = 4096,
        ) ?: error("Unable to decode image for review.")
        val rotatedBitmap = rotateBitmap(bitmap, normalizedQuarterTurns)
        bitmapToBytes(rotatedBitmap, targetMimeType)
    }
}

internal fun reviewedImageDisplayName(
    displayName: String?,
    mimeType: String,
): String? {
    val suffix = when (mimeType.lowercase()) {
        "image/png" -> "png"
        else -> "jpg"
    }
    val baseName = displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "image"
    return "$baseName.$suffix"
}

internal fun normalizedQuarterTurns(turns: Int): Int = ((turns % 4) + 4) % 4

private fun transformedImageMimeType(mimeType: String?): String = when (mimeType?.lowercase()) {
    "image/png" -> "image/png"
    else -> "image/jpeg"
}

private fun rotateBitmap(
    bitmap: Bitmap,
    quarterTurns: Int,
): Bitmap {
    if (quarterTurns == 0) {
        return bitmap
    }
    val matrix = Matrix().apply {
        postRotate(quarterTurns * 90f)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun bitmapToBytes(
    bitmap: Bitmap,
    mimeType: String,
): ByteArray {
    val format = when (mimeType.lowercase()) {
        "image/png" -> Bitmap.CompressFormat.PNG
        else -> Bitmap.CompressFormat.JPEG
    }
    return ByteArrayOutputStream().use { output ->
        bitmap.compress(format, 95, output)
        output.toByteArray()
    }
}

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
    return sampleSize
}
