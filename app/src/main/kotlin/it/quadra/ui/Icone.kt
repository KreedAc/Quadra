package it.quadra.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Le icone dell'app, disegnate a tratto.
 *
 * Sono le stesse della bozza visiva, non quelle di Material. La differenza non è
 * pignoleria: le icone di Material sono piene e pesanti, e in una lista di movimenti
 * dove ogni riga ne porta una diventano il primo elemento che l'occhio incontra —
 * rubando attenzione all'importo, che è l'unica cosa che si sta davvero cercando.
 * Un tratto sottile resta leggibile e sta al suo posto.
 *
 * Sono definite come tracciati e non come riempimenti, con lo stesso spessore ovunque:
 * è ciò che le fa sembrare parte di un unico insieme invece che raccolte a caso.
 *
 * Portarle qui dentro toglie anche la dipendenza da material-icons-extended, che pesa
 * diversi megabyte per darci una trentina di simboli.
 */

private const val TRATTO = 1.7f

private fun tratto(nome: String, vararg tracciati: String): ImageVector =
    ImageVector.Builder(
        name = nome,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        tracciati.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black), // il colore lo mette Icon() con la tinta
                strokeLineWidth = TRATTO,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private fun pieno(nome: String, vararg tracciati: String): ImageVector =
    ImageVector.Builder(
        name = nome,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        tracciati.forEach { d -> addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black)) }
    }.build()

/** Un cerchio come tracciato, perché i tracciati SVG non hanno le primitive. */
private fun cerchio(cx: Float, cy: Float, r: Float): String =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${r * 2} 0a$r $r 0 1 0 ${-r * 2} 0"

object Icone {

    // ─────────────────────────────────────────────────────── categorie

    val Carrello = tratto(
        "carrello",
        "M2.5 4h2.1l2.3 9.9a1.6 1.6 0 0 0 1.6 1.2h7.7a1.6 1.6 0 0 0 1.6-1.2L19.4 8H5.6",
        cerchio(9.2f, 19f, 1.3f),
        cerchio(16.6f, 19f, 1.3f),
    )

    val Caffe = tratto(
        "caffe",
        "M4 8.6h11.2v5.9a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4Z",
        "M15.2 9.9h2.4a2.4 2.4 0 0 1 0 4.8h-2.4",
        "M7 3.2v2.1M10.4 3.2v2.1M13.8 3.2v2.1",
    )

    val Ristorante = tratto(
        "ristorante",
        "M6.2 3.2v6.4a2.4 2.4 0 0 0 4.8 0V3.2",
        "M8.6 12v8.8",
        "M17.6 3.2c-1.7 1.2-2.6 3-2.6 5.4 0 1.9 0.9 3 2.6 3.3v9",
    )

    val Auto = tratto(
        "auto",
        "M4 13.6l1.8-4.8a2 2 0 0 1 1.9-1.4h8.6a2 2 0 0 1 1.9 1.4l1.8 4.8v4.2a1 1 0 0 1-1 1h-1.6a1 1 0 0 1-1-1v-0.8H7.6v0.8a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1Z",
        "M4 13.6h16",
        cerchio(7.6f, 15.6f, 0.9f),
        cerchio(16.4f, 15.6f, 0.9f),
    )

    val Benzina = tratto(
        "benzina",
        "M4 20.5V5.6A1.6 1.6 0 0 1 5.6 4h5.6a1.6 1.6 0 0 1 1.6 1.6v14.9",
        "M2.8 20.5h11.2M6.4 8.4h4.4",
        "M12.8 10.4h3.4a1.6 1.6 0 0 1 1.6 1.6v4.6a1.5 1.5 0 0 0 3 0V8.4l-2.4-2.4",
    )

    val Casa = tratto(
        "casa",
        "M3.6 20.6V4.6a1 1 0 0 1 1-1h7.6a1 1 0 0 1 1 1v16",
        "M13.2 10.2h5.6a1 1 0 0 1 1 1v9.4",
        "M2.4 20.6h19.2",
        "M6.6 7.4h3.4M6.6 11.2h3.4M6.6 15h3.4M16 14h1.4M16 17.2h1.4",
    )

    val Fulmine = tratto("fulmine", "M13.4 2.6 5.2 13.2h5.8l-1.4 8.2 8.2-10.6h-5.8Z")

    val Medicina = tratto(
        "medicina",
        "M6.5 17.5a4.6 4.6 0 0 1 0-6.5l4.5-4.5a4.6 4.6 0 0 1 6.5 6.5l-4.5 4.5a4.6 4.6 0 0 1-6.5 0Z",
        "M9.1 9.1l5.8 5.8",
    )

