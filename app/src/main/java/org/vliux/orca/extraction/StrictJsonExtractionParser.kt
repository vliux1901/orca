package org.vliux.orca.extraction

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class StrictJsonExtractionParser(
    private val json: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        isLenient = false
    },
) {
    fun parse(rawJson: String): AiExtractionResponse {
        return try {
            val root = json.parseToJsonElement(rawJson).jsonObject
            validateRoot(root)
            json.decodeFromString(AiExtractionResponse.serializer(), rawJson)
        } catch (error: SerializationException) {
            throw MalformedProviderPayloadException("Cloud extraction returned malformed JSON.", error)
        } catch (error: IllegalStateException) {
            throw MalformedProviderPayloadException("Cloud extraction returned malformed JSON.", error)
        }
    }

    private fun validateRoot(root: JsonObject) {
        requireExactKeys(
            label = "root object",
            actualKeys = root.keys,
            requiredKeys = setOf("title", "facts", "maintenance_event", "service_items", "warnings"),
        )
        root.getValue("facts").jsonArray.forEachIndexed { index, fact ->
            validateFact(index, fact)
        }
        val maintenanceEvent = root.getValue("maintenance_event")
        if (maintenanceEvent !is JsonNull) {
            validateMaintenanceEvent(maintenanceEvent.jsonObject)
        }
        root.getValue("service_items").jsonArray.forEachIndexed { index, item ->
            validateServiceItem(index, item)
        }
    }

    private fun validateFact(
        index: Int,
        fact: JsonElement,
    ) {
        requireExactKeys(
            label = "facts[$index]",
            actualKeys = fact.jsonObject.keys,
            requiredKeys = setOf(
                "fact_type",
                "value_text",
                "normalized_value",
                "currency_code",
                "source_id",
                "source_page",
                "evidence_text",
                "confidence",
            ),
        )
    }

    private fun validateMaintenanceEvent(event: JsonObject) {
        requireExactKeys(
            label = "maintenance_event",
            actualKeys = event.keys,
            requiredKeys = setOf(
                "event_date",
                "mileage",
                "shop_name",
                "total_cost_minor",
                "currency_code",
            ),
        )
    }

    private fun validateServiceItem(
        index: Int,
        item: JsonElement,
    ) {
        requireExactKeys(
            label = "service_items[$index]",
            actualKeys = item.jsonObject.keys,
            requiredKeys = setOf(
                "category",
                "label",
                "notes",
                "cost_minor",
                "currency_code",
                "source_id",
                "source_page",
            ),
        )
    }

    private fun requireExactKeys(
        label: String,
        actualKeys: Set<String>,
        requiredKeys: Set<String>,
    ) {
        val missing = requiredKeys - actualKeys
        val unexpected = actualKeys - requiredKeys
        if (missing.isEmpty() && unexpected.isEmpty()) {
            return
        }
        throw MalformedProviderPayloadException(
            buildString {
                append("Cloud extraction returned malformed JSON for ")
                append(label)
                append(".")
                if (missing.isNotEmpty()) {
                    append(" Missing keys: ")
                    append(missing.sorted().joinToString())
                    append(".")
                }
                if (unexpected.isNotEmpty()) {
                    append(" Unexpected keys: ")
                    append(unexpected.sorted().joinToString())
                    append(".")
                }
            },
        )
    }
}
