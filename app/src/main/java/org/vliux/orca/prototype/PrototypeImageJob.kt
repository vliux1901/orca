package org.vliux.orca.prototype

import android.net.Uri

data class PrototypeImageJob(
    val id: String,
    val uri: Uri,
    val status: PrototypeStatus = PrototypeStatus.Queued,
)

enum class PrototypeStatus {
    Queued,
    OcrRunning,
    Extracting,
    Done,
    Failed,
}
