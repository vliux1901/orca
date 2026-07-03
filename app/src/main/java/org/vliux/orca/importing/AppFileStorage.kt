package org.vliux.orca.importing

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
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
    private val filesRoot: File,
    private val openInputStream: (Uri) -> InputStream?,
) {
    constructor(appContext: Context) : this(
        filesRoot = appContext.filesDir,
        openInputStream = appContext.contentResolver::openInputStream,
    )

    fun copySource(
        source: DocumentSourceEntity,
        import: SourceImportRequest,
    ): StoredSource = storeSource(
        source = source,
        mimeType = import.mimeType,
    ) {
        openInputStream(import.uri)
    }

    internal fun storeSource(
        source: DocumentSourceEntity,
        mimeType: String?,
        openStream: () -> InputStream?,
    ): StoredSource {
        val targetFile = File(filesRoot, source.relativePath)
        targetFile.parentFile?.mkdirs()

        try {
            openStream()?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to open source stream.")
        } catch (t: Throwable) {
            deleteFileAndEmptyParents(targetFile)
            throw t
        }

        val digest = sha256(targetFile)
        val updatedSource = source.copy(
            byteSize = targetFile.length(),
            checksumSha256 = digest,
            mimeType = mimeType ?: source.mimeType,
        )
        return StoredSource(updatedSource, targetFile)
    }

    fun resolveFile(relativePath: String): File = File(filesRoot, relativePath)

    fun deleteRelativePath(relativePath: String) {
        deleteFileAndEmptyParents(resolveFile(relativePath))
    }

    fun deleteRelativePaths(relativePaths: Collection<String>) {
        relativePaths.forEach(::deleteRelativePath)
    }

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

    private fun deleteFileAndEmptyParents(file: File) {
        if (file.exists()) {
            file.delete()
        }

        var current = file.parentFile
        while (current != null && current != filesRoot && current.exists() && current.list()?.isEmpty() == true) {
            current.delete()
            current = current.parentFile
        }
    }
}
