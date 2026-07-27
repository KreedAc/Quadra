package it.quadra.ui.conti

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
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
import it.quadra.core.ledger.Totals
import it.quadra.core.model.Account
import it.quadra.core.model.Money
import it.quadra.data.LedgerRepository
import it.quadra.ui.iconFor
import it.quadra.ui.theme.extra
import it.quadra.ui.theme.tabular
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatoConti(
    val spendibili: List<Account> = emptyList(),
    val vincolati: List<Account> = emptyList(),
    val saldi: Map<String, Money> = emptyMap(),
    val totali: Totals = Totals(Money.ZERO, Money.ZERO),
) {
    fun saldo(conto: Account): Money = saldi[conto.id] ?: Money.ZERO
}

class ContiViewModel(repository: LedgerRepository) : ViewModel() {
    val stato: StateFlow<StatoConti> = combine(
        repository.observeAccounts(),
        repository.observeAllTransactions(),
    ) { conti, movimenti ->
        val attivi = conti.filterNot { it.archived }
        StatoConti(
            spendibili = attivi.filter { it.includedInTotal },
            vincolati = attivi.filterNot { it.includedInTotal },
            saldi = Ledger.balances(conti, movimenti),
            totali = Ledger.totals(conti, movimenti),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatoConti())
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

    LazyColumn(
        modifier = modifier.padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(
                "Conti",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item { SchedaDisponibile(stato) }

        if (stato.spendibili.isNotEmpty()) {
            item { Sezione("Spendibili", stato.totali.available) }
            items(stato.spendibili, key = { it.id }) { conto ->
                RigaConto(conto, stato.saldo(conto))
            }
        }

        if (stato.vincolati.isNotEmpty()) {
            item { Sezione("Vincolati", stato.totali.constrained) }
            items(stato.vincolati, key = { it.id }) { conto ->
                RigaConto(conto, stato.saldo(conto))
            }
        }

        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun SchedaDisponibile(stato: StatoConti) {
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
                    it.quadra.ui.Icone.Lucchetto,
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
private fun RigaConto(conto: Account, saldo: Money) {
    val colore = Color(conto.colorArgb)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
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
}
