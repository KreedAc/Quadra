package it.quadra.ui.ricorrenti

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import it.quadra.core.input.Digitazione
import it.quadra.core.model.Account
import it.quadra.core.model.Category
import it.quadra.core.model.Money
import it.quadra.core.model.RecurrenceUnit
import it.quadra.core.model.RecurringRule
import it.quadra.core.recurrence.RecurrenceEngine
import it.quadra.data.LedgerRepository
import it.quadra.ui.Icone
import it.quadra.ui.common.Azione
import it.quadra.ui.common.CampoTesto
import it.quadra.ui.common.ChipRow
import it.quadra.ui.common.ImportoGrande
import it.quadra.ui.common.Tastierino
import it.quadra.ui.iconFor
import it.quadra.ui.theme.extra
import it.quadra.ui.theme.tabular
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val formatoData = DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN)

data class StatoRicorrenti(
    val regole: List<RecurringRule> = emptyList(),
    val categorie: List<Category> = emptyList(),
    val conti: List<Account> = emptyList(),
) {
    val spese: List<Category>
        get() = categorie
            .filter { it.isTopLevel && !it.hidden && !it.isIncome }
            .sortedBy { it.sortOrder }

    fun categoria(id: String): Category? = categorie.firstOrNull { it.id == id }
    fun conto(id: String): Account? = conti.firstOrNull { it.id == id }

    /** Quando cade la prossima volta, oggi compreso. Null se la regola è finita o spenta. */
    fun prossima(regola: RecurringRule): LocalDate? =
        RecurrenceEngine.nextOccurrence(regola, LocalDate.now())
}

class RicorrentiViewModel(private val repository: LedgerRepository) : ViewModel() {

    val stato: StateFlow<StatoRicorrenti> = combine(
        repository.observeRecurring(),
        repository.observeCategories(),
        repository.observeAccounts(),
    ) { regole, categorie, conti ->
        StatoRicorrenti(regole.sortedBy { it.description }, categorie, conti)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatoRicorrenti())

    fun salva(regola: RecurringRule) {
        viewModelScope.launch {
            repository.salvaRicorrente(regola)
            // Una regola che parte da una data passata ha già delle scadenze arretrate:
            // generarle subito evita che compaiano al prossimo avvio, quando l'utente
            // non ricorderà più di averla creata.
            repository.materializeRecurring()
        }
    }

    fun rimuovi(regola: RecurringRule) {
        viewModelScope.launch { repository.rimuoviRicorrente(regola) }
    }
}

/**
 * Le spese che si ripetono.
 *
 * Sono la parte grossa e prevedibile del bilancio — affitto, bollette, abbonamenti,
 * rate — e sono anche il motivo per cui si smette di usare i tracker manuali: reinserire
 * ogni mese le stesse otto voci stanca prima di dicembre. Qui si scrivono una volta e
 * l'app le mette da sola alla scadenza.
 */
@Composable
fun RicorrentiScreen(
    viewModel: RicorrentiViewModel,
    onIndietro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stato by viewModel.stato.collectAsStateWithLifecycle()
    var inModifica by remember { mutableStateOf<RecurringRule?>(null) }
    var nuova by remember { mutableStateOf(false) }

    LazyColumn(modifier = modifier.padding(horizontal = 18.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icone.Sinistra,
                    contentDescription = "Indietro",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp).clickable(onClick = onIndietro),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    "Ricorrenti",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = stato.conti.isNotEmpty()) { nuova = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icone.Piu,
                        contentDescription = "Aggiungi una ricorrente",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        if (stato.regole.isEmpty()) {
            item { Vuoto() }
        } else {
            itemsIndexed(stato.regole, key = { _, r -> r.id }) { indice, regola ->
                RigaRegola(
                    regola = regola,
                    categoria = stato.categoria(regola.categoryId),
                    conto = stato.conto(regola.accountId),
                    prossima = stato.prossima(regola),
                    ultima = indice == stato.regole.lastIndex,
                    onClick = { inModifica = regola },
                )
            }
        }

        item { Spacer(Modifier.height(96.dp)) }
    }

    inModifica?.let { regola ->
        FoglioRegola(
            iniziale = regola,
            stato = stato,
            onChiudi = { inModifica = null },
            onSalva = { viewModel.salva(it); inModifica = null },
            onRimuovi = { viewModel.rimuovi(regola); inModifica = null },
        )
    }

    if (nuova) {
        FoglioRegola(
            iniziale = null,
            stato = stato,
            onChiudi = { nuova = false },
            onSalva = { viewModel.salva(it); nuova = false },
            onRimuovi = null,
        )
    }
}

