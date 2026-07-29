package it.quadra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import it.quadra.ui.Root
import it.quadra.ui.theme.QuadraTheme
import it.quadra.ui.theme.Tema

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = applicationContext as QuadraApp
        // Letto qui, fuori dalla composizione: è il valore del primo fotogramma, e
        // serve prima che il database abbia avuto il tempo di rispondere.
        val iniziale = app.temaIniziale()
        setContent {
            val flusso = remember { app.repository.observePreferenza(Tema.CHIAVE) }
            val salvato by flusso.collectAsState(initial = iniziale)
            LaunchedEffect(salvato) { app.ricordaTema(salvato) }
            QuadraTheme(Tema.da(salvato)) {
                Root(app.repository)
            }
        }
    }
}
