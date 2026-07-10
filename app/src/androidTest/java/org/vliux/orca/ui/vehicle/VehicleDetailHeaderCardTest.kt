package org.vliux.orca.ui.vehicle

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.vliux.orca.data.model.VehicleEntity
import java.time.Instant

class VehicleDetailHeaderCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun headerShowsVehicleSummaryWithoutRedundantLabel() {
        composeRule.setContent {
            MaterialTheme {
                VehicleDetailHeaderCard(
                    vehicle = VehicleEntity(
                        vehicleId = "vehicle-1",
                        displayName = "Accord",
                        make = "Honda",
                        model = "Accord",
                        year = 2019,
                        licensePlate = "853jec",
                        vin = "1HGCV1F30KA123456",
                        color = "Blue",
                        photoUri = null,
                        currentMileage = 42000,
                        createdAt = Instant.parse("2026-07-08T00:00:00Z"),
                        updatedAt = Instant.parse("2026-07-08T00:00:00Z"),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Accord").assertTextContains("Accord")
        composeRule.onNodeWithText("2019 Honda Accord").assertTextContains("2019 Honda Accord")
        composeRule.onAllNodesWithText("Vehicle Profile").assertCountEquals(0)
        composeRule.onAllNodesWithTag(VehicleDetailHeaderImageTestTag).assertCountEquals(1)
    }
}
