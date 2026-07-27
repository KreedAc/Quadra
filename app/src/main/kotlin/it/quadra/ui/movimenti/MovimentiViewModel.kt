package it.quadra.ui.movimenti

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.quadra.core.budget.Andamento
import it.quadra.core.ledger.Ledger
import it.quadra.core.model.Account
import it.quadra.core.model.Category
import it.quadra.core.model.Money
import it.quadra.core.model.Transaction
import it.quadra.data.LedgerRepository
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

/** Un giorno nella striscia in alto: c'è sempre, anche quando non è successo niente. */
data class GiornoStriscia(
    val data: LocalDate,
    val speso: Money,
    val oggi: Boolean,
)

data class StatoMovimenti(
    val mese: YearMonth = YearMonth.now(),
    val giornate: List<Giornata> = emptyList(),
    val speso: Money = Money.ZERO,
    val categorie: List<Category> = emptyList(),
    val conti: List<Account> = emptyList(),
    val caricato: Boolean = false,
    val striscia: List<GiornoStriscia> = emptyList(),
    val giornoScelto: LocalDate? = null,
    /** null quando il budget non è stato impostato: la barra semplicemente non c'è. */
    val andamento: Andamento? = null,
) {
    /** Le giornate da mostrare: tutte, oppure solo quella scelta nella striscia. */
    val giornateVisibili: List<Giornata>
        get() = giornoScelto?.let { scelto -> giornate.filter { it.data == scelto } } ?: giornate
    /**
     * Le famiglie da mostrare nella griglia del +.
     *
     * Le entrate non ci sono. Registrare uno stipendio non è dire dove è andato il
     * denaro, ed è arrivato su una carta precisa: si fa dal conto, dove la prima cosa
     * che si sceglie è quella giusta. Nella griglia obbligava a scegliere la categoria
     * prima del conto, cioè al contrario di come lo si pensa.
     */
    val categoriePrincipali: List<Category>
        get() = categorie
            .filter { it.isTopLevel && !it.hidden && !it.isIncome }
            .sortedBy { it.sortOrder }

    fun categoria(id: String): Category? = categorie.firstOrNull { it.id == id }

    fun conto(id: String): Account? = conti.firstOrNull { it.id == id }

    /**
     * L'icona da mostrare per una categoria.
     *
     * Le sottocategorie non hanno un'icona propria: nella griglia di inserimento sono
     * chip di solo testo, quindi non serviva. Ma nella lista dei movimenti l'icona serve
     * eccome, e senza questa risalita una spesa segnata come "Supermercato" comparirebbe
     * con i tre puntini del ripiego invece che col carrello della sua famiglia.
     */
    fun icona(categoriaId: String): String? {
        val categoria = categoria(categoriaId) ?: return null
        return categoria.icon ?: categoria.parentId?.let { categoria(it)?.icon }
    }

    /** Come [icona], ma per il colore. Le sottocategorie lo ereditano già sul record. */
    fun colore(categoriaId: String): Int? = categoria(categoriaId)?.colorArgb

    /**
     * Cosa scrivere sotto al nome di un movimento.
     *
     * Finisce sempre col conto, perché "con cosa ho pagato" è un'informazione che serve
     * su ogni riga e che altrimenti costringerebbe ad aprire il movimento per saperla.
     * Prima del conto va il contesto che manca: la famiglia di appartenenza quando il
     * nome mostrato è già quello di una sottocategoria, la categoria quando il movimento
     * ha una descrizione propria. Ripetere due volte la stessa parola — "Spesa" sopra e
     * "Spesa" sotto — occuperebbe una riga per non dire niente.
     */
    fun sottotitolo(movimento: Transaction): String {
        val categoria = categoria(movimento.categoryId)
        val contesto = if (movimento.description.isNotBlank()) {
            categoria?.name
        } else {
            categoria?.parentId?.let { categoria(it)?.name }
        }
        val conto = conto(movimento.accountId)?.name
        return listOfNotNull(contesto, conto).joinToString(" · ")
    }
}

/** Movimento appena cancellato, in attesa che scada la finestra per annullare. */
data class CancellazioneInCorso(val movimenti: List<Transaction>)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MovimentiViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val mese = MutableStateFlow(YearMonth.now())
    private val giorno = MutableStateFlow<LocalDate?>(null)

    private val _cancellazione = MutableStateFlow<CancellazioneInCorso?>(null)
    val cancellazione: StateFlow<CancellazioneInCorso?> = _cancellazione

    val stato: StateFlow<StatoMovimenti> = combine(
        mese,
        mese.flatMapLatest { repository.observeMonth(it) },
        repository.observeCategories(),
        repository.observeAccounts(),
        combine(giorno, repository.observeBudget()) { g, b -> g to b },
    ) { meseCorrente, movimenti, categorie, conti, (giornoScelto, budget) ->
        val giornate = raggruppaPerGiorno(movimenti)
        val speso = Ledger.totalSpent(movimenti)
        StatoMovimenti(
            mese = meseCorrente,
            giornate = giornate,
            // I trasferimenti non sono spese: Ledger li esclude, qui non si decide nulla.
            speso = speso,
            categorie = categorie,
            conti = conti,
            caricato = true,
            striscia = striscia(meseCorrente, giornate),
            giornoScelto = giornoScelto,
            andamento = Andamento.calcola(speso, budget, meseCorrente),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatoMovimenti())

    fun mesePrecedente() { cambiaMese(mese.value.minusMonths(1)) }
    fun meseSuccessivo() { cambiaMese(mese.value.plusMonths(1)) }

    /** Zero o meno toglie il budget: la barra sparisce invece di restare a zero. */
    fun salvaBudget(budget: Money) {
        viewModelScope.launch { repository.salvaBudget(budget) }
    }

    /**
     * Sceglie un giorno, o torna a vedere tutto il mese passando null.
     * Toccare il giorno già scelto lo deseleziona: il modo per uscire è lo stesso
     * gesto con cui si è entrati, senza dover cercare una X.
     */
    fun scegliGiorno(data: LocalDate?) {
        giorno.value = if (giorno.value == data) null else data
    }

    private fun cambiaMese(nuovo: YearMonth) {
        mese.value = nuovo
        // Un giorno di luglio non ha senso mentre si guarda agosto.
        giorno.value = null
    }

    /**
     * Tutti i giorni del mese, anche quelli vuoti.
     *
     * Mostrarli tutti e non solo quelli con movimenti è il punto: un giorno a zero è
     * un'informazione, e una striscia che cambia lunghezza a ogni spesa non si impara
     * mai a memoria.
     */
    private fun striscia(mese: YearMonth, giornate: List<Giornata>): List<GiornoStriscia> {
        val perData = giornate.associateBy { it.data }
        val oggi = LocalDate.now()
        return (1..mese.lengthOfMonth()).map { numero ->
            val data = mese.atDay(numero)
            GiornoStriscia(
                data = data,
                speso = perData[data]?.totale ?: Money.ZERO,
                oggi = data == oggi,
            )
        }
    }

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
