package it.quadra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import it.quadra.data.LedgerRepository
import it.quadra.ui.conti.ContiScreen
import it.quadra.ui.conti.ContiViewModel
import it.quadra.ui.impostazioni.ImpostazioniScreen
import it.quadra.ui.inserimento.AggiungiSheet
import it.quadra.ui.movimenti.MovimentiScreen
import it.quadra.ui.movimenti.MovimentiViewModel
import it.quadra.ui.statistiche.StatisticheScreen
import it.quadra.ui.statistiche.StatisticheViewModel
import it.quadra.ui.theme.extra

/**
 * Le quattro destinazioni della barra in basso.
 *
 * Sono quattro e restano quattro: oltre, le etichette si accorciano fino a diventare
 * illeggibili e la barra smette di essere memorizzabile a colpo d'occhio.
 */
enum class Destinazione(val etichetta: String, val icona: ImageVector) {
    MOVIMENTI("Movimenti", Icons.AutoMirrored.Rounded.List),
    CONTI("Conti", Icons.Rounded.AccountBalanceWallet),
    STATISTICHE("Statistiche", Icons.Rounded.BarChart),
    IMPOSTAZIONI("Impostazioni", Icons.Rounded.Tune),
}

/** Fabbrica minima: evita di ripetere l'oggetto anonimo a ogni ViewModel. */
class Fabbrica<T : ViewModel>(private val costruisci: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <M : ViewModel> create(modelClass: Class<M>): M = costruisci() as M
}

/**
 * L'impalcatura dell'app.
 *
 * La navigazione fra le quattro schede è tenuta a stato semplice invece che con un
 * grafo di navigazione: per quattro destinazioni di primo livello, sempre presenti e
 * senza parametri, un grafo aggiunge cerimonia senza aggiungere niente.
 */
@Composable
fun Root(repository: LedgerRepository) {
    var destinazione by remember { mutableStateOf(Destinazione.MOVIMENTI) }
    var foglioAperto by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val movimentiVM: MovimentiViewModel = viewModel(factory = Fabbrica { MovimentiViewModel(repository) })
    val contiVM: ContiViewModel = viewModel(factory = Fabbrica { ContiViewModel(repository) })
    val statisticheVM: StatisticheViewModel = viewModel(factory = Fabbrica { StatisticheViewModel(repository) })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                Destinazione.entries.forEach { voce ->
                    NavigationBarItem(
                        selected = destinazione == voce,
                        onClick = { destinazione = voce },
                        icon = { Icon(voce.icona, contentDescription = null) },
                        label = { Text(voce.etichetta, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = extra.brandEnd,
                            selectedTextColor = extra.brandEnd,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            // Il pulsante compare solo dove ha senso: registrare una spesa dalla
            // schermata delle impostazioni non vuol dire niente.
            if (destinazione == Destinazione.MOVIMENTI) {
                FloatingActionButton(
                    onClick = { foglioAperto = true },
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF04121A),
                    shape = RoundedCornerShape(19.dp),
                    modifier = Modifier.background(extra.brand, RoundedCornerShape(19.dp)),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Aggiungi una spesa")
                }
            }
        },
    ) { insets ->
        val contenuto = Modifier.fillMaxSize().padding(insets)
        when (destinazione) {
            Destinazione.MOVIMENTI -> MovimentiScreen(movimentiVM, snackbar, contenuto)
            Destinazione.CONTI -> ContiScreen(contiVM, contenuto)
            Destinazione.STATISTICHE -> StatisticheScreen(statisticheVM, contenuto)
            Destinazione.IMPOSTAZIONI -> ImpostazioniScreen(contenuto)
        }
    }

    if (foglioAperto) {
        val stato by movimentiVM.stato.collectAsStateWithLifecycle()
        AggiungiSheet(
            categorie = stato.categoriePrincipali,
            tutteLeCategorie = stato.categorie,
            conti = stato.conti,
            onChiudi = { foglioAperto = false },
            onSalva = { importo, categoriaId, contoId ->
                movimentiVM.aggiungi(importo, categoriaId, contoId)
                foglioAperto = false
            },
        )
    }
}
