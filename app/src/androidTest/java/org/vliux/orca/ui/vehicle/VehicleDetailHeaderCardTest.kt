package org.vliux.orca.ui.vehicle

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
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

    @Test
    fun artworkLoadsSelectedPhotoUri() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val imageFile = File(context.cacheDir, "vehicle-artwork-test.png")
        FileOutputStream(imageFile).use { output ->
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.BLUE)
                compress(Bitmap.CompressFormat.PNG, 100, output)
                recycle()
            }
        }
        val testTag = "vehicle-artwork"

        composeRule.setContent {
            MaterialTheme {
                VehicleArtwork(
                    photoUri = Uri.fromFile(imageFile).toString(),
                    contentDescription = "Vehicle photo",
                    modifier = androidx.compose.ui.Modifier,
                    testTag = testTag,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(testTag).fetchSemanticsNode().config[VehicleArtworkHasPhotoKey]
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(testTag).assert(
            SemanticsMatcher.expectValue(VehicleArtworkHasPhotoKey, true),
        )
    }
}
