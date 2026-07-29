package it.quadra.ui.statistiche

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import it.quadra.core.ledger.Ledger
import it.quadra.core.model.Category
import it.quadra.core.model.Money
import it.quadra.core.statistics.CategoryTotal
import it.quadra.core.statistics.MonthTotal
import it.quadra.core.statistics.Statistics
import it.quadra.data.LedgerRepository
import it.quadra.ui.theme.extra
import it.quadra.ui.theme.tabular
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatoMeseCorto = DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN)

data class StatoStatistiche(
    val mese: YearMonth = YearMonth.now(),
    val speso: Money = Money.ZERO,
    val mediaGiornaliera: Money = Money.ZERO,
    val perMese: List<MonthTotal> = emptyList(),
    val perCategoria: List<CategoryTotal> = emptyList(),
    val entrato: Money = Money.ZERO,
    val entratePerVoce: List<CategoryTotal> = emptyList(),
    val categorie: List<Category> = emptyList(),
) {
    fun categoria(id: String): Category? = categorie.firstOrNull { it.id == id }
    val massimoMensile: Long get() = perMese.maxOfOrNull { it.spent.cents } ?: 0L
}

class StatisticheViewModel(repository: LedgerRepository) : ViewModel() {

    val stato: StateFlow<StatoStatistiche> = combine(
        repository.observeAllTransactions(),
        repository.observeCategories(),
    ) { movimenti, categorie ->
        val mese = YearMonth.now()
        // La risoluzione della radice viene dalle categorie in archivio, non da quelle
        // predefinite: se l'utente ne ha create di sue devono aggregarsi correttamente.
        val indice = categorie.associateBy { it.id }
        val radice = { id: String -> indice[id]?.parentId ?: id }

        val delMese = movimenti.filter { YearMonth.from(it.date) == mese }
        StatoStatistiche(
            mese = mese,
            speso = Ledger.totalSpent(delMese),
            mediaGiornaliera = Statistics.dailyAverage(movimenti, mese),
            perMese = Statistics.monthlySpending(movimenti, Statistics.lastMonths(mese, 6)),
            perCategoria = Statistics.byRootCategory(delMese, radice),
            entrato = Statistics.totalIncome(delMese),
            entratePerVoce = Statistics.incomeByCategory(delMese),
            categorie = categorie,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatoStatistiche())
}

/**
 * Le statistiche, deliberatamente sobrie.
 *
 * Barre e non torte: confrontare lunghezze è più facile che confrontare fette, e una
 * torta con dodici spicchi non si legge. Ogni categoria porta il proprio nome accanto al
 * colore, così chi non distingue le tinte legge comunque tutto.
 */
@Composable
fun StatisticheScreen(viewModel: StatisticheViewModel, modifier: Modifier = Modifier) {
    val stato by viewModel.stato.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(
                "Statistiche",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item { Riepilogo(stato) }

        if (stato.entratePerVoce.isNotEmpty()) {
            item { Entrate(stato) }
        }

        if (stato.perMese.isNotEmpty()) {
            item { GraficoMensile(stato) }
        }

        if (stato.perCategoria.isEmpty()) {
            item { Vuoto() }
        } else {
            item {
                Text(
                    "PER CATEGORIA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(stato.perCategoria, key = { it.categoryId }) { voce ->
                RigaCategoria(voce, stato.categoria(voce.categoryId))
            }
        }

        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun Riepilogo(stato: StatoStatistiche) {
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
            "Spesa di questo mese",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stato.speso.format(),
            style = MaterialTheme.typography.displaySmall.tabular,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Media di ${stato.mediaGiornaliera.format()} al giorno",
            style = MaterialTheme.typography.bodyMedium.tabular,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Sei mesi, con quello corrente in evidenza. Serie unica, quindi nessuna legenda. */
/**
 * Le entrate del mese, per voce e in totale.
 *
 * Stanno in un riquadro loro e non mescolate alle spese: uno stipendio e un affitto non
 * appartengono alla stessa classifica, e sommarli produrrebbe una percentuale che non
 * risponde a nessuna domanda. Chi ha più fonti — stipendio, sussidio, lavoro autonomo —
 * la somma del mese la deve leggere, non ricavare a mente da tre righe sparse.
 */
@Composable
private fun Entrate(stato: StatoStatistiche) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Entrate del mese",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                stato.entrato.format(),
                style = MaterialTheme.typography.titleLarge.tabular,
                color = extra.income,
            )
        }

        Spacer(Modifier.height(16.dp))
        // Una riga per voce, con la quota sul totale: la barra dice a colpo d'occhio
        // quanto pesa lo stipendio rispetto al resto, il numero dice quanto è.
        stato.entratePerVoce.forEachIndexed { indice, voce ->
            if (indice > 0) Spacer(Modifier.height(12.dp))
            val categoria = stato.categoria(voce.categoryId)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    categoria?.name ?: voce.categoryId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    voce.total.format(),
                    style = MaterialTheme.typography.bodyMedium.tabular,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(voce.share.toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(99.dp))
                        .background(extra.income),
                )
            }
        }
    }
}

@Composable
private fun GraficoMensile(stato: StatoStatistiche) {
    val massimo = stato.massimoMensile.coerceAtLeast(1L)
    Column(Modifier.fillMaxWidth()) {
        Text(
            "ULTIMI SEI MESI",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            stato.perMese.forEach { voce ->
                val corrente = voce.month == stato.mese
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    val frazione = (voce.spent.cents.toFloat() / massimo).coerceIn(0.02f, 1f)
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .fillMaxHeight(frazione * 0.82f)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .then(
                                if (corrente) Modifier.background(extra.brand)
                                else Modifier.background(
                                    extra.brandStart.copy(alpha = 0.32f)
                                )
                            )
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        voce.month.atDay(1).format(formatoMeseCorto),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (corrente) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RigaCategoria(voce: CategoryTotal, categoria: Category?) {
    val colore = categoria?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(colore)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                categoria?.name ?: voce.categoryId,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                voce.total.format(),
                style = MaterialTheme.typography.titleMedium.tabular,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(voce.share.toFloat().coerceIn(0.02f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(colore)
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
            "Ancora niente da mostrare",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Le statistiche compaiono dopo la prima spesa",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
