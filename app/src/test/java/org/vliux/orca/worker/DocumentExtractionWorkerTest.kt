package org.vliux.orca.worker

import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentExtractionWorkerTest {
    @Test
    fun buildRequestRequiresConnectedNetworkAndExponentialBackoff() {
        val request = DocumentExtractionWorker.buildRequest("doc-1")

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(60_000L, request.workSpec.backoffDelayDuration)
        assertEquals("doc-1", request.workSpec.input.getString(DocumentExtractionWorker.KEY_DOCUMENT_ID))
    }
}
