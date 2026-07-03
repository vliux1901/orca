package org.vliux.orca.importing

import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vliux.orca.data.model.DocumentSourceEntity

class AppFileStorageTest {
    @Test
    fun storeSourceStoresFileAndChecksum() {
        val rootDir = createTempDirectory(prefix = "orca-storage-").toFile()
        try {
            val storage = AppFileStorage(rootDir) { error("Uri path should not be used in storeSource tests.") }
            val source = sampleSource(relativePath = "vehicles/vehicle-123/documents/doc-1/sources/source-001.png")

            val stored = storage.storeSource(
                source = source,
                mimeType = "image/png",
            ) {
                byteArrayOf(1, 2, 3, 4).inputStream()
            }

            assertTrue(stored.file.exists())
            assertEquals(4L, stored.source.byteSize)
            assertEquals("9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a", stored.source.checksumSha256)
            assertEquals(byteArrayOf(1, 2, 3, 4).toList(), stored.file.readBytes().toList())
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun storeSourceDeletesTargetWhenOpenFails() {
        val rootDir = createTempDirectory(prefix = "orca-storage-").toFile()
        try {
            val storage = AppFileStorage(rootDir) { error("Uri path should not be used in storeSource tests.") }
            val relativePath = "vehicles/vehicle-123/documents/doc-1/sources/source-001.png"

            var failed = false
            try {
                storage.storeSource(
                    source = sampleSource(relativePath = relativePath),
                    mimeType = "image/png",
                ) {
                    null
                }
            } catch (_: Throwable) {
                failed = true
            }

            assertTrue(failed)
            assertFalse(File(rootDir, relativePath).exists())
            assertFalse(File(rootDir, "vehicles/vehicle-123/documents/doc-1/sources").exists())
        } finally {
            rootDir.deleteRecursively()
        }
    }

    private fun sampleSource(relativePath: String): DocumentSourceEntity =
        DocumentSourceEntity(
            sourceId = "source-1",
            documentId = "doc-1",
            sourceOrder = 0,
            fileName = "receipt.png",
            mimeType = "image/png",
            relativePath = relativePath,
            byteSize = 0,
            checksumSha256 = null,
            pageCount = null,
            createdAt = Instant.parse("2026-07-03T00:00:00Z"),
        )
}
