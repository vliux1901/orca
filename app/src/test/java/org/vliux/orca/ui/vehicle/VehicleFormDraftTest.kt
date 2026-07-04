package org.vliux.orca.ui.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.vliux.orca.data.model.VehicleEntity
import java.time.Instant

class VehicleFormDraftTest {
    @Test
    fun validateRequiresMvpFields() {
        val validation = VehicleFormDraft().validate()

        assertNull(validation.request)
        assertEquals(
            "Display name, make, model, and year are required.",
            validation.errorMessage,
        )
    }

    @Test
    fun validateParsesTrimmedFieldsAndOptionalNumbers() {
        val validation = VehicleFormDraft(
            displayName = "  Family SUV  ",
            make = " Toyota ",
            model = " Highlander ",
            year = "2020",
            licensePlate = " ABC1234 ",
            vin = " 1ABCDEFG ",
            currentMileage = "45123",
        ).validate()

        assertEquals("Family SUV", validation.request?.displayName)
        assertEquals("Toyota", validation.request?.make)
        assertEquals("Highlander", validation.request?.model)
        assertEquals(2020, validation.request?.year)
        assertEquals("ABC1234", validation.request?.licensePlate)
        assertEquals("1ABCDEFG", validation.request?.vin)
        assertEquals(45123, validation.request?.currentMileage)
        assertNull(validation.errorMessage)
    }

    @Test
    fun validateRejectsInvalidNumbers() {
        val invalidYear = VehicleFormDraft(
            displayName = "Family SUV",
            make = "Toyota",
            model = "Highlander",
            year = "twenty",
        ).validate()
        val invalidMileage = VehicleFormDraft(
            displayName = "Family SUV",
            make = "Toyota",
            model = "Highlander",
            year = "2020",
            currentMileage = "-1",
        ).validate()

        assertEquals("Year must be a number.", invalidYear.errorMessage)
        assertEquals("Current mileage cannot be negative.", invalidMileage.errorMessage)
    }

    @Test
    fun validateTreatsBlankLicensePlateAsMissing() {
        val validation = VehicleFormDraft(
            displayName = "Family SUV",
            make = "Toyota",
            model = "Highlander",
            year = "2020",
            licensePlate = "   ",
        ).validate()

        assertNull(validation.errorMessage)
        assertEquals(null, validation.request?.licensePlate)
    }

    @Test
    fun fromEntitySeedsEditorFields() {
        val draft = VehicleFormDraft.fromEntity(
            VehicleEntity(
                vehicleId = "vehicle-123",
                displayName = "Family SUV",
                make = "Toyota",
                model = "Highlander",
                year = 2020,
                licensePlate = null,
                vin = "VIN1",
                currentMileage = 45123,
                createdAt = Instant.parse("2026-07-03T00:00:00Z"),
                updatedAt = Instant.parse("2026-07-03T00:00:00Z"),
            ),
        )

        assertEquals("Family SUV", draft.displayName)
        assertEquals("Toyota", draft.make)
        assertEquals("Highlander", draft.model)
        assertEquals("2020", draft.year)
        assertEquals("", draft.licensePlate)
        assertEquals("VIN1", draft.vin)
        assertEquals("45123", draft.currentMileage)
        assertTrue(draft.validate().request != null)
    }
}
