package it.quadra.ui.conti

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import it.quadra.core.ledger.Ledger
import it.quadra.core.ledger.Totals
import it.quadra.core.input.Digitazione
import it.quadra.core.model.Account
import it.quadra.core.model.Category
import it.quadra.core.model.AccountKind
import it.quadra.core.model.Money
import it.quadra.data.LedgerRepository
import it.quadra.ui.Icone
import it.quadra.ui.common.Azione
import it.quadra.ui.common.CampoTesto
import it.quadra.ui.common.ChipRow
import it.quadra.ui.common.ContenutoFoglio
import it.quadra.ui.common.ImportoGrande
import it.quadra.ui.common.SceltaColore
import it.quadra.ui.common.SceltaIcona
import it.quadra.ui.common.TAVOLOZZA
import it.quadra.ui.common.Tastierino
import it.quadra.ui.iconFor
import it.quadra.ui.theme.extra
import it.quadra.ui.theme.tabular
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class StatoConti(
    val tutti: List<Account> = emptyList(),
    val saldi: Map<String, Money> = emptyMap(),
    val totali: Totals = Totals(Money.ZERO, Money.ZERO),
    val entrate: List<Category> = emptyList(),
) {
    val spendibili: List<Account> get() = tutti.filter { it.includedInTotal }
    val vincolati: List<Account> get() = tutti.filterNot { it.includedInTotal }
    fun saldo(conto: Account): Money = saldi[conto.id] ?: Money.ZERO
}

class ContiViewModel(private val repository: LedgerRepository) : ViewModel() {