    val Borsa = tratto(
        "borsa",
        "M5.4 8h13.2l-1.1 11.4a1.6 1.6 0 0 1-1.6 1.4H8.1a1.6 1.6 0 0 1-1.6-1.4Z",
        "M9 10.2V6.8a3 3 0 0 1 6 0v3.4",
    )

    val Biglietto = tratto(
        "biglietto",
        "M3.4 9.6V7.4A1.4 1.4 0 0 1 4.8 6h14.4a1.4 1.4 0 0 1 1.4 1.4v2.2a2.5 2.5 0 0 0 0 4.8v2.2a1.4 1.4 0 0 1-1.4 1.4H4.8a1.4 1.4 0 0 1-1.4-1.4v-2.2a2.5 2.5 0 0 0 0-4.8Z",
        "M14 7.4v1.8M14 11.1v1.8M14 14.8v1.8",
    )

    val Scontrino = tratto(
        "scontrino",
        "M5.6 3.4h12.8v17.2l-2.1-1.4-2.1 1.4-2.2-1.4-2.1 1.4-2.1-1.4-2.2 1.4Z",
        "M8.8 8.2h6.4M8.8 12.2h6.4",
    )

    val Animali = tratto(
        "animali",
        "M6.4 7.1a1.9 2.5 0 1 0 0 5a1.9 2.5 0 1 0 0-5",
        "M17.6 7.1a1.9 2.5 0 1 0 0 5a1.9 2.5 0 1 0 0-5",
        "M10 3a1.8 2.4 0 1 0 0 4.8a1.8 2.4 0 1 0 0-4.8",
        "M14 3a1.8 2.4 0 1 0 0 4.8a1.8 2.4 0 1 0 0-4.8",
        "M12 13.2c2.6 0 4.6 1.9 4.6 4a2.6 2.6 0 0 1-3.5 2.4 3.4 3.4 0 0 0-2.2 0 2.6 2.6 0 0 1-3.5-2.4c0-2.1 2-4 4.6-4Z",
    )

    val Famiglia = tratto(
        "famiglia",
        cerchio(12f, 5.6f, 2.6f),
        "M12 8.2v6.4",
        "M7.4 11h9.2",
        "M9.4 20.6l2.6-6 2.6 6",
    )

    val Regalo = tratto(
        "regalo",
        "M3.6 9.6h16.8v3.2H3.6Z",
        "M5.2 12.8v6.4a1.4 1.4 0 0 0 1.4 1.4h10.8a1.4 1.4 0 0 0 1.4-1.4v-6.4",
        "M12 9.6v11",
        "M12 9.6S10.8 5 8.4 5a2.3 2.3 0 0 0 0 4.6ZM12 9.6S13.2 5 15.6 5a2.3 2.3 0 0 1 0 4.6Z",
    )

    val Puntini = pieno(
        "puntini",
        cerchio(6.2f, 12f, 1.5f),
        cerchio(12f, 12f, 1.5f),
        cerchio(17.8f, 12f, 1.5f),
    )

    // ────────────────────────────────────────────────────────── conti

    val Portafoglio = tratto(
        "portafoglio",
        "M20.4 9.2V6.8a1.6 1.6 0 0 0-1.6-1.6H4.8A1.6 1.6 0 0 0 3.2 6.8v10.4a1.6 1.6 0 0 0 1.6 1.6h14a1.6 1.6 0 0 0 1.6-1.6v-2.4",
        "M21.6 9.2h-4.4a2.4 2.4 0 0 0 0 4.8h4.4Z",
    )

    val Carta = tratto(
        "carta",
        "M3.4 5.4h17.2a2 2 0 0 1 2 2v9.2a2 2 0 0 1-2 2H3.4a2 2 0 0 1-2-2V7.4a2 2 0 0 1 2-2Z",
        "M1.4 9.8h21.2",
        "M5 14.6h3.4",
    )

    val Banca = tratto(
        "banca",
        "M3.2 9.4l8.8-5.2 8.8 5.2",
        "M4.8 9.4v8.4M10 9.4v8.4M14 9.4v8.4M19.2 9.4v8.4",
        "M2.8 20.2h18.4",
    )

    val Salvadanaio = tratto(
        "salvadanaio",
        "M3.4 12.6c0-3.6 3.5-6.2 7.8-6.2 4.3 0 7.8 2.6 7.8 6.2 0 1.7-0.8 3.2-2 4.3v2.4h-2.6l-0.7-1.4a11 11 0 0 1-5 0l-0.7 1.4H5.4v-2.4a6.4 6.4 0 0 1-1.3-2H2.8v-2.3Z",
        "M9 6.6L8.2 4",
    )

    // ───────────────────────────────────────────────────── navigazione

