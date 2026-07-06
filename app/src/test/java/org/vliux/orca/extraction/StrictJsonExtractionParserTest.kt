package org.vliux.orca.extraction

import org.junit.Assert.assertEquals
import org.junit.Test

class StrictJsonExtractionParserTest {
    private val parser = StrictJsonExtractionParser()

    @Test
    fun parseReadsStructuredJsonWithEvidence() {
        val result = parser.parse(
            """
            {
              "title": "Brake service",
              "facts": [
                {
                  "fact_type": "SERVICE_DATE",
                  "value_text": "2026-05-01",
                  "normalized_value": "2026-05-01",
                  "interpreted_from_fact_type": null,
                  "interpreted_from_value_text": null,
                  "currency_code": null,
                  "source_id": "src-1",
                  "source_page": 1,
                  "evidence_text": "Service Date: 05/01/2026",
                  "confidence": 0.98
                }
              ],
              "maintenance_event": {
                "event_date": "2026-05-01",
                "mileage": 32100,
                "shop_name": null,
                "total_cost_minor": null,
                "currency_code": null
              },
              "service_items": [],
              "warnings": []
            }
            """.trimIndent(),
        )

        assertEquals("Brake service", result.title)
        assertEquals("SERVICE_DATE", result.facts.single().factType)
        assertEquals("Service Date: 05/01/2026", result.facts.single().evidenceText)
        assertEquals(null, result.facts.single().interpretedFromFactType)
        assertEquals("2026-05-01", result.maintenanceEvent?.eventDate)
    }

    @Test(expected = MalformedProviderPayloadException::class)
    fun parseRejectsUnknownFields() {
        parser.parse(
            """
            {
              "title": null,
              "facts": [],
              "maintenance_event": null,
              "service_items": [],
              "warnings": [],
              "unexpected": true
            }
            """.trimIndent(),
        )
    }

    @Test(expected = MalformedProviderPayloadException::class)
    fun parseRejectsPartialFactsMissingRequiredKeys() {
        parser.parse(
            """
            {
              "title": "Brake service",
              "facts": [
                {
                  "fact_type": "SERVICE_DATE",
                  "value_text": "2026-05-01"
                }
              ],
              "maintenance_event": null,
              "service_items": [],
              "warnings": []
            }
            """.trimIndent(),
        )
    }
}
