package it.quadra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import it.quadra.data.LedgerRepository
import it.quadra.ui.categorie.CategorieScreen
import it.quadra.ui.categorie.CategorieViewModel
import it.quadra.ui.conti.ContiScreen
import it.quadra.ui.conti.ContiViewModel
import it.quadra.ui.impostazioni.ImpostazioniScreen
import it.quadra.ui.impostazioni.ImpostazioniViewModel
import it.quadra.ui.inserimento.AggiungiScreen
import it.quadra.ui.movimenti.MovimentiScreen
import it.quadra.ui.movimenti.MovimentiViewModel
import it.quadra.ui.ricorrenti.RicorrentiScreen
import it.quadra.ui.ricorrenti.RicorrentiViewModel
import it.quadra.ui.statistiche.StatisticheScreen
import it.quadra.ui.statistiche.StatisticheViewModel
import it.quadra.ui.theme.extra
import it.quadra.ui.tutorial.Tutorial
import it.quadra.ui.tutorial.TutorialScreen
import kotlinx.coroutines.launch

/**
 * Le quattro destinazioni della barra in basso.
 *
 * Sono quattro e restano quattro: oltre, le etichette si accorciano fino a diventare
 * illeggibili e la barra smette di essere memorizzabile a colpo d'occhio.
 */
enum class Destinazione(val etichetta: String, val icona: ImageVector) {
    MOVIMENTI("Movimenti", Icone.Elenco),
    CONTI("Conti", Icone.Portafoglio),
    STATISTICHE("Statistiche", Icone.Grafico),
    IMPOSTAZIONI("Impostazioni", Icone.Cursori),
}

/** Le schermate che si aprono sopra una scheda invece di sostituirla nella barra. */
private enum class Sotto { CATEGORIE, RICORRENTI, AGGIUNGI }

/**
 * Cosa si sta guardando adesso.
 *
 * La scheda resta anche quando c'è una schermata sopra, ed è quello che permette alla
 * barra in basso di riportare indietro: toccare "Conti" mentre si è dentro le categorie
 * deve andare ai conti, non muovere l'evidenziazione lasciando la stessa pagina.
 */
private data class Vista(val scheda: Destinazione, val sotto: Sotto? = null)

/** Fabbrica minima: evita di ripetere l'oggetto anonimo a ogni ViewModel. */
class Fabbrica<T : ViewModel>(private val costruisci: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <M : ViewModel> create(modelClass: Class<M>): M = costruisci() as M
}

/**
 * L'impalcatura dell'app.
 *
 * La navigazione è tenuta a stato semplice invece che con un grafo: per quattro
 * destinazioni di primo livello e tre schermate che ci si aprono sopra, un grafo
 * aggiunge cerimonia senza aggiungere niente.
 */
