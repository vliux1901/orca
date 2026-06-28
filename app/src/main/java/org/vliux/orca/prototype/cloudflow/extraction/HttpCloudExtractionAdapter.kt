package org.vliux.orca.prototype.cloudflow.extraction

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class HttpCloudExtractionAdapter(
    private val config: CloudExtractionConfig,
    private val client: OkHttpClient = OkHttpClient(),
) : CloudExtractionAdapter {
    override suspend fun extract(request: ExtractionRequest): ProviderExtractionPayload =
        withContext(Dispatchers.IO) {
            check(config.isConfigured()) {
                "Cloud extraction is not configured. Set ORCA_CLOUD_EXTRACTION_URL or orcaCloudExtractionUrl."
            }

            val metadataJson = EXTRACTION_REQUEST_JSON.encodeToString(
                CloudExtractionHttpRequest(
                    documentId = request.document.documentId,
                    vehicleId = request.document.vehicleId,
                    promptVersion = request.promptVersion,
                    providerName = config.providerName,
                    modelName = config.modelName,
                    guidance = DEFAULT_GUIDANCE,
                    sources = request.sources.map { source ->
                        CloudExtractionSourceMetadata(
                            sourceId = source.source.sourceId,
                            sourceOrder = source.source.sourceOrder,
                            fileName = source.source.fileName,
                            mimeType = source.source.mimeType,
                        )
                    },
                ),
            )

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "request_json",
                    null,
                    metadataJson.toRequestBody("application/json".toMediaTypeOrNull()),
                )
                .apply {
                    request.sources.forEach { source ->
                        addFormDataPart(
                            "source_files",
                            source.source.fileName,
                            source.file.asRequestBody(source.source.mimeType.toMediaTypeOrNull()),
                        )
                    }
                }
                .build()

            val httpRequest = Request.Builder()
                .url(config.baseUrl)
                .post(body)
                .apply {
                    if (config.apiKey.isNotBlank()) {
                        header("Authorization", "Bearer ${config.apiKey}")
                    }
                }
                .build()

            client.newCall(httpRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Cloud extraction failed with HTTP ${response.code}")
                }
                val rawJson = response.body?.string()?.takeIf { it.isNotBlank() }
                    ?: throw IOException("Cloud extraction returned an empty body.")
                ProviderExtractionPayload(
                    backendType = "CLOUD_VISION",
                    providerName = config.providerName,
                    modelName = config.modelName,
                    rawJson = rawJson,
                )
            }
        }

    companion object {
        private const val DEFAULT_GUIDANCE =
            "Rotate each image upright if needed before extraction. Preserve structural text, reconstruct tables conservatively, and return strict JSON only."
        private val EXTRACTION_REQUEST_JSON = kotlinx.serialization.json.Json {
            encodeDefaults = true
            explicitNulls = false
        }
    }
}

@kotlinx.serialization.Serializable
private data class CloudExtractionHttpRequest(
    val documentId: String,
    val vehicleId: String,
    val promptVersion: String,
    val providerName: String,
    val modelName: String,
    val guidance: String,
    val sources: List<CloudExtractionSourceMetadata>,
)

@kotlinx.serialization.Serializable
private data class CloudExtractionSourceMetadata(
    val sourceId: String,
    val sourceOrder: Int,
    val fileName: String,
    val mimeType: String,
)
