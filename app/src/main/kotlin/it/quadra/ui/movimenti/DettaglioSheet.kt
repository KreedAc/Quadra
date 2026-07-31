package it.quadra.ui.movimenti

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import it.quadra.core.model.Category
import it.quadra.core.model.Money
import it.quadra.core.model.Transaction
import it.quadra.ui.Icone
import it.quadra.ui.common.Azione
import it.quadra.ui.common.CampoTesto
import it.quadra.ui.common.ChipRow
import it.quadra.ui.common.ContenutoFoglio
import it.quadra.ui.common.GrigliaFissa
import it.quadra.ui.common.ImportoGrande
import it.quadra.ui.common.Tastierino
import it.quadra.ui.iconFor
import it.quadra.ui.theme.extra
import it.quadra.ui.theme.tabular
import it.quadra.ui.theme.tinta
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatoLungo = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)

/** Cosa l'utente sta modificando in questo momento. Uno alla volta: il foglio è stretto. */
private enum class Apertura { NESSUNA, IMPORTO, CATEGORIA, CONTO, DATA }

/**
 * Il movimento aperto, con tutto quello che si può correggere.
 *
 * Sostituisce la pressione prolungata, che era l'unico modo per cancellare e non lo
 * scopriva nessuno: un gesto nascosto che fa una cosa irreversibile è il peggior
 * accoppiamento possibile. Ora un tocco apre, e la cancellazione è un pulsante fra gli
 * altri — visibile, con la sua finestra di annullamento intatta.
 *
 * Le correzioni si applicano al salvataggio e non a ogni tocco: si può cambiare idea tre
 * volte sulla categoria senza che il movimento venga riscritto tre volte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DettaglioSheet(
    movimento: Transaction,
    stato: StatoMovimenti,
    onChiudi: () -> Unit,
    onSalva: (importo: Money, categoriaId: String, contoId: String, data: LocalDate, descrizione: String, note: String) -> Unit,
    onElimina: () -> Unit,
) {
    var digitato by remember { mutableStateOf(Digitazione.da(movimento.amount)) }
    var categoria by remember { mutableStateOf(stato.categoria(movimento.categoryId)) }
    var conto by remember { mutableStateOf(stato.conto(movimento.accountId)) }
    var data by remember { mutableStateOf(movimento.date) }
    var descrizione by remember { mutableStateOf(movimento.description) }
    var note by remember { mutableStateOf(movimento.notes) }
    var apertura by remember { mutableStateOf(Apertura.NESSUNA) }

    // Un trasferimento ha due gambe su due conti: correggerne una sola le lascerebbe
    // sullo stesso conto e farebbe comparire denaro dal nulla. Qui si può solo guardarlo
    // o cancellarlo, e la cancellazione porta via entrambe le gambe.
    val trasferimento = movimento.isTransfer

    // Si sceglie fra categorie dello stesso verso: un'entrata che diventa "Ristoranti"
    // non è una correzione, è un altro movimento.
    val sceglibili = remember(stato.categorie, movimento.isIncome) {
        stato.categorie.filter { it.isIncome == movimento.isIncome && !it.hidden }
    }
    val principali = sceglibili.filter { it.isTopLevel }.sortedBy { it.sortOrder }
    val sottocategorie = categoria?.let { scelta ->
        val madre = scelta.parentId ?: scelta.id
        sceglibili.filter { it.parentId == madre }.sortedBy { it.sortOrder }
    }.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onChiudi,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        ContenutoFoglio(spazio = 12.dp) {
            Intestazione(
                movimento = movimento,
                categoria = categoria,
                icona = stato.icona(categoria?.id ?: movimento.categoryId),
                importo = digitato.importo,
            )

            if (trasferimento) {
                Text(
                    "Questo è un trasferimento fra due conti. Si corregge dalla schermata " +
                        "Conti, perché ha due lati che devono restare d'accordo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Azione("Elimina", MaterialTheme.colorScheme.error, onClick = onElimina)
                return@ContenutoFoglio
            }

            Voce("Importo", digitato.importo.abs().format(), apertura == Apertura.IMPORTO) {
                apertura = if (apertura == Apertura.IMPORTO) Apertura.NESSUNA else Apertura.IMPORTO
            }
            Pannello(apertura == Apertura.IMPORTO) {
                ImportoGrande(digitato)
                Spacer(Modifier.height(10.dp))
                Tastierino(digitato) { digitato = it }
            }

            Voce("Categoria", categoria?.name ?: "—", apertura == Apertura.CATEGORIA) {
                apertura = if (apertura == Apertura.CATEGORIA) Apertura.NESSUNA else Apertura.CATEGORIA
            }
            Pannello(apertura == Apertura.CATEGORIA) {
                val madre = categoria?.parentId ?: categoria?.id
                GrigliaFissa(voci = principali, colonne = 4, spazio = 10.dp) { voce ->
                    Casella(voce, voce.id == madre) { categoria = voce }
                }
                if (sottocategorie.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(sottocategorie, key = { it.id }) { sotto ->
                            Pillola(
                                testo = sotto.name,
                                attiva = categoria?.id == sotto.id,
                                colore = tinta(sotto.colorArgb),
                            ) { categoria = sotto }
                        }
                    }
                }
            }

            Voce("Pagato con", conto?.name ?: "—", apertura == Apertura.CONTO) {
                apertura = if (apertura == Apertura.CONTO) Apertura.NESSUNA else Apertura.CONTO
            }
            Pannello(apertura == Apertura.CONTO) {
                ChipRow(
                    voci = stato.conti,
                    scelta = conto,
                    etichetta = { it.name },
                    colore = { Color(it.colorArgb) },
                    onScelta = { conto = it },
                )
            }

            Voce("Quando", etichettaData(data), apertura == Apertura.DATA, ultima = true) {
                apertura = if (apertura == Apertura.DATA) Apertura.NESSUNA else Apertura.DATA
            }
            Pannello(apertura == Apertura.DATA) {
                SelettoreData(data) { data = it }
            }

            CampoTesto(descrizione, "Descrizione, per esempio Esselunga") { descrizione = it }
            CampoTesto(note, "Note") { note = it }

            val cambiato = digitato.importo.abs() != movimento.amount.abs() ||
                categoria?.id != movimento.categoryId ||
                conto?.id != movimento.accountId ||
                data != movimento.date ||
                descrizione != movimento.description ||
                note != movimento.notes
            val valido = digitato.valido && categoria != null && conto != null

            Azione("Salva", extra.brandEnd, cambiato && valido) {
                onSalva(digitato.importo, categoria!!.id, conto!!.id, data, descrizione, note)
            }
            Azione("Elimina", MaterialTheme.colorScheme.error, onClick = onElimina)
        }
    }
}

@Composable
private fun Intestazione(
    movimento: Transaction,
    categoria: Category?,
    icona: String?,
    importo: Money,
) {
    val colore = categoria?.let { tinta(it.colorArgb) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
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
            Icon(iconFor(icona), contentDescription = null, tint = colore, modifier = Modifier.size(22.dp))
        }
        Text(
            movimento.description.ifBlank { categoria?.name.orEmpty() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            importo.abs().format(),
            style = MaterialTheme.typography.titleLarge.tabular,
            color = if (movimento.isIncome) extra.income else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Una riga "etichetta — valore" che si apre sul pannello corrispondente. */
