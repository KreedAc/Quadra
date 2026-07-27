package it.lemiespese.ui.movimenti

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.lemiespese.core.ledger.Ledger
import it.lemiespese.core.model.Account
import it.lemiespese.core.model.Category
import it.lemiespese.core.model.Money
import it.lemiespese.core.model.Transaction
import it.lemiespese.data.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Un giorno di movimenti, con il suo totale. */
data class Giornata(
    val data: LocalDate,
    val movimenti: List<Transaction>,
    val totale: Money,
)

data class StatoMovimenti(
    val mese: YearMonth = YearMonth.now(),
    val giornate: List<Giornata> = emptyList(),
    val speso: Money = Money.ZERO,
    val categorie: List<Category> = emptyList(),
    val conti: List<Account> = emptyList(),
    val caricato: Boolean = false,
) {
    val categoriePrincipali: List<Category>
        get() = categorie.filter { it.isTopLevel && !it.hidden }.sortedBy { it.sortOrder }

    fun categoria(id: String): Category? = categorie.firstOrNull { it.id == id }
}

/** Movimento appena cancellato, in attesa che scada la finestra per annullare. */
data class CancellazioneInCorso(val movimenti: List<Transaction>)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MovimentiViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val mese = MutableStateFlow(YearMonth.now())

    private val _cancellazione = MutableStateFlow<CancellazioneInCorso?>(null)
    val cancellazione: StateFlow<CancellazioneInCorso?> = _cancellazione

    val stato: StateFlow<StatoMovimenti> = combine(
        mese,
        mese.flatMapLatest { repository.observeMonth(it) },
        repository.observeCategories(),
        repository.observeAccounts(),
    ) { meseCorrente, movimenti, categorie, conti ->
        StatoMovimenti(
            mese = meseCorrente,
            giornate = raggruppaPerGiorno(movimenti),
            // I trasferimenti non sono spese: Ledger li esclude, qui non si decide nulla.
            speso = Ledger.totalSpent(movimenti),
            categorie = categorie,
            conti = conti,
            caricato = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatoMovimenti())

    fun mesePrecedente() { mese.value = mese.value.minusMonths(1) }
    fun meseSuccessivo() { mese.value = mese.value.plusMonths(1) }

    fun aggiungi(importo: Money, categoriaId: String, contoId: String, descrizione: String = "") {
        viewModelScope.launch {
            repository.add(
                // Chi inserisce dalla griglia delle spese sta registrando un'uscita:
                // il segno lo mette l'app, non l'utente.
                amount = if (categoriaId.startsWith("entrate")) importo.asIncome() else importo.asExpense(),
                categoryId = categoriaId,
                accountId = contoId,
                description = descrizione,
            )
        }
    }

    /**
     * Cancella subito e tiene da parte quel che serve per annullare.
     * Passata la finestra, non resta traccia da nessuna parte.
     */
    fun cancella(movimento: Transaction) {
        viewModelScope.launch {
            val gambe = repository.legsOf(movimento)
            repository.delete(movimento)
            _cancellazione.value = CancellazioneInCorso(gambe)
        }
    }

    fun annullaCancellazione() {
        val inCorso = _cancellazione.value ?: return
        _cancellazione.value = null
        viewModelScope.launch { repository.restore(inCorso.movimenti) }
    }

    fun scartaAnnullamento() { _cancellazione.value = null }

    private fun raggruppaPerGiorno(movimenti: List<Transaction>): List<Giornata> =
        movimenti
            .groupBy { it.date }
            .toSortedMap(compareByDescending { it })
            .map { (data, delGiorno) ->
                Giornata(
                    data = data,
                    movimenti = delGiorno,
                    totale = Ledger.totalSpent(delGiorno),
                )
            }
}
