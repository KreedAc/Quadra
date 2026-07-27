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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.quadra.core.model.Category
import it.quadra.core.model.Transaction
import it.quadra.ui.iconFor
import it.quadra.ui.theme.extra
import it.quadra.ui.theme.tabular
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatoGiorno = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
private val formatoMese = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)

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

    LazyColumn(
        modifier = modifier.padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { SelettoreMese(stato, viewModel) }
        item { SchedaSpeso(stato) }

        stato.giornate.forEach { giornata ->
            item(key = "giorno-${giornata.data}") { IntestazioneGiorno(giornata) }
            items(giornata.movimenti, key = { it.id }) { movimento ->
                RigaMovimento(
                    movimento = movimento,
                    categoria = stato.categoria(movimento.categoryId),
                    sottotitolo = stato.sottotitolo(movimento),
                    onLongClick = { viewModel.cancella(movimento) },
                )
            }
        }

        if (stato.caricato && stato.giornate.isEmpty()) {
            item { StatoVuoto() }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun SelettoreMese(stato: StatoMovimenti, viewModel: MovimentiViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stato.mese.atDay(1).format(formatoMese).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.ChevronLeft,
            contentDescription = "Mese precedente",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp).clickable { viewModel.mesePrecedente() },
        )
        Spacer(Modifier.size(12.dp))
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = "Mese successivo",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp).clickable { viewModel.meseSuccessivo() },
        )
    }
}

/** L'unico posto, insieme al pulsante di salvataggio, dove compare il gradiente. */
@Composable
private fun SchedaSpeso(stato: StatoMovimenti) {
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
    }
}

@Composable
private fun IntestazioneGiorno(giornata: Giornata) {
    Row(Modifier.fillMaxWidth()) {
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
    sottotitolo: String,
    onLongClick: () -> Unit,
) {
    val colore = categoria?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
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
}

@Composable
private fun StatoVuoto() {
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