@Composable
fun Root(repository: LedgerRepository) {
    var vista by remember { mutableStateOf(Vista(Destinazione.MOVIMENTI)) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Il tutorial al primo avvio, e solo lì.
    //
    // Parte da null e non da "sì": leggere la preferenza costa un giro sul database, e
    // partire dal sì farebbe lampeggiare la sequenza addosso a chi l'ha già vista. Fino
    // alla risposta non si mostra niente, che dura un fotogramma e non si nota.
    val daMostrare by produceState<Boolean?>(initialValue = null, repository) {
        repository.observePreferenza(Tutorial.CHIAVE)
            .collect { value = it != Tutorial.VERSIONE.toString() }
    }
    var riaperto by remember { mutableStateOf(false) }

    val movimentiVM: MovimentiViewModel = viewModel(factory = Fabbrica { MovimentiViewModel(repository) })
    val contiVM: ContiViewModel = viewModel(factory = Fabbrica { ContiViewModel(repository) })
    val statisticheVM: StatisticheViewModel = viewModel(factory = Fabbrica { StatisticheViewModel(repository) })
    val categorieVM: CategorieViewModel = viewModel(factory = Fabbrica { CategorieViewModel(repository) })
    val impostazioniVM: ImpostazioniViewModel = viewModel(factory = Fabbrica { ImpostazioniViewModel(repository) })
    val ricorrentiVM: RicorrentiViewModel = viewModel(factory = Fabbrica { RicorrentiViewModel(repository) })

    // Il tasto indietro del telefono chiude quello che è aperto, come la freccia in
    // alto. Senza, l'unico gesto che tutti gli utenti Android conoscono uscirebbe
    // dall'app da dentro una sottoschermata.
    // L'inserimento si gestisce l'indietro da sé, perché lì un passo indietro torna
    // alla griglia invece di chiudere tutto.
    BackHandler(enabled = vista.sotto != null && vista.sotto != Sotto.AGGIUNGI) {
        vista = vista.copy(sotto = null)
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                // L'inserimento occupa tutto lo schermo: la barra sotto offrirebbe una via
                // di fuga che perde quello che si sta scrivendo.
                if (vista.sotto != Sotto.AGGIUNGI) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        Destinazione.entries.forEach { voce ->
                            NavigationBarItem(
                                selected = vista.scheda == voce && vista.sotto == null,
                                // Torna sempre al primo livello: è il comportamento che ci
                                // si aspetta da una barra di navigazione, e senza questo
                                // toccarla da dentro le categorie non cambiava schermata.
                                onClick = { vista = Vista(voce) },
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
                }
            },
            floatingActionButton = {
                // Il pulsante compare solo dove ha senso: registrare una spesa dalla
                // schermata delle impostazioni non vuol dire niente. Compare crescendo
                // invece di apparire, così cambiando scheda si vede che è arrivato.
                AnimatedVisibility(
                    visible = vista == Vista(Destinazione.MOVIMENTI),
                    enter = scaleIn(tween(180)) + fadeIn(tween(180)),
                    exit = scaleOut(tween(140)) + fadeOut(tween(140)),
                ) {
                    val forma = RoundedCornerShape(19.dp)
                    FloatingActionButton(
                        onClick = { vista = vista.copy(sotto = Sotto.AGGIUNGI) },
                        containerColor = Color.Transparent,
                        contentColor = extra.onBrand,
                        shape = forma,
                        // L'ombra di serie è nera e su fondo scuro non si vede. Questa è
                        // colorata come il pulsante: è quella che lo stacca dal fondo e gli
                        // dà l'aria di essere acceso invece che incollato.
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                        modifier = Modifier
                            .shadow(
                                elevation = 20.dp,
                                shape = forma,
                                ambientColor = extra.brandStart,
                                spotColor = extra.brandStart,
                            )
                            .background(
                                Brush.linearGradient(listOf(extra.brandStart, extra.brandEnd)),
                                forma,
                            ),
                    ) {
                        Icon(Icone.Piu, contentDescription = "Aggiungi una spesa")
                    }
                }
            },
        ) { insets ->
            AnimatedContent(
                targetState = vista,
                transitionSpec = { transizione(initialState, targetState) },
                label = "vista",
            ) { corrente ->
                val contenuto = Modifier.fillMaxSize().padding(insets)
                when (corrente.sotto) {
                    Sotto.CATEGORIE -> CategorieScreen(
                        viewModel = categorieVM,
                        onIndietro = { vista = vista.copy(sotto = null) },
                        modifier = contenuto,
                    )

                    Sotto.RICORRENTI -> RicorrentiScreen(
                        viewModel = ricorrentiVM,
                        onIndietro = { vista = vista.copy(sotto = null) },
                        modifier = contenuto,
                    )

                    Sotto.AGGIUNGI -> {
                        val stato by movimentiVM.stato.collectAsStateWithLifecycle()
                        AggiungiScreen(
                            categorie = stato.categoriePrincipali,
                            tutteLeCategorie = stato.categorie,
                            conti = stato.conti,
                            onChiudi = { vista = vista.copy(sotto = null) },
                            onPersonalizza = { vista = vista.copy(sotto = Sotto.CATEGORIE) },
                            onSalva = { importo, categoriaId, contoId ->
                                movimentiVM.aggiungi(importo, categoriaId, contoId)
                                vista = vista.copy(sotto = null)
                            },
                            modifier = Modifier.fillMaxSize().padding(insets),
                        )
                    }

                    null -> when (corrente.scheda) {
                        Destinazione.MOVIMENTI -> MovimentiScreen(movimentiVM, snackbar, contenuto)
                        Destinazione.CONTI -> ContiScreen(contiVM, contenuto)
                        Destinazione.STATISTICHE -> StatisticheScreen(statisticheVM, contenuto)
                        Destinazione.IMPOSTAZIONI -> ImpostazioniScreen(
                            viewModel = impostazioniVM,
                            snackbar = snackbar,
                            onApriCategorie = { vista = vista.copy(sotto = Sotto.CATEGORIE) },
                            onApriRicorrenti = { vista = vista.copy(sotto = Sotto.RICORRENTI) },
                            onApriTutorial = { riaperto = true },
                            modifier = contenuto,
                        )
                    }
                }
            }
        }

        // Il tutorial sta sopra tutto, barra di navigazione compresa: è una cosa che
        // si legge, non una scheda in cui si naviga, e lasciare visibile una via di
        // fuga che non funziona sarebbe peggio che non averla.
        if (riaperto || daMostrare == true) {
            TutorialScreen(
                onFine = {
                    riaperto = false
                    scope.launch {
                        repository.salvaPreferenza(Tutorial.CHIAVE, Tutorial.VERSIONE.toString())
                    }
                },
            )
        }
    }
}

/**
 * Come una schermata sostituisce l'altra.
 *
 * Il verso non è decorativo, è l'unica cosa che distingue "sono entrato in qualcosa" da
 * "mi sono spostato di lato": aprire una sottoschermata la fa arrivare da destra, e
 * chiuderla la fa uscire da dove era entrata. Fra le schede il verso segue l'ordine
 * della barra, così la posizione di ciascuna resta coerente col gesto.
 *
 * Duecento millisecondi: abbastanza da vedersi, poco da non pesare su un'app che si apre
 * decine di volte al giorno alla cassa del supermercato.
 */
private fun androidx.compose.animation.AnimatedContentTransitionScope<Vista>.transizione(
    da: Vista,
    a: Vista,
): androidx.compose.animation.ContentTransform {
    val verso = when {
        da.sotto == null && a.sotto != null -> 1
        da.sotto != null && a.sotto == null -> -1
        else -> if (a.scheda.ordinal > da.scheda.ordinal) 1 else -1
    }
    return (slideInHorizontally(tween(200)) { it * verso / 4 } + fadeIn(tween(200))) togetherWith
        (slideOutHorizontally(tween(200)) { -it * verso / 4 } + fadeOut(tween(200)))
}
