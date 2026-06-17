package org.vliux.orca.prototype.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class MlKitOcrEngine(
    private val context: Context,
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(uri: Uri): OcrResultDraft {
        val startedAt = System.currentTimeMillis()
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).await()
        return result.toOcrResultDraft(elapsedMs = System.currentTimeMillis() - startedAt)
    }
}

private fun Text.toOcrResultDraft(elapsedMs: Long): OcrResultDraft {
    return OcrResultDraft(
        fullText = text,
        blocks = textBlocks.map { block ->
            OcrBlockDraft(
                text = block.text,
                bounds = block.boundingBox,
                lines = block.lines.map { line ->
                    OcrLineDraft(
                        text = line.text,
                        bounds = line.boundingBox,
                        elements = line.elements.map { element ->
                            OcrElementDraft(
                                text = element.text,
                                bounds = element.boundingBox,
                            )
                        },
                    )
                },
            )
        },
        elapsedMs = elapsedMs,
    )
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        continuation.resume(result)
    }
    addOnFailureListener { exception ->
        continuation.resumeWithException(exception)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