    val stato: StateFlow<StatoConti> = combine(
        repository.observeAccounts(),
        repository.observeAllTransactions(),
        repository.observeCategories(),
    ) { conti, movimenti, categorie ->
        StatoConti(
            tutti = conti.filterNot { it.archived }.sortedBy { it.sortOrder },
            saldi = Ledger.balances(conti, movimenti),
            totali = Ledger.totals(conti, movimenti),
            // Le voci di entrata, sia la famiglia sia le sue sottocategorie: qui si
            // registra uno stipendio, e "Entrate" da solo non direbbe da dove viene.
            entrate = categorie
                .filter { it.isIncome && !it.hidden && it.parentId != null }
                .sortedBy { it.sortOrder },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatoConti())

    fun salva(conto: Account) {
        viewModelScope.launch { repository.salvaConto(conto) }
    }

    fun rimuovi(conto: Account) {
        viewModelScope.launch { repository.rimuoviConto(conto) }
    }

    fun nuovo(nome: String, colore: Int, icona: String, vincolato: Boolean, apertura: Money) {
        salva(
            Account(
                id = UUID.randomUUID().toString(),
                name = nome,
                kind = AccountKind.OTHER,
                colorArgb = colore,
                openingBalance = apertura,
                includedInTotal = !vincolato,
                icon = icona,
                sortOrder = stato.value.tutti.size,
            )
        )
    }

    fun trasferisci(da: Account, a: Account, importo: Money) {
        viewModelScope.launch { repository.transfer(da, a, importo) }
    }

    fun allinea(conto: Account, saldoReale: Money) {
        viewModelScope.launch { repository.reconcile(conto, saldoReale) }
    }

    /**
     * Registra un'entrata su questo conto.
     *
     * Sta qui e non nella griglia delle spese perché è un'operazione sul conto: uno
     * stipendio non è una cosa in cui il denaro è "andato", ed è arrivato su una carta
     * precisa. Nella griglia costringeva a scegliere prima la categoria e poi il conto,
     * cioè al contrario di come lo si pensa.
     */
    fun registraEntrata(conto: Account, importo: Money, categoriaId: String) {
        viewModelScope.launch {
            repository.add(
                amount = importo.asIncome(),
                categoryId = categoriaId,
                accountId = conto.id,
            )
        }
    }
}

/**
 * I conti, divisi fra ciò che si può spendere e ciò che è vincolato.
 *
 * La divisione è il punto della schermata. Il denaro a destinazione d'uso — una carta
 * per un sussidio, un fondo accantonato — esiste e va visto, ma sommarlo alla
 * disponibilità fa credere di avere più di quanto si può davvero usare. Qui resta a
 * vista, in un totale suo.
 */
@Composable
fun ContiScreen(viewModel: ContiViewModel, modifier: Modifier = Modifier) {
    val stato by viewModel.stato.collectAsStateWithLifecycle()
    var scelto by remember { mutableStateOf<Account?>(null) }
    var nuovoConto by remember { mutableStateOf(false) }

    LazyColumn(modifier = modifier.padding(horizontal = 18.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Conti", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { nuovoConto = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icone.Piu,
                        contentDescription = "Aggiungi un conto",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        item { SchedaDisponibile(stato) }

        if (stato.spendibili.isNotEmpty()) {
            item { Sezione("Spendibili", stato.totali.available) }
            itemsIndexed(stato.spendibili, key = { _, c -> c.id }) { indice, conto ->
                RigaConto(conto, stato.saldo(conto), indice == stato.spendibili.lastIndex) {
                    scelto = conto
                }
            }
        }

        if (stato.vincolati.isNotEmpty()) {
            item { Sezione("Vincolati", stato.totali.constrained) }
            itemsIndexed(stato.vincolati, key = { _, c -> c.id }) { indice, conto ->
                RigaConto(conto, stato.saldo(conto), indice == stato.vincolati.lastIndex) {
                    scelto = conto
                }
            }
        }

        item { Spacer(Modifier.height(96.dp)) }
    }

    scelto?.let { conto ->
        FoglioConto(
            conto = conto,
            saldo = stato.saldo(conto),
            altriConti = stato.tutti.filter { it.id != conto.id },
            entrate = stato.entrate,
            onChiudi = { scelto = null },
            viewModel = viewModel,
        )
    }

    if (nuovoConto) {
        FoglioNuovoConto(
            onChiudi = { nuovoConto = false },
            onConferma = { nome, colore, icona, vincolato, apertura ->
                viewModel.nuovo(nome, colore, icona, vincolato, apertura)
                nuovoConto = false
            },
        )
    }
}

@Composable
private fun SchedaDisponibile(stato: StatoConti) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .background(
                Brush.linearGradient(
                    listOf(
                        extra.brandStart.copy(alpha = 0.22f),
                        extra.brandEnd.copy(alpha = 0.14f),
                    )
                )
            )
            .padding(20.dp),
    ) {
        Text(
            "Disponibile",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stato.totali.available.format(),
            style = MaterialTheme.typography.displaySmall.tabular,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!stato.totali.constrained.isZero) {
            Spacer(Modifier.height(9.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icone.Lucchetto,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "più ${stato.totali.constrained.format()} vincolati",
                    style = MaterialTheme.typography.bodyMedium.tabular,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Sezione(titolo: String, totale: Money) {
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            titolo.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            totale.format(),
            style = MaterialTheme.typography.bodyMedium.tabular,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RigaConto(conto: Account, saldo: Money, ultimo: Boolean, onClick: () -> Unit) {
    val colore = Color(conto.colorArgb)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colore.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconFor(conto.icon), contentDescription = null, tint = colore, modifier = Modifier.size(19.dp))
        }
        Text(
            conto.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            saldo.format(),
            style = MaterialTheme.typography.titleMedium.tabular,
            color = if (saldo.isZero) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        )
    }
        if (!ultimo) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 50.dp),
            )
        }
    }
}

