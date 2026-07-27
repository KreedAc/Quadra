# Come compilare Quadra

Il modulo `:core` è stato scritto e testato in un ambiente senza accesso a Google Maven,
quindi 74 test girano davvero. Il modulo `:app` invece **non è mai stato compilato**: è
scritto con attenzione ma va sgrezzato al primo sync. Questo documento dice cosa fare e
cosa aspettarsi.

## 1. Scarica il progetto

```bash
git clone <url-del-repo>
cd Quadra
git checkout claude/android-expense-tracker-privacy-8123ba
```

## 2. Apri in Android Studio

`File → Open`, scegli la cartella `Quadra` (quella con `settings.gradle.kts`, non una
sottocartella). Lascia partire il Gradle Sync.

## 3. Il primo sync quasi certamente si lamenta

Ed è previsto. **Quattro versioni non ho potuto verificarle**, perché vivono solo su Google
Maven che nel mio ambiente è bloccato. Stanno tutte in un file solo,
`gradle/libs.versions.toml`, nella sezione "Da confermare al primo sync":

```toml
agp = "8.13.0"
composeBom = "2025.09.00"
room = "2.7.2"
activityCompose = "1.10.1"
lifecycle = "2.9.0"
navigationCompose = "2.9.0"
coreKtx = "1.16.0"
```

Se Android Studio dice qualcosa come *"Plugin [id: 'com.android.application', version:
'8.13.0'] was not found"*, la versione non esiste. La correzione è una riga in quel file.

Come trovare quella giusta, in ordine di comodità:

- Android Studio → `Tools → AGP Upgrade Assistant`, che propone la versione giusta per la
  tua installazione.
- Oppure `Help → About` ti dice la versione di Studio; ogni versione di Studio dichiara
  l'AGP massimo supportato nelle sue note di rilascio.
- Oppure lascia che sia Studio a proporlo: il messaggio d'errore del sync spesso elenca
  le versioni disponibili.

**Non toccare `kotlin` e `ksp`.** Quelle due sono verificate e vanno d'accordo fra loro
(la parte prima del trattino in `ksp` deve sempre combaciare con `kotlin`). Se le cambi,
vanno cambiate insieme.

## 4. Cosa mi serve da te

Copiami **il testo dell'errore**, non lo screenshot — così posso cercarci dentro.
In particolare:

- gli errori di **sync** (finestra Build, dopo il tentativo di sincronizzazione);
- gli errori di **compilazione** (`Build → Make Project`), che saranno del tipo
  `e: file.kt:12:34 Unresolved reference: qualcosa`.

Vanno bene tutti insieme, anche cento righe. La maggior parte saranno import da sistemare
o firme di API leggermente diverse nella versione di Compose che scarichi tu, e si
sistemano in blocco.

## 5. Cosa dovresti vedere quando compila

La prima fetta verticale, non l'app intera:

- **Schermata movimenti**: mese corrente, totale speso, elenco raggruppato per giorno.
  Al primo avvio è vuota, con l'invito a registrare la prima spesa.
- **Pulsante +** in basso a destra, col gradiente blu-verde.
- **Inserimento in due tocchi**: griglia di quindici categorie a posizioni fisse, poi
  tastierino e sottocategorie della categoria scelta. Le cifre si accumulano in centesimi
  come sui bancomat: digiti `1250` e leggi `12,50 €`.
- **Tenere premuto** su un movimento lo elimina, con la barra "ANNULLA" per qualche
  secondo.
- Al primo avvio il database si popola da solo con due conti (Contanti e Carta) e tutte
  le categorie italiane.

## 6. Cosa non c'è ancora

Schermata conti, statistiche, ricorrenti, trasferimenti, allineamento del saldo, modifica
di un movimento. **La logica di tutte queste cose è già scritta e testata in `:core`** —
manca solo l'interfaccia. Le aggiungo appena il progetto compila, perché scrivere altre
schermate prima di sapere che quelle esistenti funzionano significherebbe moltiplicare
gli errori invece di risolverli.

## 7. Se vuoi provare senza telefono

`Tools → Device Manager → Create Device`, un Pixel qualsiasi con API 34 o superiore. Il
minSdk è 26, quindi va bene praticamente qualunque immagine.
