package it.quadra.ui.movimenti

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.quadra.core.input.Digitazione
import it.quadra.core.model.Category
import it.quadra.core.model.Money
import it.quadra.core.model.Transaction
import it.quadra.ui.common.Azione
import it.quadra.ui.common.ImportoGrande
import it.quadra.ui.common.Tastierino
import it.quadra.ui.iconFor
import it.quadra.ui.theme.extra
import it.quadra.ui.theme.tabular
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatoGiorno = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
private val formatoMese = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
private val formatoLettera = DateTimeFormatter.ofPattern("EEE", Locale.ITALIAN)

@Composable
fun MovimentiScreen(
    viewModel: MovimentiViewModel,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val stato by viewModel.stato.collectAsStateWithLifecycle()
    val cancellazione by viewModel.cancellazione.collectAsStateWithLifecycle()

    // La finestra per tornare indietro. Passata questa, non resta traccia da nessuna parte.
    LaunchedEffect(cancellazione) {
        cancellazione ?: return@LaunchedEffect
        val esito = snackbar.showSnackbar(
            message = "Movimento eliminato",
            actionLabel = "ANNULLA",
            duration = SnackbarDuration.Short,
        )
        if (esito == SnackbarResult.ActionPerformed) viewModel.annullaCancellazione()
        else viewModel.scartaAnnullamento()
    }

    val giornate = stato.giornateVisibili
    var budgetAperto by remember { mutableStateOf(false) }

    LazyColumn(modifier = modifier.padding(horizontal = 18.dp)) {
        item { SelettoreMese(stato, viewModel) }
        item { SchedaSpeso(stato) { budgetAperto = true } }
        item { StrisciaGiorni(stato) { viewModel.scegliGiorno(it) } }

        giornate.forEach { giornata ->
            item(key = "giorno-${giornata.data}") { IntestazioneGiorno(giornata) }
            itemsIndexed(giornata.movimenti, key = { _, m -> m.id }) { indice, movimento ->
                RigaMovimento(
                    movimento = movimento,
                    categoria = stato.categoria(movimento.categoryId),
                    icona = stato.icona(movimento.categoryId),
                    sottotitolo = stato.sottotitolo(movimento),
                    // L'ultima riga del giorno non porta il filo: chiuderebbe un elenco
                    // che è già chiuso dall'intestazione del giorno dopo.
                    ultimo = indice == giornata.movimenti.lastIndex,
                    onLongClick = { viewModel.cancella(movimento) },
                )
            }
        }

        if (stato.caricato && giornate.isEmpty()) {
            item { StatoVuoto(stato.giornoScelto) }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }

    if (budgetAperto) {
        FoglioBudget(
            attuale = stato.andamento?.budget ?: Money.ZERO,
            onChiudi = { budgetAperto = false },
            onSalva = {
                viewModel.salvaBudget(it)
                budgetAperto = false
            },
        )
    }
}

/**
 * I giorni del mese, scorrevoli, con quanto è uscito in ciascuno.
 *
 * Sceglie invece di scorrere. Un tocco che porta la lista a un punto sembra comodo ma
 * lascia intorno tutto il resto del mese, e per capire quanto si è speso martedì bisogna
 * comunque leggere dove finisce martedì. Filtrare risponde alla domanda vera.
 *
 * Il giorno scelto si toglie ritoccandolo: nessuna X da cercare.
 */
@Composable
private fun StrisciaGiorni(stato: StatoMovimenti, onScegli: (LocalDate) -> Unit) {
    if (stato.striscia.isEmpty()) return
    val lista = rememberLazyListState()

    // Si apre su oggi, o sull'ultimo giorno del mese se si sta guardando il passato:
    // il primo giorno del mese non è quasi mai quello che interessa.
    LaunchedEffect(stato.mese) {
        val indice = stato.striscia.indexOfFirst { it.oggi }
        lista.scrollToItem(if (indice >= 0) maxOf(indice - 2, 0) else stato.striscia.lastIndex)
    }

    LazyRow(
        state = lista,
        modifier = Modifier.padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(stato.striscia, key = { it.data.toString() }) { giorno ->
            val scelto = stato.giornoScelto == giorno.data
            val sfondo = when {
                scelto -> extra.brandEnd.copy(alpha = 0.20f)
                giorno.oggi -> MaterialTheme.colorScheme.surfaceVariant
                else -> Color.Transparent
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(sfondo)
                    .clickable { onScegli(giorno.data) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    giorno.data.format(formatoLettera).uppercase().take(1),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    giorno.data.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium.tabular,
                    color = when {
                        scelto -> extra.brandEnd
                        giorno.oggi -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(3.dp))
                // Un punto e non l'importo: le cifre a questa dimensione non si leggono,
                // e quello che serve a colpo d'occhio è in quali giorni si è speso.
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (giorno.speso.isZero) Color.Transparent
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                )
            }
        }
    }
}

@Composable
private fun SelettoreMese(stato: StatoMovimenti, viewModel: MovimentiViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stato.mese.atDay(1).format(formatoMese).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        Icon(
            it.quadra.ui.Icone.Sinistra,
            contentDescription = "Mese precedente",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp).clickable { viewModel.mesePrecedente() },
        )
        Spacer(Modifier.size(12.dp))
        Icon(
            it.quadra.ui.Icone.Destra,
            contentDescription = "Mese successivo",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp).clickable { viewModel.meseSuccessivo() },
        )
    }
}

