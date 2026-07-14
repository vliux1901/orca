package org.vliux.orca.ui.vehicle

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vliux.orca.R
import org.vliux.orca.data.model.DocumentSourceEntity
import org.vliux.orca.importing.AppFileStorage
import java.io.File
import kotlin.math.min

@Composable
internal fun SourcePreviewGrid(
    sources: List<DocumentSourceEntity>,
    appFileStorage: AppFileStorage,
    onOpenSourcePreview: (SourcePreviewRequest) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        sources.chunked(3).forEach { rowSources ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowSources.forEach { source ->
                    Box(modifier = Modifier.weight(1f)) {
                        SourcePreviewTile(
                            source = source,
                            appFileStorage = appFileStorage,
                            previewPage = null,
                            onClick = {
                                onOpenSourcePreview(
                                    SourcePreviewRequest(source = source),
                                )
                            },
                        )
                    }
                }
                repeat(3 - rowSources.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun SourcePreviewTile(
    source: DocumentSourceEntity,
    appFileStorage: AppFileStorage,
    previewPage: Int?,
    onClick: () -> Unit,
) {
    val file = remember(source.relativePath, appFileStorage) { appFileStorage.resolveFile(source.relativePath) }
    val previewBitmap = rememberSourceThumbnailBitmap(
        file = file,
        mimeType = source.mimeType,
        page = previewPage,
        maxDimension = 320,
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap,
                contentDescription = source.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (source.mimeType == "application/pdf") {
                            stringResource(R.string.pdf_label)
                        } else {
                            source.fileName.take(10)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    previewPage?.let { page ->
                        Text(
                            text = stringResource(R.string.page_label, page),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FullScreenSourcePreviewDialog(
    request: SourcePreviewRequest,
    appFileStorage: AppFileStorage,
    onDismiss: () -> Unit,
) {
    val source = request.source
    val file = remember(source.relativePath, appFileStorage) { appFileStorage.resolveFile(source.relativePath) }
    var currentPage by androidx.compose.runtime.saveable.rememberSaveable(source.sourceId, request.focusPage) {
        androidx.compose.runtime.mutableStateOf(request.focusPage?.coerceAtLeast(1) ?: 1)
    }
    val pdfPreview = rememberPdfPagePreview(
        file = file,
        page = currentPage,
        maxDimension = 2048,
    )
    val imageBitmap = rememberDecodedImageBitmap(file = file, maxDimension = 2048)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.ui.graphics.Color.Black,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (source.mimeType == "application/pdf") {
                    if (pdfPreview.bitmap != null) {
                        Image(
                            bitmap = pdfPreview.bitmap,
                            contentDescription = source.fileName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        PreviewUnavailableState()
                    }
                } else {
                    if (imageBitmap != null) {
                        ZoomableFullScreenImage(
                            bitmap = imageBitmap,
                            contentDescription = source.fileName,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        PreviewUnavailableState()
                    }
                }

                PreviewContextOverlay(
                    request = request,
                    pdfPageCount = pdfPreview.pageCount,
                    currentPage = currentPage,
                    onPreviousPage = {
                        currentPage = (currentPage - 1).coerceAtLeast(1)
                    },
                    onNextPage = {
                        currentPage = (currentPage + 1).coerceAtMost(pdfPreview.pageCount)
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp, start = 16.dp, end = 16.dp),
                    onClose = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ZoomableFullScreenImage(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by rememberSaveable(bitmap.width, bitmap.height) { mutableStateOf(1f) }
    var offset by remember(bitmap.width, bitmap.height) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(bitmap.width, bitmap.height, containerSize) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (containerSize == IntSize.Zero) {
                        return@detectTransformGestures
                    }
                    val updatedScale = clampPreviewGestureScale(scale * zoom)
                    val nextMaxOffset = maxPreviewGestureOffset(
                        containerSize = containerSize,
                        bitmapSize = IntSize(bitmap.width, bitmap.height),
                        scale = updatedScale,
                    )
                    val centroidDelta = centroid - Offset(
                        x = containerSize.width / 2f,
                        y = containerSize.height / 2f,
                    )
                    val scaledOffset = (offset + centroidDelta / scale) -
                        (centroidDelta / updatedScale + pan / scale)

                    scale = updatedScale
                    offset = clampPreviewGestureOffset(
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
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offset.x * scale
                    translationY = offset.y * scale
                    scaleX = scale
                    scaleY = scale
                },
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun PreviewUnavailableState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.preview_unavailable),
            style = MaterialTheme.typography.titleMedium,
            color = androidx.compose.ui.graphics.Color.White,
        )
    }
}

@Composable
private fun PreviewContextOverlay(
    request: SourcePreviewRequest,
    pdfPageCount: Int,
    currentPage: Int,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(0xCC101820),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = request.source.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    val contextLabels = buildList {
                        request.focusPage?.let { add(stringResource(R.string.related_page, it)) }
                        if (request.source.mimeType == "application/pdf") {
                            add(stringResource(R.string.viewing_page, currentPage, pdfPageCount))
                        }
                    }
                    if (contextLabels.isNotEmpty()) {
                        Text(
                            text = contextLabels.joinToString(" | "),
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                }
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.close), color = androidx.compose.ui.graphics.Color.White)
                }
            }
            request.focusText?.takeIf { it.isNotBlank() }?.let { focusText ->
                Text(
                    text = stringResource(R.string.related_text, focusText),
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
            if (request.source.mimeType == "application/pdf" && pdfPageCount > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onPreviousPage,
                        enabled = currentPage > 1,
                    ) {
                        Text(stringResource(R.string.prev), color = androidx.compose.ui.graphics.Color.White)
                    }
                    TextButton(
                        onClick = onNextPage,
                        enabled = currentPage < pdfPageCount,
                    ) {
                        Text(stringResource(R.string.next), color = androidx.compose.ui.graphics.Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberDecodedImageBitmap(
    file: File,
    maxDimension: Int,
) = produceState<androidx.compose.ui.graphics.ImageBitmap?>(
    initialValue = null,
    key1 = file.absolutePath,
    key2 = maxDimension,
) {
    value = withContext(Dispatchers.IO) {
        decodeScaledBitmap(file, maxDimension)?.asImageBitmap()
    }
}.value

internal fun previewableImageSources(
    sources: List<DocumentSourceEntity>,
): List<DocumentSourceEntity> = sources.filter { source ->
    source.mimeType.startsWith("image/")
}

private data class PdfPagePreview(
    val bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    val pageCount: Int,
)

internal data class SourcePreviewRequest(
    val source: DocumentSourceEntity,
    val focusPage: Int? = null,
    val focusText: String? = null,
)

@Composable
private fun rememberSourceThumbnailBitmap(
    file: File,
    mimeType: String,
    page: Int?,
    maxDimension: Int,
): androidx.compose.ui.graphics.ImageBitmap? = produceState(
    initialValue = null as androidx.compose.ui.graphics.ImageBitmap?,
    file.absolutePath,
    mimeType,
    page,
    maxDimension,
) {
    value = withContext(Dispatchers.IO) {
        if (mimeType == "application/pdf") {
            decodePdfPageBitmap(file, page ?: 1, maxDimension)?.asImageBitmap()
        } else {
            decodeScaledBitmap(file, maxDimension)?.asImageBitmap()
        }
    }
}.value

@Composable
private fun rememberPdfPagePreview(
    file: File,
    page: Int,
    maxDimension: Int,
) = produceState(
    initialValue = PdfPagePreview(bitmap = null, pageCount = 0),
    key1 = file.absolutePath,
    key2 = page,
    key3 = maxDimension,
) {
    value = withContext(Dispatchers.IO) {
        decodePdfPagePreview(file, page, maxDimension)
    }
}.value

private fun decodeScaledBitmap(
    file: File,
    maxDimension: Int,
): Bitmap? {
    if (!file.exists()) {
        return null
    }

    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return null
    }

    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return runCatching {
        BitmapFactory.decodeFile(file.absolutePath, options)
    }.getOrNull()
}

internal fun clampPreviewGestureScale(scale: Float): Float = scale.coerceIn(1f, 4f)

internal fun maxPreviewGestureOffset(
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

internal fun clampPreviewGestureOffset(
    offset: Offset,
    maxOffset: Offset,
): Offset = Offset(
    x = offset.x.coerceIn(-maxOffset.x, maxOffset.x),
    y = offset.y.coerceIn(-maxOffset.y, maxOffset.y),
)

private fun decodePdfPagePreview(
    file: File,
    page: Int,
    maxDimension: Int,
): PdfPagePreview {
    if (!file.exists()) {
        return PdfPagePreview(bitmap = null, pageCount = 0)
    }
    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) {
                    return@runCatching PdfPagePreview(bitmap = null, pageCount = 0)
                }
                val clampedPage = page.coerceIn(1, renderer.pageCount)
                val bitmap = renderer.openPage(clampedPage - 1).use { pdfPage ->
                    renderPdfPage(pdfPage, maxDimension)?.asImageBitmap()
                }
                PdfPagePreview(bitmap = bitmap, pageCount = renderer.pageCount)
            }
        }
    }.getOrElse {
        PdfPagePreview(bitmap = null, pageCount = 0)
    }
}

private fun decodePdfPageBitmap(
    file: File,
    page: Int,
    maxDimension: Int,
): Bitmap? {
    if (!file.exists()) {
        return null
    }
    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) {
                    return@runCatching null
                }
                val clampedPage = page.coerceIn(1, renderer.pageCount)
                renderer.openPage(clampedPage - 1).use { pdfPage ->
                    renderPdfPage(pdfPage, maxDimension)
                }
            }
        }
    }.getOrNull()
}

private fun renderPdfPage(
    page: PdfRenderer.Page,
    maxDimension: Int,
): Bitmap? {
    if (page.width <= 0 || page.height <= 0) {
        return null
    }
    val scale = (maxDimension.toFloat() / maxOf(page.width, page.height).toFloat()).coerceAtMost(1f)
    val width = (page.width * scale).toInt().coerceAtLeast(1)
    val height = (page.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    }
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
    return sampleSize.coerceAtLeast(1)
}
