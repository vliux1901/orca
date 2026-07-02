package org.vliux.orca.importing

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import org.vliux.orca.data.model.DocumentSourceEntity

data class SourceImportRequest(
    val uri: Uri,
    val displayName: String?,
    val mimeType: String?,
)

data class StoredSource(
    val source: DocumentSourceEntity,
    val file: File,
)

class AppFileStorage(
    private val appContext: Context,
) {
    fun copySource(
        source: DocumentSourceEntity,
        import: SourceImportRequest,
    ): StoredSource {
        val targetFile = File(appContext.filesDir, source.relativePath)
        targetFile.parentFile?.mkdirs()

        appContext.contentResolver.openInputStream(import.uri)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open source: ${import.uri}")

        val digest = sha256(targetFile)
        val updatedSource = source.copy(
            byteSize = targetFile.length(),
            checksumSha256 = digest,
            mimeType = import.mimeType ?: source.mimeType,
        )
        return StoredSource(updatedSource, targetFile)
    }

    fun resolveFile(relativePath: String): File = File(appContext.filesDir, relativePath)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}
