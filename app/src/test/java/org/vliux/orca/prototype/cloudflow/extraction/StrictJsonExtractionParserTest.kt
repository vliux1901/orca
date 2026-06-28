package org.vliux.orca.prototype.cloudflow.extraction

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
                  "source_id": "src-1",
                  "source_page": 1,
                  "evidence_text": "Service Date: 05/01/2026",
                  "confidence": 0.98
                }
              ],
              "maintenance_event": {
                "event_date": "2026-05-01",
                "mileage": 32100
              },
              "warnings": []
            }
            """.trimIndent(),
        )

        assertEquals("Brake service", result.title)
        assertEquals("SERVICE_DATE", result.facts.single().factType)
        assertEquals("Service Date: 05/01/2026", result.facts.single().evidenceText)
        assertEquals("2026-05-01", result.maintenanceEvent?.eventDate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseRejectsUnknownFields() {
        parser.parse(
            """
            {
              "facts": [],
              "warnings": [],
              "unexpected": true
            }
            """.trimIndent(),
        )
    }
}
