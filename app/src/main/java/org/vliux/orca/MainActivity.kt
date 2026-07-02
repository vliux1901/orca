package org.vliux.orca

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.vliux.orca.ui.home.FoundationHomeScreen
import org.vliux.orca.ui.theme.OrcaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OrcaTheme {
                FoundationHomeScreen()
            }
        }
    }
}
