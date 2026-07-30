# Grafica per il Play Store

I due file già pronti da caricare:

- `quadra-icona-512.png` — icona, 512 × 512
- `quadra-feature-1024x500.png` — grafica in evidenza, 1024 × 500

## Perché sono qui e non solo caricate

Sono generate dagli stessi vettori dell'icona di sistema, non ridisegnate a mano: se un
giorno cambia il simbolo dell'app, si rigenerano e restano identiche a quello che c'è
sul telefono. Un'icona di negozio disegnata a parte diverge alla prima modifica, e
nessuno se ne accorge finché non le si vede vicine.

`icona.html` ritaglia i 72 unità centrali dei 108 dell'icona adattiva: è esattamente la
porzione che il lanciatore mostra sul telefono. Renderizzare tutti i 108 farebbe un
simbolo più piccolo nel negozio che sulla schermata iniziale.

## Rigenerarle

Serve Node con Playwright e un Chromium installato:

    npx playwright install chromium
    node rendi.js

Il percorso del browser è scritto dentro `rendi.js` e va adattato alla propria macchina.

## Screenshot

`play-*.png` — quattro schermate 1200 × 2200, da caricare in ordine numerico.

Le schermate grezze del telefono sono 1200 × 2670, cioè rapporto **2,225**: Google ne
accetta al massimo **2:1**, quindi caricate così com'erano verrebbero rifiutate. Non è
l'unica ragione per rimontarle — la barra di stato mostra ora, batteria e le notifiche
di chi ha fatto lo scatto, che non raccontano l'app e non riguardano nessuno.

`scatto.js` prende i file grezzi da `scatti/`, taglia la barra di stato e li monta su una
tela conforme con la didascalia. Per rifarli con schermate nuove: sostituisci i file in
`scatti/`, aggiorna le didascalie in cima allo script e rilancia `node scatto.js`.
