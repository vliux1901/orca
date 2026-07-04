package org.vliux.orca.extraction

import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.vliux.orca.logging.OrcaLog

class OpenAiCloudExtractionAdapter(
    private val config: CloudExtractionConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json {
        explicitNulls = false
        ignoreUnknownKeys = true
    },
) : CloudExtractionAdapter {
    override suspend fun extract(request: ExtractionRequest): ProviderExtractionPayload =
        withContext(Dispatchers.IO) {
            check(config.isConfigured()) {
                "OpenAI extraction is not configured. Set ORCA_CLOUD_EXTRACTION_API_KEY."
            }

            val promptText = buildPrompt(request)
            val body = buildRequestBody(request, promptText)
            OrcaLog.info(
                "OpenAI request prepared for document=${request.document.documentId.takeLast(8)} model=${config.modelName} sources=${request.sources.size} bytes=${request.sources.sumOf { it.file.length() }}.",
            )

            val httpRequest = Request.Builder()
                .url(config.responsesUrl)
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Content-Type", "application/json")
                .build()

            try {
                client.newCall(httpRequest).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    OrcaLog.info(
                        "OpenAI HTTP response for document=${request.document.documentId.takeLast(8)} code=${response.code} bodyLength=${responseBody.length}.",
                    )
                    if (!response.isSuccessful) {
                        throw providerHttpFailure(response.code, responseBody)
                    }

                    val outputText = extractOutputText(responseBody)
                        ?: throw MalformedProviderPayloadException("OpenAI response did not contain output_text.")
                    OrcaLog.info(
                        "OpenAI output_text extracted for document=${request.document.documentId.takeLast(8)} length=${outputText.length}.",
                    )

                    ProviderExtractionPayload(
                        backendType = "CLOUD_VISION",
                        providerName = config.providerName,
                        modelName = config.modelName,
                        rawJson = outputText,
                    )
                }
            } catch (error: ExtractionPipelineException) {
                throw error
            } catch (error: IOException) {
                throw RetryableExtractionException(
                    errorCode = "PROVIDER_IO",
                    message = "OpenAI extraction request failed before a valid response was received.",
                    cause = error,
                )
            }
        }

    internal fun buildRequestBody(
        request: ExtractionRequest,
        promptText: String = buildPrompt(request),
    ): String =
        json.encodeToString(
            OpenAiResponsesRequest.serializer(),
            OpenAiResponsesRequest(
                model = config.modelName,
                input = listOf(
                    OpenAiInputMessage(
                        role = "user",
                        content = buildContent(request, promptText),
                    ),
                ),
                text = OpenAiTextConfig(
                    format = OpenAiResponseFormat(
                        type = "json_schema",
                        name = "maintenance_document_extraction",
                        description = "Structured maintenance document extraction with evidence snippets.",
                        schema = extractionSchema(),
                        strict = true,
                    ),
                ),
            ),
        )

    private fun buildContent(
        request: ExtractionRequest,
        promptText: String,
    ): List<OpenAiInputContent> {
        val parts = mutableListOf<OpenAiInputContent>()
        parts += OpenAiInputContent(
            type = "input_text",
            text = promptText,
        )
        request.sources.forEach { source ->
            parts += when {
                source.source.mimeType.startsWith("image/") -> OpenAiInputContent(
                    type = "input_image",
                    imageUrl = source.file.toDataUrl(source.source.mimeType),
                )

                source.source.mimeType == "application/pdf" -> OpenAiInputContent(
                    type = "input_file",
                    filename = source.source.fileName,
                    fileData = source.file.toDataUrl(source.source.mimeType),
                )

                else -> throw IOException("Unsupported source MIME type: ${source.source.mimeType}")
            }
        }
        return parts
    }

    internal fun buildPrompt(request: ExtractionRequest): String {
        val sourceList = request.sources.joinToString(separator = "\n") { source ->
            "- source_id=${source.source.sourceId}, order=${source.source.sourceOrder}, file_name=${source.source.fileName}, mime_type=${source.source.mimeType}"
        }
        return """
            You extract maintenance information from uploaded images and PDF files.

            Instructions:
            - Treat all uploaded source files as one logical maintenance document.
            - Use source order ascending as the default reading order across files.
            - When the files appear to be out of sequence, use visible page numbers, filenames, and textual continuation cues to infer the most likely reading order before extracting.
            - Before extraction, mentally rotate each page or image upright if needed.
            - Preserve structural meaning.
            - Reconstruct tables conservatively.
            - Do not invent values that are not supported by the document.
            - Return strict JSON only matching the required schema.
            - Keep citations tied to the original source_id even if you infer a different reading order.
            - Each extracted fact should cite the best matching source_id, page number when available, and a short evidence_text snippet.
            - Use these fact_type values only when applicable: SERVICE_DATE, ODOMETER, TOTAL_COST, SHOP_NAME, VIN, LINE_ITEM, NOTE.
            - If a field is not present or is uncertain, return null or omit it where allowed instead of guessing.
            - TOTAL_COST and item prices must be returned as minor currency units when possible.

            Metadata:
            - document_id=${request.document.documentId}
            - vehicle_id=${request.document.vehicleId}
            - prompt_version=${request.promptVersion}

            Source files:
            $sourceList
        """.trimIndent()
    }

    internal fun extractOutputText(responseBody: String): String? {
        val root = json.parseToJsonElement(responseBody).jsonObject
        root["output_text"]?.jsonPrimitive?.contentOrNull?.let { return it }

        val output = root["output"] as? JsonArray ?: return null
        output.forEach { item ->
            val content = item.jsonObject["content"] as? JsonArray ?: return@forEach
            content.forEach { contentItem ->
                val contentObject = contentItem.jsonObject
                if (contentObject["type"]?.jsonPrimitive?.content == "output_text") {
                    contentObject["text"]?.jsonPrimitive?.contentOrNull?.let { return it }
                }
            }
        }
        return null
    }

    private fun extractionSchema(): JsonObject {
        return buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("additionalProperties", JsonPrimitive(false))
            put("required", jsonStringArray("title", "facts", "maintenance_event", "service_items", "warnings"))
            put("properties", buildJsonObject {
                put("title", nullableStringSchema())
                put("facts", buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("items", factSchema())
                })
                put("maintenance_event", buildJsonObject {
                    put("anyOf", JsonArray(listOf(maintenanceEventSchema(), JsonObject(mapOf("type" to JsonPrimitive("null"))))))
                })
                put("service_items", buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("items", serviceItemSchema())
                })
                put("warnings", buildJsonObject {
                    put("type", JsonPrimitive("array"))
                    put("items", buildJsonObject { put("type", JsonPrimitive("string")) })
                })
            })
        }
    }

    private fun providerHttpFailure(
        statusCode: Int,
        responseBody: String,
    ): ExtractionPipelineException {
        val message = "OpenAI extraction failed with HTTP $statusCode."
        return if (statusCode == 408 || statusCode == 409 || statusCode == 429 || statusCode >= 500) {
            RetryableExtractionException(
                errorCode = "PROVIDER_HTTP_$statusCode",
                message = "$message Response body length=${responseBody.length}.",
            )
        } else {
            TerminalExtractionException(
                errorCode = "PROVIDER_HTTP_$statusCode",
                message = "$message Response body length=${responseBody.length}.",
            )
        }
    }

    private fun factSchema(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("additionalProperties", JsonPrimitive(false))
        put("required", jsonStringArray("fact_type", "value_text", "normalized_value", "currency_code", "source_id", "source_page", "evidence_text", "confidence"))
        put("properties", buildJsonObject {
            put("fact_type", buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("enum", jsonStringArray("SERVICE_DATE", "ODOMETER", "TOTAL_COST", "SHOP_NAME", "VIN", "LINE_ITEM", "NOTE"))
            })
            put("value_text", buildJsonObject { put("type", JsonPrimitive("string")) })
            put("normalized_value", nullableStringSchema())
            put("currency_code", nullableStringSchema())
            put("source_id", nullableStringSchema())
            put("source_page", nullableIntegerSchema())
            put("evidence_text", nullableStringSchema())
            put("confidence", nullableNumberSchema())
        })
    }

    private fun maintenanceEventSchema(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("additionalProperties", JsonPrimitive(false))
        put("required", jsonStringArray("event_date", "mileage", "shop_name", "total_cost_minor", "currency_code"))
        put("properties", buildJsonObject {
            put("event_date", nullableStringSchema())
            put("mileage", nullableIntegerSchema())
            put("shop_name", nullableStringSchema())
            put("total_cost_minor", nullableIntegerSchema())
            put("currency_code", nullableStringSchema())
        })
    }

    private fun serviceItemSchema(): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put("additionalProperties", JsonPrimitive(false))
        put("required", jsonStringArray("category", "label", "notes", "cost_minor", "currency_code", "source_id", "source_page"))
        put("properties", buildJsonObject {
            put("category", buildJsonObject { put("type", JsonPrimitive("string")) })
            put("label", buildJsonObject { put("type", JsonPrimitive("string")) })
            put("notes", nullableStringSchema())
            put("cost_minor", nullableIntegerSchema())
            put("currency_code", nullableStringSchema())
            put("source_id", nullableStringSchema())
            put("source_page", nullableIntegerSchema())
        })
    }

    private fun nullableStringSchema(): JsonObject = buildJsonObject {
        put("type", jsonStringArray("string", "null"))
    }

    private fun nullableIntegerSchema(): JsonObject = buildJsonObject {
        put("type", jsonStringArray("integer", "null"))
    }

    private fun nullableNumberSchema(): JsonObject = buildJsonObject {
        put("type", jsonStringArray("number", "null"))
    }

    private fun File.toDataUrl(mimeType: String): String {
        val encoded = Base64.getEncoder().encodeToString(readBytes())
        return "data:$mimeType;base64,$encoded"
    }

    private fun jsonStringArray(vararg values: String): JsonArray =
        buildJsonArray {
            values.forEach { add(JsonPrimitive(it)) }
        }
}

@Serializable
private data class OpenAiResponsesRequest(
    val model: String,
    val input: List<OpenAiInputMessage>,
    val text: OpenAiTextConfig,
)

@Serializable
private data class OpenAiInputMessage(
    val role: String,
    val content: List<OpenAiInputContent>,
)

@Serializable
private data class OpenAiInputContent(
    val type: String,
    val text: String? = null,
    @kotlinx.serialization.SerialName("image_url")
    val imageUrl: String? = null,
    val filename: String? = null,
    @kotlinx.serialization.SerialName("file_data")
    val fileData: String? = null,
)

@Serializable
private data class OpenAiTextConfig(
    val format: OpenAiResponseFormat,
)

@Serializable
private data class OpenAiResponseFormat(
    val type: String,
    val name: String,
    val description: String,
    val schema: JsonObject,
    val strict: Boolean,
)
