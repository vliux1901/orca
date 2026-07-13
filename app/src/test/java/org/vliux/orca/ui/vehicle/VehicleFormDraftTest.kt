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
            "Display name, make, and model are required.",
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
            color = "  Blue  ",
            photoUri = " content://vehicle/photo/1 ",
        ).validate()

        assertEquals("Family SUV", validation.request?.displayName)
        assertEquals("Toyota", validation.request?.make)
        assertEquals("Highlander", validation.request?.model)
        assertEquals(2020, validation.request?.year)
        assertEquals("ABC1234", validation.request?.licensePlate)
        assertEquals("1ABCDEFG", validation.request?.vin)
        assertEquals("Blue", validation.request?.color)
        assertEquals("content://vehicle/photo/1", validation.request?.photoUri)
        assertEquals(null, validation.request?.currentMileage)
        assertNull(validation.errorMessage)
    }

    @Test
    fun validateRejectsInvalidYear() {
        val invalidYear = VehicleFormDraft(
            displayName = "Family SUV",
            make = "Toyota",
            model = "Highlander",
            year = "twenty",
        ).validate()

        assertEquals("Year must be a number.", invalidYear.errorMessage)
    }

    @Test
    fun validateAllowsBlankYearWhenOptional() {
        val validation = VehicleFormDraft(
            displayName = "Family SUV",
            make = "Toyota",
            model = "Highlander",
            year = "",
        ).validate(requireYear = false)

        assertNull(validation.errorMessage)
        assertEquals(0, validation.request?.year)
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
                color = "Silver",
                photoUri = "content://vehicle/photo/2",
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
        assertEquals("Silver", draft.color)
        assertEquals("content://vehicle/photo/2", draft.photoUri)
        assertEquals(45123, draft.currentMileage)
        assertEquals(45123, draft.validate().request?.currentMileage)
        assertTrue(draft.validate().request != null)
    }

    @Test
    fun fromEntityLeavesYearBlankWhenStoredYearUnknown() {
        val draft = VehicleFormDraft.fromEntity(
            VehicleEntity(
                vehicleId = "vehicle-123",
                displayName = "Family SUV",
                make = "Toyota",
                model = "Highlander",
                year = 0,
                licensePlate = null,
                vin = null,
                color = null,
                photoUri = null,
                currentMileage = null,
                createdAt = Instant.parse("2026-07-03T00:00:00Z"),
                updatedAt = Instant.parse("2026-07-03T00:00:00Z"),
            ),
        )

        assertEquals("", draft.year)
    }
}