@Composable
private fun Voce(
    etichetta: String,
    valore: String,
    aperta: Boolean,
    ultima: Boolean = false,
    onTocca: () -> Unit,
) {
  Column {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(
                if (aperta) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent
            )
            .clickable(onClick = onTocca)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            etichetta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            valore,
            style = MaterialTheme.typography.bodyLarge.tabular,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(8.dp))
        Icon(
            if (aperta) Icone.Su else Icone.Giu,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
    // Le voci erano quattro righe separate solo dall'aria, e senza un segno l'occhio
    // non capisce dove finisce una e comincia l'altra: sembrava un elenco allentato
    // invece di quattro campi. Il filo è lo stesso dei movimenti e dei conti, e
    // sparisce quando la voce è aperta perché lì il confine lo fa già il pannello.
    if (!ultima && !aperta) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
    }
  }
}

/** Il pannello che si apre sotto una voce, con l'altezza che cresce invece di saltare. */
@Composable
private fun Pannello(visibile: Boolean, contenuto: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visibile,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column(Modifier.padding(bottom = 4.dp)) { contenuto() }
    }
}

@Composable
private fun Casella(categoria: Category, attiva: Boolean, onClick: () -> Unit) {
    val colore = tinta(categoria.colorArgb)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(colore.copy(alpha = if (attiva) 0.40f else 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconFor(categoria.icon), contentDescription = null, tint = colore, modifier = Modifier.size(21.dp))
        }
        Text(
            categoria.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (attiva) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Pillola(testo: String, attiva: Boolean, colore: Color, onClick: () -> Unit) {
    Text(
        testo,
        style = MaterialTheme.typography.bodySmall,
        color = if (attiva) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(
                if (attiva) colore.copy(alpha = 0.24f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * La data, scelta scorrendo indietro invece che da un calendario.
 *
 * Un movimento si corregge quasi sempre entro pochi giorni da quando è avvenuto: due
 * settimane di caselle coprono tutti i casi veri e costano un tocco, mentre un calendario
 * completo ne costa tre e serve a un caso su cento.
 */
@Composable
private fun SelettoreData(scelta: LocalDate, onScegli: (LocalDate) -> Unit) {
    val oggi = LocalDate.now()
    val giorni = remember(oggi, scelta) {
        val recenti = (0L..13L).map { oggi.minusDays(it) }
        // Se il movimento è più vecchio della finestra, la sua data resta raggiungibile.
        (if (scelta in recenti) recenti else recenti + scelta).sortedDescending()
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(giorni, key = { it.toString() }) { giorno ->
            val attivo = giorno == scelta
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        if (attivo) extra.brandEnd.copy(alpha = 0.20f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onScegli(giorno) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    when (giorno) {
                        oggi -> "Oggi"
                        oggi.minusDays(1) -> "Ieri"
                        else -> giorno.format(DateTimeFormatter.ofPattern("EEE", Locale.ITALIAN))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    giorno.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium.tabular,
                    color = if (attivo) extra.brandEnd else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun etichettaData(data: LocalDate): String = when (data) {
    LocalDate.now() -> "Oggi"
    LocalDate.now().minusDays(1) -> "Ieri"
    else -> data.format(formatoLungo).replaceFirstChar { it.uppercase() }
}
