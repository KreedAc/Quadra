package it.quadra.ui.ricorrenti

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.quadra.core.input.Digitazione
import it.quadra.core.model.Account
import it.quadra.core.model.Category
import it.quadra.core.model.Money
import it.quadra.core.scadenze.Scadenza
import it.quadra.ui.common.Azione
import it.quadra.ui.common.ContenutoFoglio
import it.quadra.ui.common.GrigliaFissa
import it.quadra.ui.common.ImportoGrande
import it.quadra.ui.common.Tastierino
import it.quadra.ui.iconFor
import it.quadra.ui.theme.extra
import it.quadra.ui.theme.tabular
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatoScadenza = DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN)

/** I rinvii proposti: coprono il "ripasso fra qualche giorno" senza far digitare un numero. */
private val RINVII = listOf(1L, 3L, 7L, 15L)

/**
 * La scadenza arrivata, che chiede conferma.
 *
 * Chiede invece di registrare, ed è tutta la differenza. Un pagamento previsto non è un
 * pagamento avvenuto: un abbonamento può non partire per fondi insufficienti e venire
 * addebitato due giorni dopo, un bonifico può essere rifiutato. Se l'app scrivesse il
 * movimento alla scadenza, mostrerebbe un saldo che non esiste — e chi guarda una carta
 * in negativo per un addebito mai avvenuto prende decisioni sbagliate su soldi veri.
 *
 * L'importo previsto è precompilato ma si riscrive: per l'affitto si conferma e basta,
 * per il gas si corregge, che è il caso in cui un valore fisso sarebbe sbagliato per
 * definizione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScadenzaSheet(
    scadenza: Scadenza,
    categoria: Category?,
    conto: Account?,
    quanteAncora: Int,
    onConferma: (Money) -> Unit,
    onRimanda: (Long) -> Unit,
    onSalta: () -> Unit,
    onChiudi: () -> Unit,
) {
    var digitato by remember(scadenza.chiave) {
        mutableStateOf(Digitazione.da(scadenza.regola.amount))
    }
    var rinvio by remember(scadenza.chiave) { mutableStateOf(false) }
    val colore = categoria?.let { Color(it.colorArgb) } ?: extra.brandEnd

    ModalBottomSheet(
        onDismissRequest = onChiudi,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ContenutoFoglio(spazio = 13.dp) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(colore.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        iconFor(categoria?.icon),
                        contentDescription = null,
                        tint = colore,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        scadenza.regola.description.ifBlank { categoria?.name.orEmpty() },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        scadenzaDetta(scadenza) + (conto?.let { " · ${it.name}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (scadenza.inRitardo && scadenza.giorniDiRitardo > 0)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                if (scadenza.inRitardo) "Quanto hai pagato davvero"
                else "Quanto hai pagato, se l'hai già fatto",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ImportoGrande(digitato)
            Tastierino(digitato) { digitato = it }

            Azione("Registra", extra.brandEnd, digitato.valido) {
                onConferma(digitato.importo)
            }

            if (rinvio) {
                Text(
                    "Quando te lo richiedo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GrigliaFissa(voci = RINVII, colonne = 4, spazio = 7.dp) { giorni ->
                    Text(
                        if (giorni == 1L) "Domani" else "$giorni giorni",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onRimanda(giorni) }
                            .padding(vertical = 12.dp),
                    )
                }
            } else {
                Azione("Non l'ho ancora pagato", MaterialTheme.colorScheme.onSurfaceVariant) {
                    rinvio = true
                }
            }

            Azione("Salta questa volta", MaterialTheme.colorScheme.onSurfaceVariant, onClick = onSalta)

            Text(
                if (quanteAncora > 0) {
                    "Dopo questa ne restano altre $quanteAncora. " +
                        "Finché non rispondi resta in evidenza sulla schermata dei movimenti."
                } else if (scadenza.rimandataAl != null) {
                    "L'hai rimandata: resta qui e te la richiedo il giorno che hai scelto. " +
                        "Puoi comunque registrarla adesso o rimandarla ancora."
                } else if (scadenza.giorniDiRitardo < 0) {
                    "Non è ancora scaduta: registrala solo se l'hai già pagata."
                } else {
                    "Niente viene registrato finché non lo confermi: l'app non può sapere " +
                        "se il pagamento è andato a buon fine."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
        }
    }
}

private fun scadenzaDetta(scadenza: Scadenza): String {
    // Legata a una variabile locale e non letta due volte: il valore arriva da :core, e
    // Kotlin non restringe il tipo di una proprietà pubblica di un altro modulo perché
    // non può garantire che fra il controllo e l'uso resti la stessa.
    val rinviata = scadenza.rimandataAl
    return when {
        // Il rinvio viene prima di tutto: è la risposta che l'utente ha già dato, e
        // sapere quando tornerà conta più di sapere da quanto è scaduta.
        rinviata != null -> "Te lo richiedo il ${rinviata.format(formatoScadenza)}"
        scadenza.giorniDiRitardo == 0 -> "Scade oggi"
        scadenza.giorniDiRitardo == 1 -> "Scaduta ieri"
        scadenza.giorniDiRitardo > 1 -> "Scaduta il ${scadenza.occorrenza.format(formatoScadenza)}"
        scadenza.giorniDiRitardo == -1 -> "Domani"
        else -> "In arrivo il ${scadenza.occorrenza.format(formatoScadenza)}"
    }
}

/**
 * Una scadenza come scheda, per la fila orizzontale sopra i movimenti.
 *
 * In fila e non impilate: chi ha otto fra abbonamenti, bollette e rate si ritroverebbe
 * la schermata dei movimenti spinta sotto il bordo da cose che non sono ancora
 * successe. In orizzontale ne restano due e mezza a vista, che è abbastanza per sapere
 * cosa bolle senza perdere di vista quello che si è speso davvero.
 *
 * È toccabile anche quando deve ancora arrivare: vedersela lì e non poterla registrare
 * è la stessa frustrazione di un pulsante che non risponde — e capita davvero di pagare
 * un abbonamento qualche giorno prima.
 */
@Composable
fun CardScadenza(
    scadenza: Scadenza,
    categoria: Category?,
    onClick: () -> Unit,
) {
    val inRitardo = scadenza.inRitardo
    val colore = categoria?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    val accento = if (inRitardo) MaterialTheme.colorScheme.error else colore
    Column(
        modifier = Modifier
            .width(186.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(accento.copy(alpha = if (inRitardo) 0.16f else 0.10f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accento.copy(alpha = 0.24f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    iconFor(categoria?.icon),
                    contentDescription = null,
                    tint = accento,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                scadenza.regola.description.ifBlank { categoria?.name.orEmpty() },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            scadenzaDetta(scadenza),
            style = MaterialTheme.typography.bodySmall,
            color = if (inRitardo) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            scadenza.regola.amount.abs().format(),
            style = MaterialTheme.typography.titleMedium.tabular,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
