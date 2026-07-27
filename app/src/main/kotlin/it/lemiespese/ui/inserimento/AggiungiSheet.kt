package it.lemiespese.ui.inserimento

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.lemiespese.core.model.Account
import it.lemiespese.core.model.Category
import it.lemiespese.core.model.Money
import it.lemiespese.ui.iconFor
import it.lemiespese.ui.theme.extra
import it.lemiespese.ui.theme.tabular

/**
 * L'inserimento in due tocchi.
 *
 * Primo passo la griglia, mai una lista che scorre: le categorie stanno sempre nelle
 * stesse posizioni, e dopo una settimana il pollice ci arriva senza leggere. La velocità
 * non viene dai tocchi risparmiati, viene dal non dover cercare.
 *
 * Secondo passo il tastierino, con le sottocategorie già filtrate dalla scelta appena
 * fatta. Il tastierino è disegnato qui e non è quello di sistema: per i numeri quello di
 * sistema è lento, piccolo e pieno di tasti che non servono.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AggiungiSheet(
    categorie: List<Category>,
    tutteLeCategorie: List<Category>,
    conti: List<Account>,
    onChiudi: () -> Unit,
    onSalva: (importo: Money, categoriaId: String, contoId: String) -> Unit,
) {
    val stato = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var scelta by remember { mutableStateOf<Category?>(null) }

    ModalBottomSheet(
        onDismissRequest = onChiudi,
        sheetState = stato,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val selezionata = scelta
        if (selezionata == null) {
            PassoGriglia(categorie) { scelta = it }
        } else {
            PassoImporto(
                categoria = selezionata,
                sottocategorie = tutteLeCategorie
                    .filter { it.parentId == selezionata.id }
                    .sortedBy { it.sortOrder },
                conti = conti,
                onCambiaCategoria = { scelta = null },
                onSalva = onSalva,
            )
        }
    }
}

@Composable
private fun PassoGriglia(categorie: List<Category>, onScelta: (Category) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
        Text(
            "Dove è andata",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            gridItems(categorie, key = { it.id }) { categoria ->
                Casella(categoria) { onScelta(categoria) }
            }
        }
    }
}

@Composable
private fun Casella(categoria: Category, onClick: () -> Unit) {
    val colore = Color(categoria.colorArgb)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(colore.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconFor(categoria.icon), contentDescription = null, tint = colore, modifier = Modifier.size(22.dp))
        }
        Text(
            categoria.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PassoImporto(
    categoria: Category,
    sottocategorie: List<Category>,
    conti: List<Account>,
    onCambiaCategoria: () -> Unit,
    onSalva: (Money, String, String) -> Unit,
) {
    val colore = Color(categoria.colorArgb)
    var cifre by remember { mutableStateOf("") }
    var sottoscelta by remember { mutableStateOf<Category?>(null) }
    var conto by remember { mutableStateOf(conti.firstOrNull()) }

    val importo = Money(cifre.toLongOrNull() ?: 0L)

    Column(
        modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        // Intestazione: la categoria scelta, toccabile per tornare alla griglia.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colore.copy(alpha = 0.18f))
                .clickable(onClick = onCambiaCategoria)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(11.dp))
                    .background(colore.copy(alpha = 0.30f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(iconFor(categoria.icon), contentDescription = null, tint = colore, modifier = Modifier.size(17.dp))
            }
            Text(
                categoria.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Cambia",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (sottocategorie.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(sottocategorie, key = { it.id }) { sotto ->
                    val attiva = sottoscelta?.id == sotto.id
                    Text(
                        sotto.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (attiva) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                if (attiva) colore.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { sottoscelta = if (attiva) null else sotto }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }

        Text(
            importo.format(),
            style = MaterialTheme.typography.displaySmall.tabular,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (conti.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(conti, key = { it.id }) { c ->
                    val attivo = conto?.id == c.id
                    Text(
                        c.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (attivo) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(
                                if (attivo) Color(c.colorArgb).copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { conto = c }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }

        Tastierino(
            onCifra = { c -> if (cifre.length < 9) cifre += c },
            onCancella = { cifre = cifre.dropLast(1) },
        )

        val contoScelto = conto
        val abilitato = !importo.isZero && contoScelto != null
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(if (abilitato) extra.brand else SolidColor(MaterialTheme.colorScheme.surfaceVariant))
                .clickable(enabled = abilitato) {
                    onSalva(importo, (sottoscelta ?: categoria).id, contoScelto!!.id)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Salva",
                style = MaterialTheme.typography.titleMedium,
                color = if (abilitato) Color(0xFF04121A) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Tastierino numerico disegnato a mano.
 *
 * Le cifre si accumulano in centesimi: si digita "1250" e si ottiene 12,50 €. Non c'è
 * un tasto virgola perché non serve — la virgola si sposta da sola, come sui bancomat.
 */
@Composable
private fun Tastierino(onCifra: (Char) -> Unit, onCancella: () -> Unit) {
    val righe = listOf("123", "456", "789", " 0<")
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        righe.forEach { riga ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                riga.forEach { tasto ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(
                                if (tasto == ' ') Color.Transparent
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable(enabled = tasto != ' ') {
                                if (tasto == '<') onCancella() else onCifra(tasto)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (tasto) {
                            ' ' -> Unit
                            '<' -> Icon(
                                Icons.Rounded.Backspace,
                                contentDescription = "Cancella una cifra",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(21.dp),
                            )
                            else -> Text(
                                tasto.toString(),
                                style = MaterialTheme.typography.headlineSmall.tabular,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
