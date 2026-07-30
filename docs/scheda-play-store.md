# Testi per la scheda del Play Store

Da copiare e incollare nella Console. I limiti di caratteri sono quelli di Google.

---

## Nome dell'app (max 30)

```
Quadra — Spese personali
```

24 caratteri. Non è solo estetica: "Quadra" da solo è un nome usato da altri in Italia,
software compreso, e qualificarlo con il proprio settore merceologico è il modo in cui la
convivenza fra segni omonimi funziona in pratica. Non va abbreviato in "Quadra" e basta.

Sotto l'icona sul telefono resta invece **Quadra** e basta, perché è il nome dell'app nel
lanciatore e i nomi lunghi vengono troncati con i puntini. Sono due campi diversi: questo
sta nella Console, quello sta in `app/src/main/res/values/strings.xml`.

## Nome dello sviluppatore

```
Quadra Lab
```

Campo separato, compare sotto ogni app pubblicata dall'account. È il posto giusto per
"Quadra Lab": identifica chi fa le app, non un prodotto in una categoria affollata, e te
lo porti sui progetti futuri.

## Descrizione breve (max 80)

Compare sotto il nome nei risultati di ricerca. È la riga che decide se uno tocca o
scorre, quindi dice la cosa che distingue e non quella che fanno tutti.

```
Le tue spese sul telefono. Niente account, niente server, nemmeno un permesso.
```

## Descrizione completa (max 4000)

```
Quadra è un'app per tenere le spese che non ti chiede niente.

Nessuna registrazione, nessun account, nessun server. Quello che scrivi resta sul tuo
telefono, e non c'è nessun posto nostro dove potrebbe arrivare — perché non esiste
nessun posto nostro.

L'app non dichiara nemmeno un permesso Android: né rete, né notifiche, né contatti, né
posizione. Non devi crederci sulla parola, puoi verificarlo tu stesso nelle
autorizzazioni dell'app.


SEGNARE UNA SPESA IN DUE TOCCHI

Scegli dove è andata da una griglia che sta sempre nello stesso posto, scrivi quanto, e
hai finito. Dopo una settimana il pollice ci arriva senza leggere.

Il tastierino fa i conti. Se al bar hai pagato in una volta il caffè, il cornetto e la
spremuta, scrivi 4,50 + 1,40 + 2,40 invece di farli a mente in fila alla cassa.


I CONTI COME SONO DAVVERO

Contanti, carte, conti online: ognuno con il suo saldo, ricalcolato a ogni movimento e
mai memorizzato, quindi sempre giusto.

I conti vincolati restano separati. I soldi con una destinazione — un fondo accantonato,
una carta per un sussidio — esistono e si vedono, ma non vengono sommati a quello che
puoi davvero spendere.

I trasferimenti fra conti non sono spese, e non inquinano le statistiche.


LE RICORRENTI TI AVVISANO, NON PAGANO AL POSTO TUO

Affitto, bollette, abbonamenti, l'assicurazione ogni quattro mesi: imposti quando
scadono e Quadra te le ricorda al momento giusto. Sei tu a dire quanto hai pagato
davvero, e solo allora il movimento viene registrato.

È una scelta, non una mancanza. Un addebito può saltare per fondi insufficienti e
arrivare due giorni dopo: un'app che scrive il pagamento da sola ti mostra un saldo che
non esiste, e un saldo sbagliato fa prendere decisioni sbagliate su soldi veri.

Se non l'hai ancora pagata, rimandi il promemoria di qualche giorno. Se quel mese non
c'era, la salti.


STATISTICHE CHE SI LEGGONO

Quanto hai speso questo mese e quanto ti resta davvero. Le entrate del mese con il loro
totale. L'andamento degli ultimi sei mesi. La spesa per categoria, ordinata.


IL BACKUP È TUO

Esporti tutto in un file JSON che puoi aprire e leggere con i tuoi occhi. Scegli tu dove
metterlo con il selettore di sistema: la memoria del telefono, una chiavetta, il tuo
cloud. Quadra non lo carica da nessuna parte, e per rimetterlo su un altro telefono
basta ripristinarlo.


PENSATA PER L'ITALIA

Importi in euro con la virgola dei decimali, date nel formato italiano, categorie di
partenza tarate su come si spende qui. E tutto in italiano, non tradotto a metà.

Tema chiaro e tema scuro, o quello che dice il telefono.


Quadra è gratis. Non ci sono funzioni bloccate, non c'è una versione a pagamento e non
c'è pubblicità.
```

---

## Modulo "Sicurezza dei dati"

| Domanda | Risposta |
|---|---|
| L'app raccoglie o condivide dati utente richiesti? | **No** |
| I dati sono criptati in transito? | Non applicabile — non c'è transito |
| Gli utenti possono richiedere l'eliminazione dei dati? | **Sì** — disinstallando l'app, oppure cancellando i singoli movimenti |

> Il backup automatico di Android **non** va dichiarato come raccolta dati: avviene fra
> l'utente e Google, non fra l'utente e lo sviluppatore. Google lo esclude
> esplicitamente dal modulo.

## Altre voci della Console

- **Categoria**: Finanza
- **Contiene annunci**: No
- **Acquisti in-app**: No — la donazione, se attivata, è un link esterno senza
  contropartita, quindi non è un acquisto in-app
- **URL informativa privacy**: `https://kreedac.github.io/Quadra/privacy.html`
- **Sito web**: `https://kreedac.github.io/Quadra/`
- **Pubblico di destinazione**: adulti, 18+
- **ID applicazione**: `io.github.kreedac.quadra` — assegnato al primo caricamento e
  immutabile per sempre. Controllare che sia questo, e non `it.quadra`, prima di
  premere invio.

## Materiale grafico da preparare

| Cosa | Formato |
|---|---|
| Icona | 512 × 512 PNG |
| Grafica in evidenza | 1024 × 500 |
| Screenshot telefono | almeno 2, meglio 4-6 |

Per gli screenshot userei, in quest'ordine: Movimenti (la schermata principale con la
scheda del mese), l'inserimento con il tastierino e un'operazione in corso, Statistiche,
Conti. Uno chiaro e uno scuro fanno capire subito che ci sono entrambi.
