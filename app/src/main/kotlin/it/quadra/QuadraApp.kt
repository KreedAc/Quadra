package it.quadra

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            // Al primo avvio popola conti e categorie; poi non fa nulla.
            repository.seedIfEmpty()
        }
    }
}
