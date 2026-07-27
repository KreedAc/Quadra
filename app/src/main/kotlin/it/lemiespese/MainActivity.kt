package it.lemiespese

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import it.lemiespese.ui.movimenti.MovimentiScreen
import it.lemiespese.ui.movimenti.MovimentiViewModel
import it.lemiespese.ui.theme.LeMieSpeseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LeMieSpeseTheme {
                Radice()
            }
        }
    }
}

@Composable
private fun Radice() {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as LeMieSpeseApp
    val viewModel: MovimentiViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                MovimentiViewModel(app.repository) as T
        }
    )
    MovimentiScreen(viewModel)
}
