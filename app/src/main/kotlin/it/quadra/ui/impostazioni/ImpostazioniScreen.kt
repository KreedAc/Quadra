package it.quadra.ui.impostazioni

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.quadra.BuildConfig
import it.quadra.ui.Icone
import it.quadra.ui.theme.Tema
import it.quadra.ui.theme.extra

/**
 * Impostazioni.
 *
 * Contiene le due cose che l'utente cerca qui: la dichiarazione sulla privacy — che non è
 * riempitivo, è la promessa del prodotto scritta dove la si va a controllare — e il
 * backup, che è ciò che rende la promessa sostenibile. Senza dati sul nostro server,
 * l'unico modo per non perdere mesi di spese cambiando telefono è che il file sia in mano
 * all'utente.
 */
@Composable
fun ImpostazioniScreen(
    viewModel: ImpostazioniViewModel,
    snackbar: SnackbarHostState,
    onApriCategorie: () -> Unit,
    onApriRicorrenti: () -> Unit,
    onApriTutorial: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val messaggio by viewModel.messaggio.collectAsStateWithLifecycle()
    val tema by viewModel.tema.collectAsStateWithLifecycle()
    var chiedeConferma by remember { mutableStateOf(false) }

    val salva = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { destinazione -> destinazione?.let { viewModel.esporta(context, it) } }

    val apri = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { origine -> origine?.let { viewModel.ripristina(context, it) } }

    LaunchedEffect(messaggio) {
        messaggio?.let {
            // Un errore va letto: resta più a lungo e si può chiudere a mano.
            snackbar.showSnackbar(
                message = it.testo,
                withDismissAction = it.errore,
                duration = if (it.errore) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            viewModel.messaggioLetto()
        }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text(
                "Impostazioni",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item {
            Riquadro {
                // In cima e non in fondo: chi ha saltato la presentazione al primo
                // avvio la cerca qui, e la cerca subito.
                VoceImpostazione(
                    icona = Icone.Lucchetto,
                    titolo = "Come funziona Quadra",
                    sottotitolo = "Le quattro cose che conviene sapere, in un minuto",
                    onClick = onApriTutorial,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                VoceImpostazione(
                    icona = Icone.Elenco,
                    titolo = "Categorie",
                    sottotitolo = "Rinomina, ricolora, aggiungi e togli quello che vuoi",
                    onClick = onApriCategorie,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                VoceImpostazione(
                    icona = Icone.Scambio,
                    titolo = "Spese ricorrenti",
                    sottotitolo = "Affitto, bollette, abbonamenti: te le ricorda alla scadenza",
                    onClick = onApriRicorrenti,
                )
            }
        }

        item { Aspetto(tema, viewModel::impostaTema) }

        item {
            Riquadro {
                VoceImpostazione(
                    icona = Icone.Su,
                    titolo = "Esporta backup",
                    sottotitolo = "Salva tutto in un file: scegli tu dove, anche sul tuo cloud",
                    onClick = { salva.launch(viewModel.nomeFileProposto()) },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                VoceImpostazione(
                    icona = Icone.Giu,
                    titolo = "Ripristina backup",
                    sottotitolo = "Rimette i dati di un file salvato al posto di quelli attuali",
                    onClick = { chiedeConferma = true },
                )
            }
        }

        item {
            SchedaTesto(
                titolo = "Il backup è tuo",
                righe = listOf(
                    "Il file è un JSON leggibile: puoi aprirlo e vedere con i tuoi occhi " +
                        "che contiene soltanto le tue spese.",
                    "Quadra non lo carica da nessuna parte. Se lo vuoi al sicuro, salvalo " +
                        "dove salvi le altre cose: Drive, OneDrive, una chiavetta.",
                ),
            )
        }

        item {
            SchedaTesto(
                titolo = "I tuoi dati restano qui",
                righe = listOf(
                    "Quadra non ha registrazione, non ha account e non ha un server. " +
                        "Non esiste nessun posto nostro dove i tuoi dati possano " +
                        "arrivare, perché non esiste nessun posto nostro.",
                    "L'app non chiede nemmeno un permesso: né rete, né notifiche, né " +
                        "contatti. Puoi verificarlo tu stesso nelle informazioni " +
                        "dell'app, alla voce autorizzazioni.",
                ),
            )
        }

        item {
            // Detto qui e non taciuto. Il backup automatico di Android è una rete di
            // sicurezza vera — ti fa ritrovare le spese cambiando telefono senza aver
            // fatto niente — ma è anche l'unico caso in cui l'archivio lascia il
            // telefono. Scrivere "non esiste nessun posto dove potrebbe arrivare" e poi
            // lasciarlo acceso sarebbe stato falso, e su un'app che vende esattamente
            // questo, una riga falsa vale più di tutte le altre messe insieme.
            SchedaTesto(
                titolo = "Una cosa che non dipende da noi",
                righe = listOf(
                    "Android ha un backup automatico suo, e Quadra lo lascia acceso: il " +
                        "sistema può copiare l'archivio nel tuo account Google, cifrato " +
                        "con il codice di blocco del telefono. È quello che ti fa " +
                        "ritrovare tutto quando cambi telefono senza aver fatto niente.",
                    "Non passa da noi e non possiamo leggerlo. Ma è l'unico caso in cui " +
                        "i tuoi dati escono da qui, quindi è giusto che tu lo sappia: si " +
                        "spegne dalle impostazioni di Android, alla voce backup.",
                ),
            )
        }

        if (DONAZIONE.isNotBlank()) {
            item { Donazione() }
        }

        item {
            Text(
                // Letta dalla build e non scritta a mano: era ferma alla 0.1.0 mentre
                // l'app era andata avanti, ed è il tipo di bugia che nessuno rilegge.
                "Quadra ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { Spacer(Modifier.height(96.dp)) }
    }

    if (chiedeConferma) {
        AlertDialog(
            onDismissRequest = { chiedeConferma = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Ripristinare il backup?") },
            text = {
                // Un ripristino sostituisce, non unisce: dirlo prima evita di scoprirlo
                // dopo, quando i movimenti di oggi sono già spariti.
                Text(
                    "I conti, le categorie e i movimenti che hai adesso vengono " +
                        "sostituiti da quelli del file. Se hai registrato qualcosa dopo " +
                        "aver fatto il backup, esporta prima quello attuale."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    chiedeConferma = false
                    // Molti provider non dichiarano application/json per i file salvati
                    // sul cloud: filtrare su quel tipo li renderebbe non selezionabili.
                    // Un file sbagliato viene comunque riconosciuto e rifiutato.
                    apri.launch(arrayOf("*/*"))
                }) {
                    Text("Scegli il file", color = extra.brandEnd)
                }
            },
            dismissButton = {
                TextButton(onClick = { chiedeConferma = false }) {
                    Text("Annulla", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

/**
 * Chiaro, scuro, o quello che dice il telefono.
 *
 * Le tre voci stanno tutte in vista invece che dentro un menù a tendina: sono tre, la
 * scelta si fa una volta sola nella vita dell'app, e vederle tutte insieme risparmia
 * il tocco che serve ad aprire per scoprire cosa c'è dentro.
 *
 * "Sistema" è la prima e resta il valore predefinito: chi ha già detto ad Android cosa
 * preferisce non deve ripeterlo a ogni app che installa.
 */
@Composable
private fun Aspetto(scelto: Tema, onScelta: (Tema) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icone.Luna,
                contentDescription = null,
                tint = extra.brandEnd,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(16.dp))
            Column {
                Text(
                    "Aspetto",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Il tema scuro resta quello di casa, ma di giorno decidi tu",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tema.entries.forEach { voce ->
                val attivo = voce == scelto
                Text(
                    voce.etichetta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (attivo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (attivo) extra.brandEnd.copy(alpha = 0.20f) else Color.Transparent
                        )
                        .clickable { onScelta(voce) }
                        .padding(vertical = 11.dp),
                )
            }
        }
    }
}

/**
 * Dove va chi vuole offrire un caffè.
 *
 * Vuoto di proposito: finché non c'è un indirizzo vero il riquadro non compare, così non
 * si rischia di pubblicare un pulsante che porta a una pagina che non esiste. Basta
 * incollare qui un link PayPal.me, Ko-fi o simile.
 *
 * Deve restare una donazione e nient'altro. Nel momento in cui dà qualcosa in cambio —
 * una funzione in più, la pubblicità tolta, un distintivo — smette di essere una
 * donazione e diventa un acquisto, che Google obbliga a far passare dal suo sistema di
 * pagamento. E soprattutto tradirebbe il patto: l'app è intera per tutti.
 */
private val DONAZIONE: String = ""

/**
 * Il pulsante apre il browser, non un pagamento.
 *
 * È la ragione per cui l'app continua a non dichiarare nessun permesso: la rete la fa il
 * browser, che è un'altra applicazione. Se il pagamento avvenisse qui dentro servirebbe
 * `INTERNET`, e la riga "non chiede nemmeno un permesso" diventerebbe falsa — cioè
 * costerebbe molto più di quanto qualunque donazione possa rendere.
 */
@Composable
private fun Donazione() {
    val contesto = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable {
                // Un telefono senza browser è raro ma esiste, e un'app che si chiude
                // toccando "offri un caffè" è il peggior modo di chiedere qualcosa.
                runCatching {
                    contesto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DONAZIONE)))
                }
            }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icone.Caffe,
                contentDescription = null,
                tint = extra.income,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(16.dp))
            Text(
                "Offri un caffè",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icone.Destra,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            "Quadra è gratis e resta gratis: non c'è niente da sbloccare e non ci sarà " +
                "mai. Se ti è utile e ti va, si apre il browser e decidi tu.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Riquadro(contenuto: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) { contenuto() }
}

@Composable
private fun VoceImpostazione(
    icona: ImageVector,
    titolo: String,
    sottotitolo: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icona,
            contentDescription = null,
            tint = extra.brandEnd,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                titolo,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                sottotitolo,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        Icon(
            Icone.Destra,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SchedaTesto(titolo: String, righe: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            titolo,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        righe.forEach {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
