# LeMieSpese

App Android per la gestione delle spese personali, con una regola sola: **i dati non
escono dal telefono.** Nessuna registrazione, nessun account, nessun server nostro,
nessun collegamento al conto bancario. Non raccogliamo niente perché non c'è niente
da raccogliere: non esiste un posto dove i dati potrebbero arrivare.

## Stato

In costruzione. Il modulo `:core` esiste, compila ed è coperto da test.
Il modulo `:app` (Android) non è ancora stato scritto.

## Architettura

Il progetto è diviso in due strati, e la divisione è deliberata.

**`:core` — Kotlin puro, zero dipendenze da Android.**
Modello di dominio, aritmetica del denaro, motore delle ricorrenze, import, formato di
backup, statistiche. Gira e si testa nella JVM in millisecondi, senza emulatore.
È la parte dove stanno le regole che, se sbagliate, producono numeri sbagliati — quindi
è la parte che deve essere sotto test.

**`:app` — Android.**
Persistenza (Room), interfaccia (Compose), I/O di sistema. Deve restare sottile:
se una regola di calcolo finisce qui, è nel posto sbagliato.

### Decisioni prese e perché

**Il denaro è un `Long` di centesimi, mai un `Double`.** In virgola mobile 0,1 + 0,2 non
fa 0,3. In un'app di spese l'errore diventa visibile all'utente dopo poche decine di
movimenti, ed è il tipo di bug che distrugge la fiducia nei numeri.

**Gli importi sono firmati:** negativo uscita, positivo entrata. Sommare una lista di
movimenti è una somma, senza casi particolari e senza segni dimenticati in qualche ramo.

**Gli identificativi sono UUID, non interi autoincrementali.** I dati attraversano backup
e ripristini su dispositivi diversi: servono id che restino validi fuori dal database
che li ha generati.

**Ogni movimento porta un campo `source`** (manuale, ricorrente, import CSV, notifica).
Costa nulla oggi ed evita una migrazione dello schema il giorno in cui si aggiunge
un'origine nuova.

**Ogni movimento porta uno `status`** (in attesa / definitivo). Alla pompa di benzina la
preautorizzazione blocca un importo diverso dall'addebito reale, e lo stesso vale per
hotel e noleggi. Trattare l'autorizzazione come definitiva significa mostrare totali
sbagliati proprio nel caso più frequente.

**Le categorie predefinite sono tarate sull'Italia:** condominio, bollette, bollo auto,
IMU, TARI. Se l'utente le trova già pronte, l'app sembra pensata per lui. Non è
localizzazione, è il posizionamento del prodotto.

**Le categorie stanno su due livelli, ordinati per frequenza e non per parentela.** Il
primo livello è una dozzina di voci disposte in una griglia fissa che sta tutta su una
schermata; il secondo è il dettaglio, che resta a un tocco perché la scelta precedente
lo ha già filtrato. È la decisione da cui dipende la velocità di inserimento: un elenco
piatto di quaranta voci che scorre obbliga a *leggere* ogni volta per trovare quella
giusta, mentre una griglia ferma si impara col pollice in una settimana. La velocità non
viene dai tocchi risparmiati, viene dal non dover cercare. Per questo il test sul primo
livello fallisce se le voci superano dodici.

### Fuori perimetro per la v1

**Cattura automatica dalle notifiche.** Era l'idea di partenza, è stata scartata: il
permesso di accesso alle notifiche è delicato in fase di revisione su Google Play, il
parsing del testo delle notifiche bancarie è fragile e si rompe quando una banca cambia
formato, e — soprattutto — non è affatto garantito che le notifiche delle banche
italiane contengano importo ed esercente, perché molte sono volutamente generiche.

Rinunciarci rende la promessa sulla privacy assoluta invece che sfumata: l'app non
chiede il permesso di leggere le notifiche, punto. L'enum `TransactionSource.NOTIFICATION`
resta dichiarata per non dover migrare lo schema se un domani la si volesse riprendere.

Al suo posto, per coprire lo stesso bisogno con un rischio molto minore:
spese ricorrenti generate automaticamente, import CSV dall'home banking (formato più
stabile del testo di una notifica), e inserimento manuale reso molto veloce.

## Backup

Progettato dal primo giorno, non aggiunto dopo: senza, l'utente perde tutto quando
cambia telefono. Tre livelli, in ordine di importanza.

1. **Export e import manuale** — l'utente preme "esporta", sceglie la destinazione dal
   selettore di file di sistema (Drive, Dropbox, memoria locale, chiavetta) e l'app
   scrive un file. Nessun permesso, nessun login, nessun OAuth. Il file è suo e va dove
   decide lui. Formato JSON versionato dentro uno zip, così il backup non è legato allo
   schema interno del database ed è ispezionabile a mano da chi vuole verificare cosa
   contiene.
2. **Backup automatico di Android** — attivo, gratuito, zero codice oltre alla
   configurazione. È una rete di sicurezza, non la difesa principale: non è lanciabile
   dall'utente, il ripristino avviene solo al momento dell'installazione, e i file
   collaterali di SQLite in modalità WAL richiedono attenzione per non ripristinare un
   database incoerente.
3. **Integrazione diretta con Google Drive** — backup automatico e schedulato nella
   cartella privata dell'app. Richiede il login Google, ma usa lo scope `drive.appdata`
   che Google classifica come non-sensitive: niente verifica costosa né security
   assessment di terze parti. Candidata naturale per l'acquisto una-tantum.

Il backup **manuale resta gratuito in ogni caso.** Mettere a pagamento l'unico modo di
non perdere i dati sarebbe esattamente quel riscatto di funzioni essenziali che il
modello di business vuole evitare.

## Ambiente di build

⚠️ **Questo progetto non è compilabile per intero in un ambiente senza accesso a
`dl.google.com` / `maven.google.com`.** Lì vivono l'SDK Android, l'Android Gradle Plugin
e tutti gli artefatti AndroidX, Compose e Room inclusi; su Maven Central non ci sono.

Il modulo `:core` è stato scritto proprio per non avere questo vincolo: dipende solo da
Maven Central e si compila e testa ovunque ci sia un JDK.

Per lavorare sul modulo `:app` serve una macchina con Android Studio e accesso a Google
Maven. In quel caso vanno riabilitate le righe `google()` in `settings.gradle.kts`.

### Requisiti

- JDK 21 (il bytecode prodotto è Java 17, per compatibilità con l'Android Gradle Plugin)
- Android Studio, solo per il modulo `:app`

### Comandi

```bash
./gradlew :core:test    # esegue i test del nucleo
./gradlew :core:build   # compila il nucleo
```

## Licenza

Da definire. L'ipotesi è GPLv3: un repository pubblico è la prova verificabile che
l'app fa quello che dichiara, ed è l'argomento più forte verso un pubblico che per
definizione non si fida delle promesse sulla privacy.
