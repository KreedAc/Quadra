package it.quadra.ui.impostazioni

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.quadra.ui.Icone
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val messaggio by viewModel.messaggio.collectAsStateWithLifecycle()
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
                        "Tutto quello che scrivi resta sul telefono, e non esiste nessun " +
                        "posto dove potrebbe arrivare.",
                    "L'app non chiede nemmeno un permesso: né rete, né notifiche, né " +
                        "contatti. Puoi verificarlo tu stesso nelle informazioni " +
                        "dell'app, alla voce autorizzazioni.",
                ),
            )
        }

        item {
            SchedaTesto(
                titolo = "In arrivo",
                righe = listOf(
                    "Modifica dei movimenti e importazione dei movimenti dalla banca.",
                ),
            )
        }

        item {
            Text(
                "Quadra 0.1.0 — versione di sviluppo",
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
