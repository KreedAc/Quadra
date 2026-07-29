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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.quadra.core.input.Digitazione
import it.quadra.core.model.Account
import it.quadra.core.model.Category
import it.quadra.core.model.Money
import it.quadra.ui.Icone
import it.quadra.ui.common.ImportoGrande
import it.quadra.ui.common.Tastierino
import it.quadra.ui.iconFor
import it.quadra.ui.theme.extra
import it.quadra.ui.theme.tinta

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

/**
 * La griglia occupa lo schermo invece di appoggiarcisi sopra.
 *
 * Le caselle erano alte quanto la loro icona e restavano incollate in cima, con sotto
 * mezza schermata di niente: la pagina sembrava incompiuta, e le categorie — che sono la
 * cosa da toccare — finivano tutte lontano dal pollice. Qui l'altezza di una casella si
 * ricava dallo spazio che c'è: le righe visibili si spartiscono la pagina, e quando le
 * categorie sono poche le caselle smettono di crescere e la griglia si centra invece di
 * diventare un manifesto.
 *
 * Cinque righe è il punto in cui una casella arriva al minimo leggibile: oltre, la
 * griglia scorre, che è la ragione per cui resta pigra pur essendo quasi sempre corta.
 */
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
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.weight(1f)) {
            val spazio = 9.dp
            // La matita conta come una casella: sta in griglia, quindi occupa un posto.
            val righe = (categorie.size + 1 + COLONNE - 1) / COLONNE
            val visibili = righe.coerceIn(1, RIGHE_A_VISTA)
            val altezza = ((maxHeight - spazio * (visibili - 1)) / visibili)
                .coerceIn(ALTEZZA_MINIMA, ALTEZZA_MASSIMA)
            // Quello che avanza quando le caselle hanno smesso di crescere si divide in
            // due, così la griglia resta al centro invece di lasciare un vuoto sotto.
            val occupato = altezza * visibili + spazio * (visibili - 1)
            val margine = ((maxHeight - occupato) / 2).coerceAtLeast(0.dp)

            LazyVerticalGrid(
                columns = GridCells.Fixed(COLONNE),
                verticalArrangement = Arrangement.spacedBy(spazio),
                horizontalArrangement = Arrangement.spacedBy(spazio),
                contentPadding = PaddingValues(vertical = margine),
            ) {
                gridItems(categorie, key = { it.id }) { categoria ->
                    Casella(categoria, altezza) { onScelta(categoria) }
                }
                // La matita sta qui, in coda alla griglia, e non solo nelle impostazioni:
                // è guardando le proprie categorie che viene voglia di cambiarle, e chi
                // non sa che si può fare non va a cercarlo in un altro posto.
                item(key = "personalizza") { CasellaMatita(altezza, onPersonalizza) }
            }
        }
    }
}

/** Quattro colonne: con tre i nomi ci stanno, ma servono due schermate per sedici voci. */
private const val COLONNE = 4
private const val RIGHE_A_VISTA = 5
private val ALTEZZA_MINIMA = 78.dp
private val ALTEZZA_MASSIMA = 116.dp

@Composable
private fun CasellaMatita(altezza: Dp, onClick: () -> Unit) {
    val colore = MaterialTheme.colorScheme.onSurfaceVariant
    Corpo(
        altezza = altezza,
        sfondo = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        onClick = onClick,
    ) {
        Pastiglia(colore.copy(alpha = 0.16f)) {
            Icon(Icone.Matita, contentDescription = null, tint = colore, modifier = Modifier.size(19.dp))
        }
        Etichetta("Modifica", colore)
    }
}

/**
 * Una categoria come tessera piena e non come icona sospesa.
 *
 * Il fondo colorato è quello che le dà un bordo da toccare: senza, il bersaglio del dito
 * era l'icona, e fra una casella e l'altra c'era aria che sembrava cliccabile e non lo
 * era.
 */
@Composable
private fun Casella(categoria: Category, altezza: Dp, onClick: () -> Unit) {
    val colore = tinta(categoria.colorArgb)
    Corpo(altezza = altezza, sfondo = colore.copy(alpha = 0.13f), onClick = onClick) {
        Pastiglia(colore.copy(alpha = 0.22f)) {
            Icon(iconFor(categoria.icon), contentDescription = null, tint = colore, modifier = Modifier.size(21.dp))
        }
        Etichetta(categoria.name, MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Corpo(
    altezza: Dp,
    sfondo: Color,
    onClick: () -> Unit,
    contenuto: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(altezza)
            .clip(RoundedCornerShape(19.dp))
            .background(sfondo)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = contenuto,
    )
}

@Composable
private fun Pastiglia(sfondo: Color, contenuto: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(sfondo),
        contentAlignment = Alignment.Center,
        content = { contenuto() },
    )
}

@Composable
private fun Etichetta(testo: String, colore: Color) {
    Spacer(Modifier.height(7.dp))
    Text(
        testo,
        style = MaterialTheme.typography.labelSmall,
        color = colore,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 12.sp,
    )
}

@Composable
private fun PassoImporto(
    categoria: Category,
    sottocategorie: List<Category>,
    conti: List<Account>,
    onCambiaCategoria: () -> Unit,
    onSalva: (Money, String, String) -> Unit,
) {
    val colore = tinta(categoria.colorArgb)
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

        // L'importo prende tutto lo spazio che avanza e ci sta in mezzo.
        //
        // Prima lo spazio avanzato era un vuoto sotto la cifra, e la cifra restava in
        // alto: la pagina aveva un buco esattamente dove va l'occhio mentre si digita.
        // Al centro il numero è l'unica cosa fra la categoria e i tasti, che è quello
        // che sta succedendo davvero.
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            ImportoGrande(digitato)
        }

        if (conti.size > 1) {
            // I conti stanno appena sopra i tasti e non sotto la categoria: si scelgono
            // dopo aver scritto la cifra, e stando qui il pollice non attraversa lo
            // schermo per arrivarci.
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
                                if (attivo) tinta(c.colorArgb).copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { conto = c }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }

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
                color = if (abilitato) extra.onBrand else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
