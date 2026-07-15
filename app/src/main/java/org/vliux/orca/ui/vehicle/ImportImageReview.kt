package org.vliux.orca.ui.vehicle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vliux.orca.R
import org.vliux.orca.importing.SourceImportRequest

internal data class PendingImportSource(
    val selectionIndex: Int,
    val uri: Uri,
    val displayName: String?,
    val mimeType: String?,
    val acquisitionMethod: ImportAcquisitionMethod = ImportAcquisitionMethod.FILES,
    val onConsumed: () -> Unit = {},
)

internal val PendingImportSource.requiresImageReview: Boolean
    get() = requiresImageReview(mimeType, acquisitionMethod)

internal fun PendingImportSource.markConsumed() {
    onConsumed()
}

internal fun requiresImageReview(
    mimeType: String?,
    acquisitionMethod: ImportAcquisitionMethod,
): Boolean = mimeType?.startsWith("image/") == true &&
    acquisitionMethod != ImportAcquisitionMethod.SCAN_DOCUMENT

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
                    Text(
                        text = stringResource(
                            if (reviewableSources.size == 1) {
                                R.string.import_image_review_title_single
                            } else {
                                R.string.import_image_review_title_multiple
                            },
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
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
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (reviewableSources.size > 1) {
                            Text(
                                text = stringResource(
                                    R.string.import_image_review_progress,
                                    currentReviewIndex + 1,
                                    reviewableSources.size,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedContent(
                                targetState = currentReviewIndex,
                                transitionSpec = {
                                    if (targetState > initialState) {
                                        slideInHorizontally(
                                            animationSpec = tween(durationMillis = 280),
                                            initialOffsetX = { width -> width / 3 },
                                        ) + fadeIn(animationSpec = tween(durationMillis = 280)) togetherWith
                                            slideOutHorizontally(
                                                animationSpec = tween(durationMillis = 280),
                                                targetOffsetX = { width -> -width / 3 },
                                            ) + fadeOut(animationSpec = tween(durationMillis = 220))
                                    } else {
                                        slideInHorizontally(
                                            animationSpec = tween(durationMillis = 280),
                                            initialOffsetX = { width -> -width / 3 },
                                        ) + fadeIn(animationSpec = tween(durationMillis = 280)) togetherWith
                                            slideOutHorizontally(
                                                animationSpec = tween(durationMillis = 280),
                                                targetOffsetX = { width -> width / 3 },
                                            ) + fadeOut(animationSpec = tween(durationMillis = 220))
                                    }
                                },
                                label = "import-image-review-transition",
                            ) { targetReviewIndex ->
                                val targetSource = reviewableSources[targetReviewIndex]
                                val targetRotation = rotations[targetSource.selectionIndex]
                                val targetBitmap = rememberImportReviewBitmap(
                                    context = context,
                                    uri = targetSource.uri,
                                )
                                key(targetSource.selectionIndex, targetRotation) {
                                    ZoomableImportImagePreview(
                                        bitmap = targetBitmap,
                                        quarterTurns = targetRotation,
                                    )
                                }
                            }
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
internal fun ZoomableImportImagePreview(
    bitmap: Bitmap?,
    quarterTurns: Int,
    modifier: Modifier = Modifier,
) {
    if (bitmap == null) {
        Column(
            modifier = modifier,
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
        return
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by rememberSaveable { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val normalizedTurns = normalizedQuarterTurns(quarterTurns)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(bitmap, normalizedTurns, containerSize) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (containerSize == IntSize.Zero) {
                        return@detectTransformGestures
                    }
                    val updatedScale = clampImportImageScale(scale * zoom)
                    val rotatedSize = rotatedBitmapSize(bitmap, normalizedTurns)
                    val nextMaxOffset = maxPreviewOffset(
                        containerSize = containerSize,
                        bitmapSize = rotatedSize,
                        scale = updatedScale,
                    )
                    val centroidDelta = centroid - Offset(
                        x = containerSize.width / 2f,
                        y = containerSize.height / 2f,
                    )
                    val scaledOffset = (offset + centroidDelta / scale) -
                        (centroidDelta / updatedScale + pan / scale)

                    scale = updatedScale
                    offset = clampPreviewOffset(
                        offset = scaledOffset,
                        maxOffset = nextMaxOffset,
                    )
                    if (scale <= 1.01f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.import_image_review_preview),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offset.x * scale
                    translationY = offset.y * scale
                    scaleX = scale
                    scaleY = scale
                    rotationZ = normalizedTurns * 90f
                },
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
internal fun rememberImportReviewBitmap(
    context: Context,
    uri: Uri,
) = produceState<Bitmap?>(
    initialValue = null,
    key1 = uri,
) {
    value = withContext(Dispatchers.IO) {
        decodeOriginalBitmap(context, uri)
    }
}.value

internal fun Uri.toPendingImportSource(
    context: Context,
    selectionIndex: Int,
    acquisitionMethod: ImportAcquisitionMethod = ImportAcquisitionMethod.FILES,
    onConsumed: () -> Unit = {},
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
        acquisitionMethod = acquisitionMethod,
        onConsumed = onConsumed,
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
            onConsumed = onConsumed,
        )
    }

    val targetMimeType = transformedImageMimeType(mimeType)
    return SourceImportRequest.fromByteArray(
        displayName = reviewedImageDisplayName(displayName, targetMimeType),
        mimeType = targetMimeType,
        bytesProvider = {
            val bitmap = decodeOriginalBitmap(
                context = context,
                uri = uri,
            ) ?: error("Unable to decode image for review.")
            val rotatedBitmap = rotateBitmap(bitmap, normalizedQuarterTurns)
            bitmapToBytes(rotatedBitmap, targetMimeType)
        },
        onConsumed = onConsumed,
    )
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

internal fun decodeOriginalBitmap(
    context: Context,
    uri: Uri,
): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        }.getOrNull()?.let { return it }
    }

    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        }
    }.getOrNull()
}

internal fun clampImportImageScale(scale: Float): Float = scale.coerceIn(1f, 4f)

private fun rotatedBitmapSize(
    bitmap: Bitmap,
    quarterTurns: Int,
): IntSize = if (quarterTurns % 2 == 0) {
    IntSize(bitmap.width, bitmap.height)
} else {
    IntSize(bitmap.height, bitmap.width)
}

internal fun maxPreviewOffset(
    containerSize: IntSize,
    bitmapSize: IntSize,
    scale: Float,
): Offset {
    if (containerSize == IntSize.Zero || bitmapSize == IntSize.Zero || scale <= 1f) {
        return Offset.Zero
    }
    val fitScale = min(
        containerSize.width.toFloat() / bitmapSize.width.toFloat(),
        containerSize.height.toFloat() / bitmapSize.height.toFloat(),
    )
    val displayedWidth = bitmapSize.width * fitScale * scale
    val displayedHeight = bitmapSize.height * fitScale * scale
    return Offset(
        x = ((displayedWidth - containerSize.width) / 2f / scale).coerceAtLeast(0f),
        y = ((displayedHeight - containerSize.height) / 2f / scale).coerceAtLeast(0f),
    )
}

internal fun clampPreviewOffset(
    offset: Offset,
    maxOffset: Offset,
): Offset = Offset(
    x = offset.x.coerceIn(-maxOffset.x, maxOffset.x),
    y = offset.y.coerceIn(-maxOffset.y, maxOffset.y),
)
