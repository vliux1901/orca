package org.vliux.orca.extraction

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class StrictJsonExtractionParser(
    private val json: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        isLenient = false
    },
) {
    fun parse(rawJson: String): AiExtractionResponse {
        return try {
            json.decodeFromString(AiExtractionResponse.serializer(), rawJson)
        } catch (error: SerializationException) {
            throw IllegalArgumentException("Cloud extraction returned malformed JSON.", error)
        }
    }
}
