package org.vliux.orca.prototype

import android.net.Uri
import org.vliux.orca.prototype.ocr.OcrResultDraft

data class PrototypeImageJob(
    val id: String,
    val uri: Uri,
    val status: PrototypeStatus = PrototypeStatus.Queued,
    val ocrResult: OcrResultDraft? = null,
    val errorMessage: String? = null,
)

enum class PrototypeStatus {
    Queued,
    OcrRunning,
    OcrReady,
    Extracting,
    Done,
    Failed,
}