/** I passi in cui può trovarsi il foglio di un conto. */
private enum class Passo { AZIONI, ENTRATA, TRASFERIMENTO, ALLINEA, MODIFICA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoglioConto(
    conto: Account,
    saldo: Money,
    altriConti: List<Account>,
    entrate: List<Category>,
    onChiudi: () -> Unit,
    viewModel: ContiViewModel,
) {
    var passo by remember { mutableStateOf(Passo.AZIONI) }

    ModalBottomSheet(
        onDismissRequest = onChiudi,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ContenutoFoglio(spazio = 16.dp) {
            Intestazione(conto, saldo)

            when (passo) {
                Passo.AZIONI -> Azioni(
                    trasferibile = altriConti.isNotEmpty(),
                    onEntrata = { passo = Passo.ENTRATA },
                    onTrasferisci = { passo = Passo.TRASFERIMENTO },
                    onAllinea = { passo = Passo.ALLINEA },
                    onModifica = { passo = Passo.MODIFICA },
                )

                Passo.ENTRATA -> PassoEntrata(entrate) { importo, categoriaId ->
                    viewModel.registraEntrata(conto, importo, categoriaId)
                    onChiudi()
                }

                Passo.TRASFERIMENTO -> PassoTrasferimento(conto, altriConti) { destinazione, importo ->
                    viewModel.trasferisci(conto, destinazione, importo)
                    onChiudi()
                }

                Passo.ALLINEA -> PassoAllinea(saldo) { reale ->
                    viewModel.allinea(conto, reale)
                    onChiudi()
                }

                Passo.MODIFICA -> PassoModifica(
                    conto = conto,
                    onSalva = { viewModel.salva(it); onChiudi() },
                    onRimuovi = { viewModel.rimuovi(conto); onChiudi() },
                )
            }
        }
    }
}

@Composable
private fun Intestazione(conto: Account, saldo: Money) {
    val colore = Color(conto.colorArgb)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colore.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(11.dp))
                .background(colore.copy(alpha = 0.30f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconFor(conto.icon), contentDescription = null, tint = colore, modifier = Modifier.size(17.dp))
        }
        Text(
            conto.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            saldo.format(),
            style = MaterialTheme.typography.titleMedium.tabular,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Quattro azioni, in due pesi diversi.
 *
 * Manca "Preleva" perché prelevare al bancomat è un trasferimento verso i contanti: due
 * nomi per la stessa cosa costringono solo a scegliere ogni volta. E manca "Bilancio",
 * sostituito da "Allinea saldo", che invece di riscrivere il numero genera la differenza
 * come movimento.
 *
 * Erano quattro quadretti grigi identici, e quattro cose identiche non sono un menù: non
 * dicono quale si usa tutti i giorni né cosa succede toccandole. Entrata e Trasferisci
 * sono le due che si fanno spesso e stanno in alto, colorate; allineare e modificare
 * capitano una volta ogni tanto e scendono a riga, con scritto cosa fanno — che è
 * l'informazione che serve davvero prima di toccare "Allinea".
 */
@Composable
private fun Azioni(
    trasferibile: Boolean,
    onEntrata: () -> Unit,
    onTrasferisci: () -> Unit,
    onAllinea: () -> Unit,
    onModifica: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        Tessera(
            icona = Icone.Su,
            titolo = "Entrata",
            sottotitolo = "Stipendio, rimborso, regalo",
            colore = extra.income,
            abilitata = true,
            modifier = Modifier.weight(1f),
            onClick = onEntrata,
        )
        Tessera(
            icona = Icone.Scambio,
            titolo = "Trasferisci",
            sottotitolo = if (trasferibile) "Sposta su un altro conto"
            else "Serve almeno un altro conto",
            colore = extra.brandEnd,
            abilitata = trasferibile,
            modifier = Modifier.weight(1f),
            onClick = onTrasferisci,
        )
    }
    RigaAzione(
        icona = Icone.Spunta,
        titolo = "Allinea il saldo",
        sottotitolo = "Se la banca dice un altro numero, la differenza diventa un movimento",
        colore = extra.brandStart,
        onClick = onAllinea,
    )
    RigaAzione(
        icona = Icone.Matita,
        titolo = "Modifica il conto",
        sottotitolo = "Nome, colore, icona — oppure eliminalo",
        colore = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onModifica,
    )
}

@Composable
private fun Tessera(
    icona: androidx.compose.ui.graphics.vector.ImageVector,
    titolo: String,
    sottotitolo: String,
    colore: Color,
    abilitata: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tinta = if (abilitata) colore else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (abilitata) colore.copy(alpha = 0.13f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable(enabled = abilitata, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tinta.copy(alpha = if (abilitata) 0.22f else 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icona, contentDescription = null, tint = tinta, modifier = Modifier.size(19.dp))
        }
        Text(
            titolo,
            style = MaterialTheme.typography.titleMedium,
            color = if (abilitata) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            sottotitolo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RigaAzione(
    icona: androidx.compose.ui.graphics.vector.ImageVector,
    titolo: String,
    sottotitolo: String,
    colore: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Icon(icona, contentDescription = null, tint = colore, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                titolo,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                sottotitolo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icone.Destra,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
    }
}

/** Registrare un'entrata: quanto è arrivato e da dove viene. */
@Composable
private fun PassoEntrata(entrate: List<Category>, onConferma: (Money, String) -> Unit) {
    var digitato by remember { mutableStateOf(Digitazione()) }
    var categoria by remember { mutableStateOf(entrate.firstOrNull()) }
    val importo = digitato.importo

    Text(
        "Da dove arriva",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Il colore si legge qui e non dentro la lambda: `extra` è un getter @Composable,
    // e ChipRow chiama `colore` fuori dal contesto di composizione.
    val verde = extra.income
    ChipRow(
        voci = entrate,
        scelta = categoria,
        etichetta = { it.name },
        colore = { verde },
        onScelta = { categoria = it },
    )
    ImportoGrande(digitato)
    Tastierino(digitato) { digitato = it }
    val pronto = digitato.valido && categoria != null
    Azione("Registra entrata", extra.income, pronto) {
        categoria?.let { onConferma(importo, it.id) }
    }
}

@Composable
private fun PassoTrasferimento(
    da: Account,
    altri: List<Account>,
    onConferma: (Account, Money) -> Unit,
) {
    var destinazione by remember { mutableStateOf(altri.firstOrNull()) }
    var digitato by remember { mutableStateOf(Digitazione()) }
    val importo = digitato.importo

    Text("Verso quale conto", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ChipRow(
        voci = altri,
        scelta = destinazione,
        etichetta = { it.name },
        colore = { Color(it.colorArgb) },
        onScelta = { destinazione = it },
    )
    ImportoGrande(digitato)
    Tastierino(digitato) { digitato = it }
    val pronto = digitato.valido && destinazione != null
    Azione("Trasferisci", extra.brandEnd, pronto) {
        destinazione?.let { onConferma(it, importo) }
    }
    Text(
        "Il denaro si sposta ma non viene speso: il trasferimento non entra nei totali " +
            "di spesa né nelle statistiche.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Si scrive quanto si ha davvero, non la differenza.
 *
 * È il punto dell'operazione: guardi l'app della banca, leggi il numero, lo scrivi. Il
 * calcolo lo fa l'app e te lo mostra prima di confermare, così lo controlli senza mai
 * fare una sottrazione a mente.
 */
@Composable
private fun PassoAllinea(saldoAttuale: Money, onConferma: (Money) -> Unit) {
    var digitato by remember { mutableStateOf(Digitazione()) }
    val reale = digitato.importo
    val differenza = reale - saldoAttuale
    // Zero è un saldo legittimo: quello che distingue "non ho ancora scritto niente" da
    // "ho scritto zero" è che si sia toccato un tasto, non che il numero sia diverso da 0.
    val scritto = !digitato.vuota

    Row(Modifier.fillMaxWidth()) {
        Text(
            "Secondo l'app hai",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            saldoAttuale.format(),
            style = MaterialTheme.typography.bodyMedium.tabular,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Text(
        "Quanto hai davvero adesso?",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    ImportoGrande(digitato)

    if (scritto && !differenza.isZero) {
        val colore = if (differenza.isExpense) MaterialTheme.colorScheme.error else extra.income
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(colore.copy(alpha = 0.16f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (differenza.isExpense) "Rettifica in meno" else "Rettifica in più",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Diventa un movimento",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                differenza.format(withSign = true),
                style = MaterialTheme.typography.titleMedium.tabular,
                color = colore,
            )
        }
    }

    Tastierino(digitato) { digitato = it }
    Azione("Allinea", extra.brandEnd, scritto && !differenza.isZero) { onConferma(reale) }
}

@Composable
private fun PassoModifica(
    conto: Account,
    onSalva: (Account) -> Unit,
    onRimuovi: () -> Unit,
) {
    var nome by remember { mutableStateOf(conto.name) }
    var colore by remember { mutableStateOf(conto.colorArgb) }
    var icona by remember { mutableStateOf(conto.icon ?: "wallet") }
    var vincolato by remember { mutableStateOf(!conto.includedInTotal) }

    CampoTesto(nome, "Nome del conto") { nome = it }
    SceltaColore(colore) { colore = it }
    SceltaIcona(icona, Color(colore)) { icona = it }
    InterruttoreVincolato(vincolato) { vincolato = it }

    Azione("Salva", extra.brandEnd, nome.isNotBlank()) {
        onSalva(
            conto.copy(
                name = nome.trim(),
                colorArgb = colore,
                icon = icona,
                includedInTotal = !vincolato,
            )
        )
    }
    Azione("Elimina", MaterialTheme.colorScheme.error, onClick = onRimuovi)
    Text(
        "Se il conto ha già dei movimenti non viene cancellato ma archiviato, così quei " +
            "movimenti restano corretti.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoglioNuovoConto(
    onChiudi: () -> Unit,
    onConferma: (String, Int, String, Boolean, Money) -> Unit,
) {
    var nome by remember { mutableStateOf("") }
    var colore by remember { mutableStateOf(TAVOLOZZA.first()) }
    var icona by remember { mutableStateOf("wallet") }
    var vincolato by remember { mutableStateOf(false) }
    var digitato by remember { mutableStateOf(Digitazione()) }
    val apertura = digitato.importo

    ModalBottomSheet(
        onDismissRequest = onChiudi,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ContenutoFoglio(spazio = 16.dp) {
            Text("Nuovo conto", style = MaterialTheme.typography.titleMedium)
            CampoTesto(nome, "Nome del conto") { nome = it }
            SceltaColore(colore) { colore = it }
            SceltaIcona(icona, Color(colore)) { icona = it }
            InterruttoreVincolato(vincolato) { vincolato = it }

            Text(
                "Quanto c'è dentro adesso",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ImportoGrande(digitato)
            Tastierino(digitato) { digitato = it }
            Azione("Crea", extra.brandEnd, nome.isNotBlank()) {
                onConferma(nome.trim(), colore, icona, vincolato, apertura)
            }
        }
    }
}

/**
 * L'interruttore che decide se il conto entra nella disponibilità.
 *
 * Nasce da un caso concreto: una carta per un sussidio contiene denaro che non si può
 * spendere per quello che si vuole. Sommarlo alla disponibilità fa credere di avere più
 * di quanto si ha, ma nasconderlo del tutto lo farebbe dimenticare — per questo resta
 * visibile in un totale separato invece che sparire.
 */
@Composable
private fun InterruttoreVincolato(vincolato: Boolean, onCambia: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Denaro vincolato",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Resta visibile ma fuori dalla disponibilità",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = vincolato,
            onCheckedChange = onCambia,
            colors = SwitchDefaults.colors(
                checkedThumbColor = extra.brandEnd,
                checkedTrackColor = extra.brandEnd.copy(alpha = 0.35f),
            ),
        )
    }
}
