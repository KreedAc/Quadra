package it.quadra.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.quadra.ui.Icone
import it.quadra.ui.theme.extra
import it.quadra.ui.theme.tinta
import kotlinx.coroutines.launch

/**
 * Cosa sa l'app di aver già raccontato.
 *
 * Salvato come numero e non come sì/no: se un domani si aggiunge una scheda, si alza
 * [VERSIONE] e la sequenza ricompare a chi ha visto solo la versione precedente, senza
 * ricomparire a chi l'ha già vista tutta.
 *
 * Sta nella tabella delle preferenze, quindi viaggia col backup: imparare com'è fatta
 * l'app è una cosa della persona, non del telefono, e chi cambia dispositivo non deve
 * ripetere la lezione.
 */
object Tutorial {
    const val CHIAVE = "tutorial"
    const val VERSIONE = 1
}

private data class Scheda(
    val icona: ImageVector,
    val colore: Color,
    val titolo: String,
    val testo: String,
)

/**
 * Le quattro cose che non si scoprono da soli.
 *
 * Non è un giro guidato dell'interfaccia: dove stanno i pulsanti si vede guardando, e
 * spiegarlo annoia. Qui c'è solo quello che l'app fa in modo diverso da come uno se lo
 * aspetta — le operazioni nascoste nel tastierino, il tocco che apre un movimento al
 * posto della pressione lunga, e soprattutto le ricorrenti che avvisano invece di
 * pagare, che è la scelta più facile da fraintendere per chi arriva da altre app.
 *
 * Quattro e non otto: la quinta scheda è quella che fa premere "Salta", e chi salta non
 * legge nemmeno le prime.
 */
@Composable
private fun schede(): List<Scheda> = listOf(
    Scheda(
        icona = Icone.Lucchetto,
        colore = extra.brandEnd,
        titolo = "I tuoi soldi, e basta",
        testo = "Quadra non ha registrazione, non ha account e non ha un server. " +
            "Quello che scrivi resta sul telefono, e il backup te lo porti via tu " +
            "quando vuoi, in un file che puoi aprire e leggere.",
    ),
    Scheda(
        icona = Icone.Carrello,
        colore = extra.income,
        titolo = "Una spesa in due tocchi",
        testo = "Scegli dove è andata, scrivi quanto. E se al bar hai pagato in una " +
            "volta tre cose diverse, il tastierino fa i conti: 4,50 + 1,40 + 2,40, " +
            "senza farli a mente in fila alla cassa.",
    ),
    Scheda(
        icona = Icone.Matita,
        colore = extra.brandStart,
        titolo = "Sbagliato? Toccalo",
        testo = "Un tocco su un movimento lo apre: cambi categoria, importo, conto, " +
            "aggiungi una nota o lo cancelli. Niente di quello che scrivi è " +
            "scolpito nella pietra.",
    ),
    Scheda(
        icona = Icone.Scambio,
        colore = tinta(Color(0xFF8B5CF6)),
        titolo = "Le ricorrenti avvisano, non pagano",
        testo = "Affitto, bollette, abbonamenti: al momento giusto te li ricordo e sei " +
            "tu a dirmi quanto hai pagato davvero. Non registro niente al posto " +
            "tuo, perché un addebito può saltare — e un saldo che mostra soldi mai " +
            "usciti fa prendere decisioni sbagliate su soldi veri.",
    ),
)

@Composable
fun TutorialScreen(onFine: () -> Unit, modifier: Modifier = Modifier) {
    val voci = schede()
    val pagina = rememberPagerState(pageCount = { voci.size })
    val scope = rememberCoroutineScope()
    val ultima = pagina.currentPage == voci.lastIndex

    // Indietro torna alla scheda precedente invece di chiudere: è lo stesso passo del
    // dito, al contrario. Dalla prima, chiude — ed è comunque un'uscita, quindi vale
    // come "ho finito" e non va riproposto al prossimo avvio.
    BackHandler {
        if (pagina.currentPage > 0) {
            scope.launch { pagina.animateScrollToPage(pagina.currentPage - 1) }
        } else {
            onFine()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 26.dp),
    ) {
        // "Salta" resta sempre in vista, anche sull'ultima scheda. Un'uscita che
        // scompare mentre leggi è il modo più veloce per far sentire in trappola.
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
            Text(
                "Salta",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .clickable(onClick = onFine)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        HorizontalPager(state = pagina, modifier = Modifier.weight(1f)) { indice ->
            val voce = voci[indice]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(voce.colore.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        voce.icona,
                        contentDescription = null,
                        tint = voce.colore,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Spacer(Modifier.height(34.dp))
                Text(
                    voce.titolo,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    voce.testo,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            voci.indices.forEach { indice ->
                val attivo = indice == pagina.currentPage
                // Il puntino attivo si allunga invece di cambiare colore soltanto: la
                // forma si legge con la coda dell'occhio, il colore no.
                val larghezza by animateDpAsState(if (attivo) 22.dp else 7.dp, label = "puntino")
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(7.dp)
                        .width(larghezza)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (attivo) extra.brandEnd
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        ),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(extra.brand)
                .clickable {
                    if (ultima) onFine()
                    else scope.launch { pagina.animateScrollToPage(pagina.currentPage + 1) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (ultima) "Cominciamo" else "Avanti",
                style = MaterialTheme.typography.titleMedium,
                color = extra.onBrand,
            )
        }
        Spacer(Modifier.height(26.dp))
    }
}
