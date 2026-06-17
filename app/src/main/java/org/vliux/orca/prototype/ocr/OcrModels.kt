package org.vliux.orca.prototype.ocr

import android.graphics.Rect

data class OcrResultDraft(
    val fullText: String,
    val blocks: List<OcrBlockDraft>,
    val elapsedMs: Long,
)

data class OcrBlockDraft(
    val text: String,
    val bounds: Rect?,
    val lines: List<OcrLineDraft>,
)

data class OcrLineDraft(
    val text: String,
    val bounds: Rect?,
    val elements: List<OcrElementDraft>,
)

data class OcrElementDraft(
    val text: String,
    val bounds: Rect?,
)
