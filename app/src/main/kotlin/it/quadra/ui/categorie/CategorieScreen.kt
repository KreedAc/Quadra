package it.quadra.ui.categorie

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import it.quadra.core.model.Category
import it.quadra.core.model.CategoryKind
import it.quadra.data.LedgerRepository
import it.quadra.ui.ICONE_SCEGLIBILI
import it.quadra.ui.Icone
import it.quadra.ui.common.Azione
import it.quadra.ui.common.CampoTesto
import it.quadra.ui.common.SceltaColore
import it.quadra.ui.common.SceltaIcona
import it.quadra.ui.common.TAVOLOZZA
import it.quadra.ui.iconFor
import it.quadra.ui.theme.extra
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class StatoCategorie(val tutte: List<Category> = emptyList()) {
    val principali: List<Category>
        get() = tutte.filter { it.isTopLevel && !it.hidden }.sortedBy { it.sortOrder }

    fun figlie(id: String): List<Category> =
        tutte.filter { it.parentId == id && !it.hidden }.sortedBy { it.sortOrder }
}

class CategorieViewModel(private val repository: LedgerRepository) : ViewModel() {

    val stato: StateFlow<StatoCategorie> = repository.observeCategories()
        .map { StatoCategorie(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatoCategorie())

    fun salva(categoria: Category) {
        viewModelScope.launch { repository.salvaCategoria(categoria) }
    }

    fun rimuovi(categoria: Category) {
        viewModelScope.launch { repository.rimuoviCategoria(categoria) }
    }

    /** Nuova voce di primo livello, in fondo alla griglia. */
    fun nuovaPrincipale(nome: String, colore: Int, icona: String) {
        val posto = stato.value.principali.size
        salva(
            Category(
                id = UUID.randomUUID().toString(),
                name = nome,
                kind = CategoryKind.EXPENSE,
                parentId = null,
                colorArgb = colore,
                icon = icona,
                sortOrder = posto,
                isSystem = false,
            )
        )
    }

    /** Nuova sottocategoria: eredita colore e natura della madre, come tutte le altre. */
    fun nuovaSotto(madre: Category, nome: String) {
        val posto = stato.value.figlie(madre.id).size
        salva(
            Category(
                id = UUID.randomUUID().toString(),
                name = nome,
                kind = madre.kind,
                parentId = madre.id,
                colorArgb = madre.colorArgb,
                icon = null,
                sortOrder = posto,
                isSystem = false,
            )
        )
    }
}

/**
 * Le categorie sono dell'utente, non nostre.
 *
 * Quelle predefinite sono un punto di partenza tarato sull'Italia, non una gabbia: si
 * rinominano, si ricolorano, si cambia icona, se ne aggiungono e si tolgono di mezzo
 * quelle che non servono. Chi tiene tutto com'è non deve toccare niente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorieScreen(
    viewModel: CategorieViewModel,
    onIndietro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stato by viewModel.stato.collectAsStateWithLifecycle()
    var inModifica by remember { mutableStateOf<Category?>(null) }
    var nuovaSottoDi by remember { mutableStateOf<Category?>(null) }
    var nuovaPrincipale by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icone.Sinistra,
                    contentDescription = "Indietro",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp).clickable(onClick = onIndietro),
                )
                Spacer(Modifier.size(10.dp))
                Text("Categorie", style = MaterialTheme.typography.headlineSmall)
            }
        }

        items(stato.principali, key = { it.id }) { categoria ->
            BloccoCategoria(
                categoria = categoria,
                figlie = stato.figlie(categoria.id),
                onModifica = { inModifica = it },
                onAggiungiSotto = { nuovaSottoDi = categoria },
            )
        }

        item {
            Riga(
                testo = "Aggiungi una categoria",
                colore = extra.brandEnd,
                onClick = { nuovaPrincipale = true },
            )
        }

        item { Spacer(Modifier.height(96.dp)) }
    }

    inModifica?.let { categoria ->
        FoglioModifica(
            categoria = categoria,
            onChiudi = { inModifica = null },
            onSalva = { viewModel.salva(it); inModifica = null },
            onRimuovi = { viewModel.rimuovi(categoria); inModifica = null },
        )
    }

    nuovaSottoDi?.let { madre ->
        FoglioNome(
            titolo = "Nuova voce in ${madre.name}",
            onChiudi = { nuovaSottoDi = null },
            onConferma = { viewModel.nuovaSotto(madre, it); nuovaSottoDi = null },
        )
    }

    if (nuovaPrincipale) {
        FoglioNuovaCategoria(
            onChiudi = { nuovaPrincipale = false },
            onConferma = { nome, colore, icona ->
                viewModel.nuovaPrincipale(nome, colore, icona)
                nuovaPrincipale = false
            },
        )
    }
}

@Composable
private fun BloccoCategoria(
    categoria: Category,
    figlie: List<Category>,
    onModifica: (Category) -> Unit,
    onAggiungiSotto: () -> Unit,
) {
    val colore = Color(categoria.colorArgb)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onModifica(categoria) },
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
                Icon(iconFor(categoria.icon), contentDescription = null, tint = colore, modifier = Modifier.size(19.dp))
            }
            Text(
                categoria.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icone.Matita,
                contentDescription = "Modifica",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(figlie, key = { it.id }) { figlia ->
                // Anche le sottocategorie si toccano per modificarle: la matita in alto
                // vale per tutto il blocco, non solo per la riga su cui sta.
                Chip(figlia.name, MaterialTheme.colorScheme.surfaceVariant) { onModifica(figlia) }
            }
            item {
                Chip("+ Aggiungi", colore.copy(alpha = 0.20f), onAggiungiSotto)
            }
        }
    }
}

@Composable
private fun Chip(testo: String, sfondo: Color, onClick: () -> Unit) {
    Text(
        testo,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(sfondo)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun Riga(testo: String, colore: Color, onClick: () -> Unit) {
    Text(
        testo,
        style = MaterialTheme.typography.bodyLarge,
        color = colore,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(18.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoglioModifica(
    categoria: Category,
    onChiudi: () -> Unit,
    onSalva: (Category) -> Unit,
    onRimuovi: () -> Unit,
) {
    var nome by remember { mutableStateOf(categoria.name) }
    var colore by remember { mutableStateOf(categoria.colorArgb) }
    var icona by remember { mutableStateOf(categoria.icon) }

    ModalBottomSheet(
        onDismissRequest = onChiudi,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CampoTesto(nome, "Nome") { nome = it }

            // Colore e icona solo al primo livello: le sottocategorie li ereditano dalla
            // madre, ed è quello che le tiene riconoscibili come famiglia.
            if (categoria.isTopLevel) {
                SceltaColore(colore) { colore = it }
                SceltaIcona(icona, Color(colore)) { icona = it }
            }

            Azione("Salva", extra.brandEnd) {
                if (nome.isNotBlank()) {
                    onSalva(categoria.copy(name = nome.trim(), colorArgb = colore, icon = icona))
                }
            }
            Azione("Elimina", MaterialTheme.colorScheme.error, onClick = onRimuovi)
            Text(
                "Se la categoria è già stata usata da qualche spesa non viene cancellata " +
                    "ma nascosta, così i movimenti esistenti restano corretti.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoglioNome(titolo: String, onChiudi: () -> Unit, onConferma: (String) -> Unit) {
    var nome by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onChiudi,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(titolo, style = MaterialTheme.typography.titleMedium)
            CampoTesto(nome, "Nome") { nome = it }
            Azione("Aggiungi", extra.brandEnd) { if (nome.isNotBlank()) onConferma(nome.trim()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoglioNuovaCategoria(
    onChiudi: () -> Unit,
    onConferma: (String, Int, String) -> Unit,
) {
    var nome by remember { mutableStateOf("") }
    var colore by remember { mutableStateOf(TAVOLOZZA.first()) }
    var icona by remember { mutableStateOf(ICONE_SCEGLIBILI.first()) }

    ModalBottomSheet(
        onDismissRequest = onChiudi,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Nuova categoria", style = MaterialTheme.typography.titleMedium)
            CampoTesto(nome, "Nome") { nome = it }
            SceltaColore(colore) { colore = it }
            SceltaIcona(icona, Color(colore)) { icona = it }
            Azione("Crea", extra.brandEnd) { if (nome.isNotBlank()) onConferma(nome.trim(), colore, icona) }
        }
    }
}