    val Elenco = tratto("elenco", "M4 6.4h16M4 12h16M4 17.6h11")

    val Grafico = tratto("grafico", "M5 20V11M12 20V4.6M19 20v-6.2")

    val Cursori = tratto(
        "cursori",
        "M3.4 6.6h8.2M17.2 6.6h3.4M3.4 12h3.4M12.6 12h8M3.4 17.4h8.2M17.2 17.4h3.4",
        cerchio(14.4f, 6.6f, 2.2f),
        cerchio(9.6f, 12f, 2.2f),
        cerchio(14.4f, 17.4f, 2.2f),
    )

    // ────────────────────────────────────────────────────────── azioni

    val Piu = tratto("piu", "M12 5.4v13.2M5.4 12h13.2")

    val Giu = tratto("giu", "M7.4 9.8l4.6 4.4 4.6-4.4")
    val Sinistra = tratto("sinistra", "M14.6 6.4L9.2 12l5.4 5.6")
    val Destra = tratto("destra", "M9.4 6.4L14.8 12l-5.4 5.6")

    val Cancella = tratto(
        "cancella",
        "M20 5.8H9.4L3.4 12l6 6.2H20a1.4 1.4 0 0 0 1.4-1.4V7.2A1.4 1.4 0 0 0 20 5.8Z",
        "M11.6 9.8l5 4.4M16.6 9.8l-5 4.4",
    )

    val Cestino = tratto(
        "cestino",
        "M3.8 6.4h16.4",
        "M9.4 6.4V4.6a1.2 1.2 0 0 1 1.2-1.2h2.8a1.2 1.2 0 0 1 1.2 1.2v1.8",
        "M6.2 6.4L7 19.6a1.4 1.4 0 0 0 1.4 1.3h7.2a1.4 1.4 0 0 0 1.4-1.3l0.8-13.2",
        "M10.4 10.2v6.6M13.6 10.2v6.6",
    )

    val Lucchetto = tratto(
        "lucchetto",
        "M4.8 10.2h14.4a2.2 2.2 0 0 1 2.2 2.2v5.2a2.2 2.2 0 0 1-2.2 2.2H4.8a2.2 2.2 0 0 1-2.2-2.2v-5.2a2.2 2.2 0 0 1 2.2-2.2Z",
        "M8.4 10.2V7.6a3.6 3.6 0 0 1 7.2 0v2.6",
    )

    val Matita = tratto(
        "matita",
        "M16.2 4.2a2.1 2.1 0 0 1 3 3L9 17.4l-4 1 1-4Z",
        "M14.6 5.8l3 3",
    )

    val Scambio = tratto(
        "scambio",
        "M4 8.6h13.4",
        "M14.6 5.4l3.2 3.2-3.2 3.2",
        "M20 15.4H6.6",
        "M9.4 12.2l-3.2 3.2 3.2 3.2",
    )

    val Su = tratto("su", "M12 19.4V5M6.6 10.4l5.4-5.4 5.4 5.4")

    val Spunta = tratto("spunta", "M4.8 12.6l4.8 4.6 9.6-10")

    val Chiudi = tratto("chiudi", "M6 6l12 12M18 6L6 18")
}

/**
 * Dalla chiave salvata nel database al disegno.
 *
 * Il modulo :core non conosce ImageVector: salva una stringa. La traduzione sta qui, ed
 * è l'unico punto che cambia se un domani le icone si ridisegnano.
 */
fun iconFor(key: String?): ImageVector = when (key) {
    "cart" -> Icone.Carrello
    "coffee" -> Icone.Caffe
    "restaurant" -> Icone.Ristorante
    "car" -> Icone.Auto
    "fuel" -> Icone.Benzina
    "building" -> Icone.Casa
    "bolt" -> Icone.Fulmine
    "pill" -> Icone.Medicina
    "bag" -> Icone.Borsa
    "ticket" -> Icone.Biglietto
    "receipt" -> Icone.Scontrino
    "pets" -> Icone.Animali
    "child" -> Icone.Famiglia
    "gift" -> Icone.Regalo
    "wallet" -> Icone.Portafoglio
    "credit_card" -> Icone.Carta
    "bank" -> Icone.Banca
    "savings" -> Icone.Salvadanaio
    "swap" -> Icone.Scambio
    else -> Icone.Puntini
}

/** Le chiavi disponibili quando l'utente sceglie l'icona di una categoria. */
val ICONE_SCEGLIBILI: List<String> = listOf(
    "cart", "coffee", "restaurant", "car", "fuel", "building", "bolt", "pill",
    "bag", "ticket", "receipt", "pets", "child", "gift", "wallet", "credit_card",
    "bank", "savings",
)
