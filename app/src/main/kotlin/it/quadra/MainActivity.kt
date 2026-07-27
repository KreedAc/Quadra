package it.quadra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import it.quadra.ui.movimenti.MovimentiScreen
import it.quadra.ui.movimenti.MovimentiViewModel
import it.quadra.ui.theme.QuadraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            QuadraTheme {
                Radice()
            }
        }
    }
}

@Composable
private fun Radice() {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as QuadraApp
    val viewModel: MovimentiViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                MovimentiViewModel(app.repository) as T
        }
    )
    MovimentiScreen(viewModel)
}
