package org.vliux.orca.extraction

import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.vliux.orca.data.model.DocumentEntity
import org.vliux.orca.data.model.DocumentSourceEntity
import org.vliux.orca.data.model.ExtractionRunEntity

data class ExtractionRequest(
    val document: DocumentEntity,
    val sources: List<SourcePayload>,
    val promptVersion: String,
)

data class SourcePayload(
    val source: DocumentSourceEntity,
    val file: File,
)

data class ProviderExtractionPayload(
    val backendType: String,
    val providerName: String,
    val modelName: String,
    val rawJson: String,
)

data class ParsedExtractionPayload(
    val response: AiExtractionResponse,
    val provider: ProviderExtractionPayload,
)

interface CloudExtractionAdapter {
    suspend fun extract(
        request: ExtractionRequest,
    ): ProviderExtractionPayload
}

interface MaintenanceExtractionEngine {
    suspend fun extract(
        request: ExtractionRequest,
    ): ParsedExtractionPayload
}

class DefaultMaintenanceExtractionEngine(
    private val adapter: CloudExtractionAdapter,
    private val parser: StrictJsonExtractionParser,
) : MaintenanceExtractionEngine {
    override suspend fun extract(
        request: ExtractionRequest,
    ): ParsedExtractionPayload {
        val providerPayload = adapter.extract(request)
        return ParsedExtractionPayload(
            response = parser.parse(providerPayload.rawJson),
            provider = providerPayload,
        )
    }
}

data class CloudExtractionConfig(
    val responsesUrl: String,
    val apiKey: String,
    val providerName: String,
    val modelName: String,
) {
    fun isConfigured(): Boolean = apiKey.isNotBlank()
}

@Serializable
data class AiExtractionResponse(
    val title: String? = null,
    val facts: List<AiExtractedFact> = emptyList(),
    @SerialName("maintenance_event")
    val maintenanceEvent: AiMaintenanceEvent? = null,
    @SerialName("service_items")
    val serviceItems: List<AiServiceItem> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable
data class AiExtractedFact(
    @SerialName("fact_type")
    val factType: String,
    @SerialName("value_text")
    val valueText: String,
    @SerialName("normalized_value")
    val normalizedValue: String? = null,
    @SerialName("currency_code")
    val currencyCode: String? = null,
    @SerialName("source_id")
    val sourceId: String? = null,
    @SerialName("source_page")
    val sourcePage: Int? = null,
    @SerialName("evidence_text")
    val evidenceText: String? = null,
    val confidence: Double? = null,
)

@Serializable
data class AiMaintenanceEvent(
    @SerialName("event_date")
    val eventDate: String? = null,
    val mileage: Int? = null,
    @SerialName("shop_name")
    val shopName: String? = null,
    @SerialName("total_cost_minor")
    val totalCostMinor: Long? = null,
    @SerialName("currency_code")
    val currencyCode: String? = null,
)

@Serializable
data class AiServiceItem(
    val category: String,
    val label: String,
    val notes: String? = null,
    @SerialName("cost_minor")
    val costMinor: Long? = null,
    @SerialName("currency_code")
    val currencyCode: String? = null,
    @SerialName("source_id")
    val sourceId: String? = null,
    @SerialName("source_page")
    val sourcePage: Int? = null,
)

fun ExtractionRunEntity.asCompleted(status: String, errorCode: String? = null): ExtractionRunEntity {
    val now = java.time.Instant.now()
    return copy(
        status = status,
        errorCode = errorCode,
        startedAt = startedAt ?: now,
        completedAt = now,
    )
}
