package it.quadra.ui.statistiche

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import it.quadra.ui.Icone
import it.quadra.ui.theme.extra
import it.quadra.ui.theme.tabular
import it.quadra.ui.theme.tinta
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private val formatoMeseCorto = DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN)

data class StatoStatistiche(
    val mese: YearMonth = YearMonth.now(),
    val speso: Money = Money.ZERO,
    val mediaGiornaliera: Money = Money.ZERO,
    val perMese: List<MonthTotal> = emptyList(),
    val perCategoria: List<CategoryTotal> = emptyList(),
    /** Le voci dentro ogni famiglia, per la riga che si apre. Calcolate una volta sola. */
    val dentroCategoria: Map<String, List<CategoryTotal>> = emptyMap(),
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
        val famiglie = Statistics.byRootCategory(delMese, radice)
        // La scomposizione si calcola qui e non al tocco: sono poche decine di movimenti,
        // e farla in composizione significherebbe rifare il conto a ogni ridisegno.
        val dentro = famiglie.associate { famiglia ->
            famiglia.categoryId to Statistics.bySubcategory(delMese, famiglia.categoryId, radice)
        }

        StatoStatistiche(
            mese = mese,
            speso = Ledger.totalSpent(delMese),
            mediaGiornaliera = Statistics.dailyAverage(movimenti, mese),
            perMese = Statistics.monthlySpending(movimenti, Statistics.lastMonths(mese, 6)),
            perCategoria = famiglie,
            dentroCategoria = dentro,
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
    // Una famiglia aperta alla volta: aprirne cinque riempirebbe la pagina di barre
    // sottili in cui non si distingue più quali appartengono a cosa.
    var aperta by remember { mutableStateOf<String?>(null) }

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
                RigaCategoria(
                    voce = voce,
                    categoria = stato.categoria(voce.categoryId),
                    aperta = aperta == voce.categoryId,
                    dentro = stato.dentroCategoria[voce.categoryId].orEmpty(),
                    nome = { stato.categoria(it)?.name },
                    onApri = { aperta = if (aperta == voce.categoryId) null else voce.categoryId },
                    modifier = Modifier.animateItem(),
                )
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

/**
 * Una famiglia di spesa, che si apre per dire dove sono finiti quei soldi.
 *
 * Il totale risponde a "quanto", non a "dove": duecento euro in Ristoranti diventano
 * un'informazione solo quando si scopre che centocinquanta erano pizzerie. È la domanda
 * che viene subito dopo aver letto il numero, e prima non aveva risposta.
 *
 * Si apre **al tocco** e non tenendo premuto. La pressione lunga era il gesto con cui si
 * cancellava un movimento, e l'abbiamo tolta proprio perché nessuno la trovava: non ha
 * senso reintrodurla altrove. Qui il tocco era libero, ed è lo stesso gesto con cui si
 * aprono le categorie nelle impostazioni.
 *
 * Le barre di dentro sono in scala sulla famiglia, non sul mese: si sta guardando dentro
 * quella, e riferirle a un totale diverso da quello scritto sopra non tornerebbe.
 */
@Composable
private fun RigaCategoria(
    voce: CategoryTotal,
    categoria: Category?,
    aperta: Boolean,
    dentro: List<CategoryTotal>,
    nome: (String) -> String?,
    onApri: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colore = categoria?.let { tinta(it.colorArgb) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    // Una famiglia con una voce sola non ha niente da scomporre: aprirla mostrerebbe la
    // stessa riga due volte, quindi non si apre affatto e non finge di poterlo fare.
    val scomponibile = dentro.size > 1
    val rotazione by animateFloatAsState(if (aperta) 90f else 0f, label = "freccia")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = scomponibile, onClick = onApri)
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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
            if (scomponibile) {
                Icon(
                    Icone.Destra,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp).rotate(rotazione),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                voce.total.format(),
                style = MaterialTheme.typography.titleMedium.tabular,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Barra(voce.share, colore)

        AnimatedVisibility(visible = aperta) {
            Column(
                modifier = Modifier.padding(start = 17.dp, top = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                dentro.forEach { sotto ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                // La spesa messa sulla famiglia senza scegliere una voce
                                // esiste e va nominata: chiamarla col nome della famiglia
                                // la farebbe sembrare un doppione della riga sopra.
                                if (sotto.categoryId == voce.categoryId) "Senza voce"
                                else nome(sotto.categoryId) ?: sotto.categoryId,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${(sotto.share * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall.tabular,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                sotto.total.format(),
                                style = MaterialTheme.typography.bodyMedium.tabular,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Barra(sotto.share, colore.copy(alpha = 0.55f), alta = 4.dp)
                    }
                }
            }
        }
    }
}

/** La barra di una quota. Sempre visibile anche a zero virgola: una riga senza barra sembra rotta. */
@Composable
private fun Barra(quota: Double, colore: Color, alta: Dp = 6.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(alta)
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(quota.toFloat().coerceIn(0.02f, 1f))
                .height(alta)
                .clip(RoundedCornerShape(99.dp))
                .background(colore)
        )
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
