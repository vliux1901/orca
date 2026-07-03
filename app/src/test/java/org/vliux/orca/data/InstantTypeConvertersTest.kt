package org.vliux.orca.data

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstantTypeConvertersTest {
    private val converters = InstantTypeConverters()

    @Test
    fun roundTripsIso8601InstantValues() {
        val instant = Instant.parse("2026-06-28T12:00:00Z")

        val encoded = converters.instantToIso8601(instant)
        val decoded = converters.iso8601ToInstant(encoded)

        assertEquals("2026-06-28T12:00:00Z", encoded)
        assertEquals(instant, decoded)
    }

    @Test
    fun roundTripsIso8601LocalDateValues() {
        val localDate = LocalDate.parse("2026-05-01")

        val encoded = converters.localDateToIso8601(localDate)
        val decoded = converters.iso8601ToLocalDate(encoded)

        assertEquals("2026-05-01", encoded)
        assertEquals(localDate, decoded)
    }

    @Test
    fun preservesNulls() {
        assertNull(converters.instantToIso8601(null))
        assertNull(converters.iso8601ToInstant(null))
        assertNull(converters.localDateToIso8601(null))
        assertNull(converters.iso8601ToLocalDate(null))
    }
}
