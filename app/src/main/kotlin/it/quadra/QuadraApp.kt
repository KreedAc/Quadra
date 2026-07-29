package it.quadra

import android.app.Application
import android.content.Context
import it.quadra.data.LedgerRepository
import it.quadra.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Le dipendenze dell'app, tenute a mano.
 *
 * Niente Hilt per ora: per un'app di questa dimensione un contenitore scritto a mano è
 * cinquanta righe, si legge tutto in un colpo d'occhio e non porta con sé generazione di
 * codice. Se un giorno il progetto cresce, migrare è meccanico.
 */
class QuadraApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: LedgerRepository by lazy { LedgerRepository(AppDatabase.get(this)) }

    /**
     * L'ultimo tema visto, ricopiato dove si può leggere senza aspettare.
     *
     * La preferenza vera sta nel database — deve viaggiare col backup — ma leggerla è
     * asincrono, e il primo fotogramma verrebbe disegnato prima della risposta: chi ha
     * scelto il tema scuro su un telefono in modalità chiara vedrebbe un lampo bianco a
     * ogni avvio. Questa è solo una copia per il primo fotogramma, riallineata a ogni
     * emissione: se il database dice altro, vince il database.
     */
    private val aspetto by lazy { getSharedPreferences("aspetto", Context.MODE_PRIVATE) }

    fun temaIniziale(): String? = aspetto.getString(CHIAVE_TEMA, null)

    fun ricordaTema(valore: String?) {
        aspetto.edit().putString(CHIAVE_TEMA, valore).apply()
    }

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            // Al primo avvio popola conti e categorie; poi non fa nulla.
            repository.seedIfEmpty()
        }
    }

    private companion object {
        const val CHIAVE_TEMA = "tema"
    }
}
