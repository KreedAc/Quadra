# Quadra — istruzioni per chi ci lavora

App Android per le spese personali. Nessun account, nessun server, nessun permesso
dichiarato: tutto resta sul telefono. È la promessa del prodotto, e vincola le decisioni
tecniche più di qualunque preferenza di stile.

Pubblicata sul Play Store come **Quadra — Spese personali**.

## La lingua

Codice, commenti, messaggi di commit e conversazione: **italiano**. I nomi delle API di
libreria restano quelli che sono, tutto il resto è italiano.

I commenti spiegano **perché**, mai cosa. Un commento che ripete il codice è rumore; uno
che racconta la decisione — e soprattutto la strada scartata — è quello che impedisce a
qualcuno di "semplificare" fra sei mesi riaprendo un problema già chiuso.

## L'architettura, e perché è così

**`:core`** — Kotlin puro, **zero dipendenze da Android**, 172 test JVM. Ci sta tutto ciò
che produce numeri: saldi, statistiche, ricorrenze, scadenze, backup, la matematica del
tastierino.

**`:app`** — Room e Compose, deliberatamente sottile: legge, traduce, chiama la regola,
riscrive. **Se qui dentro compare un calcolo, è nel posto sbagliato.**

> **Regola da non violare mai**: in `:core` non entra un import di Android. È ciò che
> tiene la logica testabile in millisecondi senza emulatore, ed è anche l'unica cosa che
> rende pensabile un giorno una versione iOS.

### Invarianti del dominio

- `Money` è `@JvmInline value class Money(val cents: Long)`, con segno (negativo = uscita).
  **Mai un Double per il denaro.**
- **Il saldo non si memorizza mai**: è sempre `apertura + somma(movimenti)`. Un saldo
  memorizzato è un saldo che prima o poi diverge.
- Lo stesso vale per le scadenze: il loro stato **si deriva** da regole più movimenti
  esistenti. Cancellare un movimento riapre la scadenza da sé, senza codice apposta.
- Un trasferimento sono **due gambe** con lo stesso `transferGroupId`, escluse da spesa e
  entrate. In elenco si mostra solo quella in uscita, ma sotto restano due.
- Le ricorrenti **avvisano, non registrano**. Un addebito può saltare, e un saldo che
  mostra soldi mai usciti fa prendere decisioni sbagliate su soldi veri.

### Database

Room con `exportSchema = true`, **niente `fallbackToDestructiveMigration`**, migrazioni
scritte a mano. Gli schemi sono versionati in `app/schemas/` e vanno consultati per
scrivere quella nuova. Su Android 8 SQLite è 3.19: **niente `ALTER TABLE ... DROP
COLUMN`**, togliere una colonna richiede di ricreare la tabella.

La tabella `impostazioni` è una mappa chiave-valore: aggiungere una preferenza è scrivere
una chiave nuova, senza migrazione. Ci vivono il tema e la versione del tutorial vista.

## Il vincolo che conta di più

**`:app` non si compila in questo ambiente** — Google Maven non è raggiungibile. Tutto il
codice Android si scrive alla cieca e lo verifica la build dell'utente.

Da qui discende tutto il resto:

```
bash scripts/controlli.sh          # obbligatorio prima di ogni commit
```

Controlla quello che si può verificare leggendo i sorgenti: lambda finale legata a un
parametro che non è una funzione, `extra`/`MaterialTheme` letti dentro una lambda non
composable, composable privati chiamati da un altro file, smart cast che non attraversa i
moduli, parentesi sbilanciate. **Ogni controllo lì dentro nasce da un errore vero arrivato
fino alla build dell'utente**: quando ne sfugge uno nuovo, si aggiunge il controllo.

`:core` invece si compila e si testa:

```
./gradlew :core:test
```

(Il progetto radice dichiara i plugin Android; per lanciarlo qui vanno tolti
temporaneamente — vedi lo script di verifica nella cartella di lavoro della sessione.)

## Il ramo

Si lavora e si pubblica su `claude/android-expense-tracker-privacy-8123ba`.

## Pubblicazione

`applicationId` = `io.github.kreedac.quadra`, **immutabile per sempre**: cambiarlo
significa pubblicare un'altra app, senza recensioni e senza utenti. Il `namespace` dei
sorgenti resta `it.quadra`, ed è un'altra cosa.

La firma legge `keystore.properties`, ignorato da git. Senza quel file la release resta
non firmata invece di fallire.

**A ogni modifica pubblicabile**: alzare `versionCode` (deve solo crescere) e
`versionName` in `app/build.gradle.kts`, e chiudere la risposta con questo blocco:

````
## 📦 Per pubblicare

**1. Apri CMD** e vai nel progetto
```cmd
cd C:\Users\user\Quadra
```

**2. Prendi le modifiche**
```cmd
git pull origin claude/android-expense-tracker-privacy-8123ba
```

**3. Prova sul telefono** (cavo collegato, debug USB attivo)
```cmd
gradlew.bat :app:installRelease
```

**4. Genera il file da caricare**
```cmd
gradlew.bat :app:bundleRelease
```
```
C:\Users\user\Quadra\app\build\outputs\bundle\release\app-release.aab
```

**Versione**: `X.Y.Z` (`versionCode N`)

**Note di rilascio**
```
...
```
````

L'utente è su **Windows** e alterna `cmd` e PowerShell. In `cmd` è `gradlew.bat`, in
PowerShell `.\gradlew.bat`; i percorsi con spazi in PowerShell vogliono `& ` davanti.

Se sul telefono c'è la copia arrivata dal Play Store, `installRelease` fallisce con
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`: le firme non combaciano. Va disinstallata —
**esportando prima il backup dalle impostazioni dell'app**, altrimenti i dati si perdono.

Testi della scheda, risposte ai moduli e materiale grafico stanno in `docs/`. La grafica
si rigenera dai sorgenti in `docs/grafica/` con `node scatto.js`: gli screenshot grezzi
del telefono hanno rapporto 2,225 e Google ne accetta al massimo 2:1.

## Come si prendono le decisioni qui

L'utente ragiona bene sul prodotto e le sue obiezioni vanno prese sul serio: le ricorrenti
sono diventate promemoria perché ha fatto notare che un'app non può sapere se un pagamento
è andato a buon fine. Quando la sua proposta ha un difetto — mostrare solo la gamba verde
di un trasferimento lo avrebbe fatto sembrare un'entrata — si dice qual è e si propone la
variante che ottiene la stessa cosa senza il difetto.

Il **long press è bandito**: era il gesto per cancellare un movimento ed è stato tolto
perché non lo trovava nessuno. Non va reintrodotto altrove.

I colori significano sempre la stessa cosa: verde conferma o entrata, blu rimanda, rosso
toglie di mezzo. La tavolozza dell'utente passa da `tinta()`, che la adatta al tema.
