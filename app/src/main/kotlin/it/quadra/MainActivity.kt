package it.quadra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalContext
import it.quadra.ui.Root
import it.quadra.ui.theme.QuadraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            QuadraTheme {
                val app = LocalContext.current.applicationContext as QuadraApp
                Root(app.repository)
            }
        }
    }
}
