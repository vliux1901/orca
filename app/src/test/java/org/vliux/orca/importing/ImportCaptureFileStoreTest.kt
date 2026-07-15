package org.vliux.orca.importing

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportCaptureFileStoreTest {
    @Test
    fun deleteCaptureTargetRemovesFileAndEmptyParents() {
        val root = Files.createTempDirectory("import-capture-test").toFile()
        val captureDir = File(root, "captures").apply { mkdirs() }
        val captureFile = File(captureDir, "scan.jpg").apply { writeText("test") }
        val store = ImportCaptureFileStore(
            tempRoot = captureDir,
            createUri = { error("URI creation not needed for this test") },
        )

        assertTrue(captureFile.exists())

        store.deleteCaptureTarget(captureFile)

        assertFalse(captureFile.exists())
        assertFalse(captureDir.exists())
        root.deleteRecursively()
    }
}
