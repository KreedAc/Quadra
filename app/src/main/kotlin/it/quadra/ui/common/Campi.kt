package it.quadra.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.quadra.core.input.Digitazione
import it.quadra.core.model.Money
import it.quadra.ui.ICONE_SCEGLIBILI
import it.quadra.ui.Icone
import it.quadra.ui.iconFor
import it.quadra.ui.theme.extra
import it.quadra.ui.theme.tabular

/**
 * I pezzi di interfaccia che ricorrono nei fogli: tastierino, campo di testo, scelta
 * del colore e dell'icona.
 *
 * Stanno insieme perché devono comportarsi allo stesso modo ovunque compaiano. Un
 * tastierino che in una schermata accumula centesimi e in un'altra no è il tipo di
 * incoerenza che si nota subito col pollice e non si riesce mai a spiegare.
 */

/**
 * Il contenuto di un foglio che sale dal basso.
 *
 * Scorre. Sembra ovvio e non lo è stato: un foglio con dentro un tastierino, una griglia
 * di giorni e tre file di scelte supera l'altezza dello schermo su qualunque telefono, e
 * senza scorrimento il pulsante di salvataggio resta sotto il bordo — irraggiungibile,
 * perché trascinare verso l'alto chiude il foglio invece di scoprirlo.
 *
 * Dentro non ci devono essere componenti che scorrono in verticale a loro volta: si
 * contenderebbero lo stesso gesto. Per le griglie c'è [GrigliaFissa], che non scorre.
 */
@Composable
fun ContenutoFoglio(
    modifier: Modifier = Modifier,
    spazio: Dp = 14.dp,
    contenuto: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(spazio),
        content = contenuto,
    )
}

/**
 * Una griglia che non scorre, disposta a mano riga per riga.
 *
 * Serve dentro i fogli: una griglia pigra è anche un componente che scorre, e dentro un
 * foglio che scorre a sua volta le due cose si rubano il gesto. Con poche decine di
 * elementi — le categorie, i giorni del mese — la pigrizia non serviva comunque.
 */
@Composable
fun <T> GrigliaFissa(
    voci: List<T>,
    colonne: Int,
    modifier: Modifier = Modifier,
    spazio: Dp = 6.dp,
    contenuto: @Composable (T) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spazio)) {
        voci.chunked(colonne).forEach { riga ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spazio),
            ) {
                riga.forEach { voce ->
                    Box(Modifier.weight(1f)) { contenuto(voce) }
                }
                // L'ultima riga incompleta non deve allargare le celle che ha:
                // il posto vuoto resta vuoto e le colonne restano allineate.
                repeat(colonne - riga.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Le tinte fra cui scegliere, le stesse verificate per il tema scuro e per quello chiaro. */
val TAVOLOZZA: List<Int> = listOf(
    0xFF12A374, 0xFF8B5CF6, 0xFFE85545, 0xFF0E93AE,
    0xFFB07F0A, 0xFF3B7BE8, 0xFFDB4F92, 0xFF6E9E2F, 0xFF6B7A89,
).map { it.toInt() }

/**
 * Tastierino numerico disegnato a mano.
 *
 * I tasti scrivono il numero come lo si scriverebbe su un foglio: 2, 0 fa venti, e i
 * centesimi arrivano solo dopo la virgola. Le regole stanno in [Digitazione], nel modulo
 * :core, dove sono coperte dai test: qui restano soltanto i tasti.
 */
@Composable
fun Tastierino(
    stato: Digitazione,
    modifier: Modifier = Modifier,
    onCambia: (Digitazione) -> Unit,
) {
    val righe = listOf("123", "456", "789", ",0<")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        righe.forEach { riga ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                riga.forEach { tasto ->
                    // La virgola già premuta non ha più niente da fare: spegnerla evita
                    // di far premere un tasto che non risponde.
                    val attivo = tasto != ',' || !stato.haVirgola
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = attivo) {
                                onCambia(
                                    when (tasto) {
                                        '<' -> stato.indietro()
                                        ',' -> stato.virgola()
                                        else -> stato.cifra(tasto)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (tasto == '<') {
                            Icon(
                                Icone.Cancella,
                                contentDescription = "Cancella",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(21.dp),
                            )
                        } else {
                            Text(
                                tasto.toString(),
                                style = MaterialTheme.typography.headlineSmall.tabular,
                                color = if (attivo) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * L'importo in composizione, grande e centrato.
 *
 * Mostra i tasti premuti e non l'importo formattato: appena si preme la virgola si deve
 * leggere "20,", perché "20,00" farebbe sembrare i centesimi già scritti.
 */
@Composable
fun ImportoGrande(stato: Digitazione, modifier: Modifier = Modifier) {
    ImportoGrande("${stato.testo()} €", modifier)
}

@Composable
fun ImportoGrande(importo: Money, modifier: Modifier = Modifier) {
    ImportoGrande(importo.format(), modifier)
}

@Composable
private fun ImportoGrande(testo: String, modifier: Modifier) {
    Text(
        testo,
        style = MaterialTheme.typography.displaySmall.tabular,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun CampoTesto(
    valore: String,
    segnaposto: String,
    modifier: Modifier = Modifier,
    // Ultimo, così le chiamate possono scriverlo come lambda finale:
    // CampoTesto(nome, "Nome") { nome = it }
    onCambia: (String) -> Unit,
) {
    BasicTextField(
        value = valore,
        onValueChange = onCambia,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(extra.brandEnd),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        decorationBox = { campo ->
            if (valore.isEmpty()) {
                Text(
                    segnaposto,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            campo()
        },
    )
}

@Composable
fun SceltaColore(scelto: Int, onScelta: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(TAVOLOZZA) { colore ->
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(colore))
                    .clickable { onScelta(colore) },
                contentAlignment = Alignment.Center,
            ) {
                if (colore == scelto) {
                    Icon(
                        Icone.Spunta,
                        contentDescription = "Scelto",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SceltaIcona(scelta: String?, colore: Color, onScelta: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        items(ICONE_SCEGLIBILI) { chiave ->
            val attiva = chiave == scelta
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        if (attiva) colore.copy(alpha = 0.28f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onScelta(chiave) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    iconFor(chiave),
                    contentDescription = null,
                    tint = if (attiva) colore else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/** Pulsante pieno, con il colore che ne dichiara la conseguenza. */
@Composable
fun Azione(
    testo: String,
    colore: Color,
    abilitata: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        testo,
        style = MaterialTheme.typography.titleMedium,
        color = if (abilitata) colore else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (abilitata) colore.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(enabled = abilitata, onClick = onClick)
            .padding(vertical = 15.dp),
    )
}

/** Riga di chip a scelta singola: conti, sottocategorie, qualunque elenco corto. */
@Composable
fun <T> ChipRow(
    voci: List<T>,
    scelta: T?,
    etichetta: (T) -> String,
    colore: (T) -> Color,
    onScelta: (T) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(voci) { voce ->
            val attiva = voce == scelta
            Text(
                etichetta(voce),
                style = MaterialTheme.typography.bodySmall,
                color = if (attiva) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        if (attiva) colore(voce).copy(alpha = 0.24f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onScelta(voce) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
