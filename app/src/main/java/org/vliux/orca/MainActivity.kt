package org.vliux.orca

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.vliux.orca.ui.theme.OrcaTheme
import org.vliux.orca.ui.vehicle.VehicleHubScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as OrcaApplication
        enableEdgeToEdge()
        setContent {
            OrcaTheme {
                VehicleHubScreen(vehicleRepository = app.graph.vehicleRepository)
            }
        }
    }
}