/** L'unico posto, insieme al pulsante di salvataggio, dove compare il gradiente. */
@Composable
private fun SchedaSpeso(stato: StatoMovimenti, onTocca: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
            .clickable(onClick = onTocca)
            .padding(20.dp),
    ) {
        Text(
            "Speso questo mese",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stato.speso.format(),
            style = MaterialTheme.typography.displaySmall.tabular,
            color = MaterialTheme.colorScheme.onSurface,
        )

        val andamento = stato.andamento
        if (andamento == null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Tocca per darti un budget mensile",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.13f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(andamento.frazione)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        // Il rosso arriva solo quando si è davvero sforato. Colorare di
                        // rosso l'ultimo quarto farebbe suonare l'allarme ogni mese
                        // intorno al venti, e a quel punto smetterebbe di dire qualcosa.
                        if (andamento.sforato) SolidColor(MaterialTheme.colorScheme.error)
                        else Brush.horizontalGradient(listOf(extra.brandStart, extra.brandEnd))
                    ),
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                buildString {
                    append(if (andamento.sforato) "Sforato di " else "Restano ")
                    append(andamento.restano.abs().format())
                },
                style = MaterialTheme.typography.bodySmall.tabular,
                color = if (andamento.sforato) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                andamento.alGiorno?.let { "${it.format()} al giorno" }
                    ?: "su ${andamento.budget.format()}",
                style = MaterialTheme.typography.bodySmall.tabular,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Il budget si scrive col tastierino, come tutti gli altri importi dell'app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoglioBudget(attuale: Money, onChiudi: () -> Unit, onSalva: (Money) -> Unit) {
    var digitato by remember { mutableStateOf(Digitazione.da(attuale)) }

    ModalBottomSheet(
        onDismissRequest = onChiudi,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Budget mensile", style = MaterialTheme.typography.titleMedium)
            Text(
                "Quanto vuoi spendere al massimo in un mese. Vale per tutti i mesi, " +
                    "non va reimpostato ogni volta.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ImportoGrande(digitato)
            Tastierino(digitato) { digitato = it }
            Azione("Salva", extra.brandEnd) { onSalva(digitato.importo) }
            if (!attuale.isZero) {
                Azione("Togli il budget", MaterialTheme.colorScheme.onSurfaceVariant) {
                    onSalva(Money.ZERO)
                }
            }
        }
    }
}

@Composable
private fun IntestazioneGiorno(giornata: Giornata) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 2.dp)) {
        Text(
            etichettaGiorno(giornata.data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            giornata.totale.format(),
            style = MaterialTheme.typography.bodyMedium.tabular,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RigaMovimento(
    movimento: Transaction,
    categoria: Category?,
    icona: String?,
    sottotitolo: String,
    ultimo: Boolean,
    onLongClick: () -> Unit,
) {
    val colore = categoria?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
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
                iconFor(icona),
                contentDescription = null,
                tint = colore,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                movimento.description.ifBlank { categoria?.name.orEmpty() },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sottotitolo.isNotBlank()) {
                Text(
                    sottotitolo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            movimento.amount.abs().format(),
            style = MaterialTheme.typography.titleMedium.tabular,
            // Gli importi normali restano testo neutro: un'app che colora di rosso ogni
            // spesa genera ansia. Il verde segnala solo le entrate.
            color = if (movimento.isIncome) extra.income else MaterialTheme.colorScheme.onSurface,
        )
    }
        if (!ultimo) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                // Parte dal testo e non dal bordo: allineato all'icona farebbe
                // sembrare l'icona parte della riga sotto.
                modifier = Modifier.padding(start = 48.dp),
            )
        }
    }
}

@Composable
private fun StatoVuoto(giornoScelto: LocalDate?) {
    if (giornoScelto != null) {
        Text(
            "Niente il ${giornoScelto.format(formatoGiorno)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
            textAlign = TextAlign.Center,
        )
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Nessun movimento questo mese",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Tocca + per registrare la prima spesa",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Tieni premuto su un movimento per eliminarlo",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun etichettaGiorno(data: LocalDate): String = when (data) {
    LocalDate.now() -> "Oggi"
    LocalDate.now().minusDays(1) -> "Ieri"
    else -> data.format(formatoGiorno).replaceFirstChar { it.uppercase() }
}