@Composable
private fun RigaRegola(
    regola: RecurringRule,
    categoria: Category?,
    conto: Account?,
    prossima: LocalDate?,
    ultima: Boolean,
    onClick: () -> Unit,
) {
    val colore = categoria?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colore.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    iconFor(categoria?.icon),
                    contentDescription = null,
                    tint = colore,
                    modifier = Modifier.size(19.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    regola.description.ifBlank { categoria?.name.orEmpty() },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        cadenza(regola),
                        prossima?.let { "prossima ${it.format(formatoData)}" },
                        conto?.name,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                regola.amount.abs().format(),
                style = MaterialTheme.typography.titleMedium.tabular,
                color = if (regola.active) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!ultima) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 48.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoglioRegola(
    iniziale: RecurringRule?,
    stato: StatoRicorrenti,
    onChiudi: () -> Unit,
    onSalva: (RecurringRule) -> Unit,
    onRimuovi: (() -> Unit)?,
) {
    var descrizione by remember { mutableStateOf(iniziale?.description.orEmpty()) }
    var digitato by remember { mutableStateOf(Digitazione.da(iniziale?.amount ?: Money.ZERO)) }
    var categoria by remember {
        mutableStateOf(iniziale?.let { stato.categoria(it.categoryId) } ?: stato.spese.firstOrNull())
    }
    var conto by remember {
        mutableStateOf(iniziale?.let { stato.conto(it.accountId) } ?: stato.conti.firstOrNull())
    }
    var cadenza by remember { mutableStateOf(iniziale?.let { it.every to it.unit } ?: (1 to RecurrenceUnit.MONTH)) }
    var giorno by remember {
        mutableStateOf(iniziale?.dayOfMonth ?: iniziale?.startDate?.dayOfMonth ?: LocalDate.now().dayOfMonth)
    }

    ModalBottomSheet(
        onDismissRequest = onChiudi,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (iniziale == null) "Nuova ricorrente" else "Modifica ricorrente",
                style = MaterialTheme.typography.titleMedium,
            )
            CampoTesto(descrizione, "Nome, per esempio Affitto") { descrizione = it }

            ImportoGrande(digitato)
            Tastierino(digitato) { digitato = it }

            Text(
                "Ogni quanto",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // `extra` è un getter @Composable e ChipRow chiama `colore` fuori dalla
            // composizione: il colore va letto qui.
            val marchio = extra.brandEnd
            ChipRow(
                voci = CADENZE,
                scelta = CADENZE.firstOrNull { it.every == cadenza.first && it.unit == cadenza.second },
                etichetta = { it.etichetta },
                colore = { marchio },
                onScelta = { cadenza = it.every to it.unit },
            )

            // Il giorno serve solo a chi si ripete a mesi o ad anni: per una regola
            // settimanale la data di partenza dice già tutto.
            if (cadenza.second == RecurrenceUnit.MONTH || cadenza.second == RecurrenceUnit.YEAR) {
                Text(
                    "Il giorno $giorno del mese",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GrigliaGiorni(giorno) { giorno = it }
                Text(
                    "Nei mesi più corti scala all'ultimo giorno disponibile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                "Categoria",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow(
                voci = stato.spese,
                scelta = categoria,
                etichetta = { it.name },
                colore = { Color(it.colorArgb) },
                onScelta = { categoria = it },
            )

            Text(
                "Da quale conto",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow(
                voci = stato.conti,
                scelta = conto,
                etichetta = { it.name },
                colore = { Color(it.colorArgb) },
                onScelta = { conto = it },
            )

            val pronto = !digitato.importo.isZero && categoria != null && conto != null
            Azione("Salva", extra.brandEnd, pronto) {
                val c = categoria ?: return@Azione
                val a = conto ?: return@Azione
                val mensile = cadenza.second == RecurrenceUnit.MONTH ||
                    cadenza.second == RecurrenceUnit.YEAR
                onSalva(
                    RecurringRule(
                        id = iniziale?.id ?: UUID.randomUUID().toString(),
                        description = descrizione.trim(),
                        // Una ricorrente è una spesa: il segno lo mette l'app.
                        amount = digitato.importo.asExpense(),
                        categoryId = c.id,
                        accountId = a.id,
                        every = cadenza.first,
                        unit = cadenza.second,
                        startDate = iniziale?.startDate ?: partenza(giorno, mensile),
                        dayOfMonth = if (mensile) giorno else null,
                        // Le occorrenze già saltate a mano restano saltate: modificare
                        // l'importo non è chiedere di far rinascere ciò che si è tolto.
                        skippedDates = iniziale?.skippedDates.orEmpty(),
                    )
                )
            }

            onRimuovi?.let {
                Azione("Elimina", MaterialTheme.colorScheme.error, onClick = it)
                Text(
                    "I movimenti già registrati restano dove sono: sono spese avvenute " +
                        "davvero, e i saldi dei conti le comprendono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** I giorni del mese, per scegliere quando cade. */
@Composable
private fun GrigliaGiorni(scelto: Int, onScegli: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.heightIn(max = 200.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        gridItems((1..31).toList(), key = { it }) { numero ->
            val attivo = numero == scelto
            Text(
                numero.toString(),
                style = MaterialTheme.typography.bodyMedium.tabular,
                textAlign = TextAlign.Center,
                color = if (attivo) extra.brandEnd else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (attivo) extra.brandEnd.copy(alpha = 0.20f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onScegli(numero) }
                    .padding(vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun Vuoto() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Nessuna spesa ricorrente",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Affitto, bollette, abbonamenti, rate: scrivili una volta e " +
                "l'app li mette da sola alla scadenza.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private data class Cadenza(val every: Int, val unit: RecurrenceUnit, val etichetta: String)

/**
 * Le cadenze reali di una casa italiana.
 *
 * Sono voci fatte invece di due campi "ogni N" e "unità": scegliere fra sei etichette è
 * un tocco, comporre un numero e un'unità sono tre più il dubbio se il gas bimestrale
 * sia "ogni 2 mesi" o "ogni 60 giorni".
 */
private val CADENZE = listOf(
    Cadenza(1, RecurrenceUnit.MONTH, "Ogni mese"),
    Cadenza(2, RecurrenceUnit.MONTH, "Ogni 2 mesi"),
    Cadenza(3, RecurrenceUnit.MONTH, "Ogni 3 mesi"),
    Cadenza(6, RecurrenceUnit.MONTH, "Ogni 6 mesi"),
    Cadenza(1, RecurrenceUnit.YEAR, "Ogni anno"),
    Cadenza(1, RecurrenceUnit.WEEK, "Ogni settimana"),
)

private fun cadenza(regola: RecurringRule): String =
    CADENZE.firstOrNull { it.every == regola.every && it.unit == regola.unit }?.etichetta
        ?: "Ogni ${regola.every} ${regola.unit.name.lowercase()}"

/**
 * Da quando far partire una regola nuova.
 *
 * Dal prossimo giorno utile e non da oggi: chi crea la ricorrente dell'affitto il 20 non
 * vuole vedersi comparire l'affitto del mese in corso, che ha già pagato.
 */
private fun partenza(giorno: Int, mensile: Boolean): LocalDate {
    val oggi = LocalDate.now()
    if (!mensile) return oggi
    val questoMese = oggi.withDayOfMonth(minOf(giorno, oggi.lengthOfMonth()))
    return if (questoMese.isAfter(oggi)) {
        questoMese
    } else {
        val prossimo = oggi.plusMonths(1)
        prossimo.withDayOfMonth(minOf(giorno, prossimo.lengthOfMonth()))
    }
}
