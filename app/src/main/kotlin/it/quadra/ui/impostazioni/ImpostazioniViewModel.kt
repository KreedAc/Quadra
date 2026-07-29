package it.quadra.ui.impostazioni

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.quadra.core.backup.Backup
import it.quadra.core.backup.EsitoRipristino
import it.quadra.data.LedgerRepository
import it.quadra.ui.theme.Tema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Cosa dire all'utente dopo un'operazione di backup. */
data class Messaggio(val testo: String, val errore: Boolean = false)

class ImpostazioniViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _messaggio = MutableStateFlow<Messaggio?>(null)
    val messaggio: StateFlow<Messaggio?> = _messaggio

    /**
     * Il tema scelto.
     *
     * Chi lo applica è [it.quadra.MainActivity], che legge la stessa preferenza: qui
     * serve soltanto a far vedere quale delle tre voci è accesa.
     */
    val tema: StateFlow<Tema> = repository.observePreferenza(Tema.CHIAVE)
        .map { Tema.da(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Tema.SISTEMA)

    fun impostaTema(scelto: Tema) {
        viewModelScope.launch { repository.salvaPreferenza(Tema.CHIAVE, scelto.name) }
    }

    /** Il nome proposto nel selettore: la data lo rende ordinabile e riconoscibile. */
    fun nomeFileProposto(): String = "quadra-${LocalDate.now()}.json"

    fun esporta(context: Context, destinazione: Uri) {
        viewModelScope.launch {
            val esito = runCatching {
                val contenuto = repository.esporta()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(destinazione)?.use { flusso ->
                        flusso.write(contenuto.toByteArray())
                    } ?: error("destinazione non scrivibile")
                }
            }
            _messaggio.value = if (esito.isSuccess) {
                Messaggio("Backup salvato")
            } else {
                Messaggio("Non sono riuscito a scrivere il file", errore = true)
            }
        }
    }

    fun ripristina(context: Context, origine: Uri) {
        viewModelScope.launch {
            val contenuto = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(origine)?.use {
                        it.readBytes().decodeToString()
                    }
                }
            }.getOrNull()

            if (contenuto == null) {
                _messaggio.value = Messaggio("Non sono riuscito a leggere il file", errore = true)
                return@launch
            }

            _messaggio.value = when (val esito = Backup.leggi(contenuto)) {
                is EsitoRipristino.Riuscito -> {
                    // Il file può essere JSON valido e comunque incoerente — un
                    // movimento che punta a un conto assente, per dire. La scrittura
                    // avviene in transazione, quindi qui o è andata o non è successo
                    // niente: in nessun caso l'archivio resta a metà.
                    runCatching { repository.ripristina(esito.documento) }.fold(
                        onSuccess = {
                            Messaggio("Ripristinati ${esito.documento.movimenti.size} movimenti")
                        },
                        onFailure = {
                            Messaggio("Il backup è incompleto o danneggiato", errore = true)
                        },
                    )
                }

                EsitoRipristino.NonRiconosciuto ->
                    Messaggio("Questo file non è un backup di Quadra", errore = true)

                is EsitoRipristino.FormatoTroppoNuovo ->
                    Messaggio(
                        "Backup creato da una versione più recente di Quadra: aggiorna l'app",
                        errore = true,
                    )
            }
        }
    }

    fun messaggioLetto() { _messaggio.value = null }
}
