package org.vliux.orca.prototype.layout

import android.graphics.Rect

data class NormalizedLine(
    val text: String,
    val bounds: Rect?,
    val sourceBlockIndex: Int,
    val sourceLineIndex: Int,
)

data class NormalizedRow(
    val text: String,
    val bounds: Rect?,
    val lines: List<NormalizedLine>,
)
