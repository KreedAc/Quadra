package it.quadra.ui.inserimento

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.quadra.core.input.Digitazione
import it.quadra.core.model.Account
import it.quadra.core.model.Category
import it.quadra.core.model.Money
import it.quadra.ui.Icone
import it.quadra.ui.common.ImportoGrande
import it.quadra.ui.common.Tastierino
import it.quadra.ui.iconFor
import it.quadra.ui.theme.extra

/**
 * L'inserimento in due tocchi, a schermo intero.
 *
 * Primo passo la griglia, mai una lista che scorre: le categorie stanno sempre nelle
 * stesse posizioni, e dopo una settimana il pollice ci arriva senza leggere. La velocità
 * non viene dai tocchi risparmiati, viene dal non dover cercare.
 *
 * Secondo passo il tastierino, con le sottocategorie già filtrate dalla scelta appena
 * fatta. Il tastierino è disegnato qui e non è quello di sistema: per i numeri quello di
 * sistema è lento, piccolo e pieno di tasti che non servono.
 *
 * Occupa tutto lo schermo invece di essere un foglio che sale dal basso. Un foglio si
 * chiude trascinandolo, e su una griglia che si scorre col dito quei due gesti si
 * contendono lo stesso movimento: capitava di far sparire tutto mentre si cercava una
 * categoria. A schermo intero il gesto della griglia è solo della griglia, e la via
 * d'uscita è una sola e visibile.
 */
@Composable
fun AggiungiScreen(
    categorie: List<Category>,
    tutteLeCategorie: List<Category>,
    conti: List<Account>,
    onChiudi: () -> Unit,
    onPersonalizza: () -> Unit,
    onSalva: (importo: Money, categoriaId: String, contoId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scelta by remember { mutableStateOf<Category?>(null) }

    // Il tasto indietro fa un passo alla volta: dal tastierino torna alla griglia, e
    // solo dalla griglia esce. Uscire direttamente perderebbe l'importo digitato.
    BackHandler { if (scelta != null) scelta = null else onChiudi() }

    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (scelta == null) "Nuova spesa" else "Quanto",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onChiudi),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icone.Chiudi,
                    contentDescription = "Chiudi",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // I due passi scorrono lateralmente: si capisce che è lo stesso gesto che
        // avanza, e che indietro si torna.
        AnimatedContent(
            targetState = scelta,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val avanti = targetState != null
                val verso = if (avanti) 1 else -1
                (slideInHorizontally { it * verso / 3 } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally { -it * verso / 3 } + fadeOut(tween(180)))
            },
            label = "passo",
        ) { selezionata ->
            if (selezionata == null) {
                PassoGriglia(categorie, onPersonalizza) { scelta = it }
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
}

@Composable
private fun PassoGriglia(
    categorie: List<Category>,
    onPersonalizza: () -> Unit,
    onScelta: (Category) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
        Text(
            "Dove è andata",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            gridItems(categorie, key = { it.id }) { categoria ->
                Casella(categoria) { onScelta(categoria) }
            }
            // La matita sta qui, in coda alla griglia, e non solo nelle impostazioni:
            // è guardando le proprie categorie che viene voglia di cambiarle, e chi non
            // sa che si può fare non va a cercarlo in un altro posto.
            item(key = "personalizza") { CasellaMatita(onPersonalizza) }
        }
    }
}

@Composable
private fun CasellaMatita(onClick: () -> Unit) {
    val colore = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icone.Matita, contentDescription = null, tint = colore, modifier = Modifier.size(20.dp))
        }
        Text(
            "Modifica",
            style = MaterialTheme.typography.labelSmall,
            color = colore,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
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
    var digitato by remember { mutableStateOf(Digitazione()) }
    var sottoscelta by remember { mutableStateOf<Category?>(null) }
    var conto by remember { mutableStateOf(conti.firstOrNull()) }

    val importo = digitato.importo

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(bottom = 16.dp),
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

        ImportoGrande(digitato)

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

        // Lo spazio avanzato sta in mezzo: il tastierino e il salvataggio restano in
        // basso, dove arriva il pollice, invece di galleggiare a metà schermo.
        Spacer(Modifier.weight(1f))

        Tastierino(digitato) { digitato = it }

        val contoScelto = conto
        val abilitato = digitato.valido && contoScelto != null
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
