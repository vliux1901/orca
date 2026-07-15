package org.vliux.orca.importing

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

class ImportCaptureFileStore(
    private val tempRoot: File,
    private val createUri: (File) -> Uri,
) {
    constructor(appContext: Context) : this(
        tempRoot = File(appContext.cacheDir, "document-import-captures"),
        createUri = { file ->
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file,
            )
        },
    )

    data class CaptureTarget(
        val file: File,
        val uri: Uri,
    )

    fun createCaptureTarget(): CaptureTarget {
        tempRoot.mkdirs()
        val file = File.createTempFile("document-capture-", ".jpg", tempRoot)
        return CaptureTarget(
            file = file,
            uri = createUri(file),
        )
    }

    fun deleteCaptureTarget(file: File) {
        if (file.exists()) {
            file.delete()
        }
        deleteEmptyParents(file.parentFile)
    }

    private fun deleteEmptyParents(directory: File?) {
        var current = directory
        while (current != null && current != tempRoot.parentFile && current.exists() && current.list()?.isEmpty() == true) {
            current.delete()
            current = current.parentFile
        }
    }
}
