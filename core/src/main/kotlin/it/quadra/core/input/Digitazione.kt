package it.quadra.core.input

import it.quadra.core.model.Money

/** Le operazioni che il tastierino sa fare. Il simbolo è quello che compare sul tasto. */
enum class Operazione(val simbolo: Char) {
    PIU('+'), MENO('−'), PER('×'), DIVISO('÷');

    /** Applica l'operazione a due importi in centesimi. */
    internal fun applica(sinistra: Long, destra: Long): Long = when (this) {
        PIU -> sinistra + destra
        MENO -> sinistra - destra
        // Il secondo operando è un moltiplicatore, non denaro: 4,50 × 3 fa 13,50.
        // Passa per i centesimi, quindi va diviso per cento e arrotondato.
        PER -> arrotonda(sinistra * destra, 100)
        // Dividere per zero non ha risposta: si lascia il valore com'era.
        DIVISO -> if (destra == 0L) sinistra else arrotonda(sinistra * 100, destra)
    }

    /** Divisione intera con arrotondamento al centesimo più vicino, mezzo per eccesso. */
    private fun arrotonda(numeratore: Long, denominatore: Long): Long {
        val segno = if ((numeratore < 0) != (denominatore < 0)) -1 else 1
        val n = kotlin.math.abs(numeratore)
        val d = kotlin.math.abs(denominatore)
        return segno * ((n + d / 2) / d)
    }
}

/**
 * Quello che l'utente ha digitato finora sul tastierino.
 *
 * Esistono due modi di far digitare un importo, e sono incompatibili fra loro.
 *
 * Il primo accumula centesimi da destra: si preme 2, 0 e si ottiene 0,20 — la virgola si
 * sposta da sola, come sui bancomat. È veloce per chi inserisce sempre importi con i
 * decimali, ed è quello che avevamo.
 *
 * Il secondo tratta i tasti come si scrive un numero su un foglio: 2, 0 fa venti, e i
 * centesimi si aggiungono solo dopo aver premuto la virgola. Costa un tocco in più
 * quando i decimali servono, ma non costringe a pensare al contrario per gli importi
 * tondi — che nella spesa quotidiana sono la maggioranza.
 *
 * Questo è il secondo. Il primo confondeva.
 *
 * Lo stato tiene le due parti separate invece di un numero solo, perché servono
 * distinzioni che un numero non può esprimere: "20" e "20," valgono lo stesso importo ma
 * non sono lo stesso stato — nel secondo il tasto successivo scrive nei centesimi — e
 * "20,5" non è "20,05". Tenere il testo digitato, e non il valore, è quello che permette
 * di cancellare a ritroso esattamente nell'ordine in cui si è scritto.
 */
data class Digitazione(
    val intero: String = "",
    /** null finché non si preme la virgola; poi "", "5", "50". */
    val decimali: String? = null,
    /**
     * Il risultato dei termini già chiusi da un operatore, in centesimi.
     *
     * Il tastierino fa i conti perché al bar si paga in una volta quello che si è preso
     * in tre: 4,50 più 1,40 più 2,40. Farlo a mente mentre si è in fila è il modo più
     * facile per sbagliare, ed è anche il momento in cui si rinuncia a registrare.
     */
    val parziale: Long? = null,
    /** L'operatore che aspetta il termine che si sta scrivendo. */
    val operazione: Operazione? = null,
    /** I termini già chiusi, scritti come li ha digitati l'utente, per mostrarli. */
    val passi: String = "",
) {
    val vuota: Boolean get() = intero.isEmpty() && decimali == null

    val haVirgola: Boolean get() = decimali != null

    /** I soli centesimi del termine che si sta scrivendo. */
    private val termine: Long
        get() {
            val euro = intero.toLongOrNull() ?: 0L
            val centesimi = (decimali ?: "").padEnd(2, '0').toLongOrNull() ?: 0L
            return euro * 100 + centesimi
        }

    /** Il risultato di tutto quello che è stato scritto, operazioni comprese. */
    val importo: Money
        get() = when {
            operazione == null || parziale == null -> Money(termine)
            // Con un operatore appeso e nessun termine ancora scritto vale il parziale:
            // moltiplicare per zero azzererebbe il conto mentre si sta ancora digitando.
            vuota -> Money(parziale)
            else -> Money(operazione.applica(parziale, termine))
        }

    /**
     * L'espressione scritta finora, o null quando non c'è nessuna operazione in corso.
     *
     * Con l'operatore appena premuto la formula si ferma lì: [testo] restituirebbe "0",
     * e mostrare "10 − 0" farebbe credere di aver digitato uno zero che nessuno ha
     * premuto.
     */
    val formula: String?
        get() = when {
            passi.isEmpty() -> null
            vuota -> passi.trimEnd()
            else -> passi + testo()
        }

    /** Vero quando c'è qualcosa di sensato da salvare. */
    val valido: Boolean get() = importo.cents > 0

    /**
     * Una cifra.
     *
     * I limiti non sono estetici: senza, un dito appoggiato sul tasto porta il totale
     * oltre quello che un Long in centesimi può contenere.
     */
    fun cifra(c: Char): Digitazione {
        if (c !in '0'..'9') return this
        return when {
            decimali == null -> {
                // Uno zero iniziale da solo non vuol dire niente: "0" poi "5" fa 5, non 05.
                val nuovo = if (intero == "0") c.toString() else intero + c
                if (nuovo.length > MAX_INTERE) this else copy(intero = nuovo)
            }
            decimali.length < 2 -> copy(decimali = decimali + c)
            else -> this
        }
    }

    /**
     * La virgola.
     *
     * Premuta due volte non fa niente: ripeterla non ha un significato da inventare.
     * Premuta per prima vale "0,": chi scrive ",50" intende cinquanta centesimi.
     */
    fun virgola(): Digitazione = when {
        decimali != null -> this
        intero.isEmpty() -> Digitazione(intero = "0", decimali = "")
        else -> copy(decimali = "")
    }

    /**
     * Cancella l'ultimo tasto premuto, virgola compresa, nell'ordine in cui è stato premuto.
     *
     * Su un termine vuoto con un'operazione appesa, il passo indietro è annullare
     * l'operazione: il parziale torna a essere il numero che si sta scrivendo, così si
     * può correggere invece di restare bloccati con un operatore che non risponde.
     */
    fun indietro(): Digitazione = when {
        !decimali.isNullOrEmpty() -> copy(decimali = decimali.dropLast(1))
        // La virgola c'è ma è vuota: il passo indietro è toglierla.
        decimali == "" -> copy(decimali = null)
        intero.isNotEmpty() -> copy(intero = intero.dropLast(1))
        parziale != null -> da(Money(parziale))
        else -> this
    }

    /**
     * Chiude il termine corrente e mette in attesa un'operazione.
     *
     * Premuto due volte di fila cambia l'operatore invece di aggiungerne un altro: è
     * quello che si intende quando si sbaglia tasto.
     */
    fun operazione(nuova: Operazione): Digitazione {
        if (vuota && operazione != null) {
            return copy(operazione = nuova, passi = passi.dropLast(3) + " ${nuova.simbolo} ")
        }
        return Digitazione(
            parziale = importo.cents,
            operazione = nuova,
            passi = passi + testo() + " ${nuova.simbolo} ",
        )
    }

    fun azzera(): Digitazione = Digitazione()

    /**
     * Cosa mostrare mentre si scrive.
     *
     * Rispecchia i tasti premuti invece di formattare l'importo: dopo la virgola si deve
     * vedere "20," e non "20,00", altrimenti non si capisce di aver cambiato metà del
     * numero e i due zeri sembrano cifre già inserite.
     */
    fun testo(): String {
        val parteIntera = raggruppa(intero.ifEmpty { "0" })
        return if (decimali == null) parteIntera else "$parteIntera,$decimali"
    }

    private fun raggruppa(cifre: String): String {
        val n = cifre.toLongOrNull() ?: return cifre
        return buildString {
            val testo = n.toString()
            testo.forEachIndexed { i, c ->
                if (i > 0 && (testo.length - i) % 3 == 0) append('.')
                append(c)
            }
        }
    }

    companion object {
        /** Nove cifre intere: 999.999.999 € è ben oltre qualunque spesa domestica. */
        const val MAX_INTERE = 9

        /** Ricostruisce lo stato da un importo esistente, per la modifica. */
        fun da(importo: Money): Digitazione {
            val abs = kotlin.math.abs(importo.cents)
            val centesimi = (abs % 100).toInt()
            val euro = (abs / 100).toString()
            return if (centesimi == 0) {
                Digitazione(intero = euro)
            } else {
                Digitazione(intero = euro, decimali = centesimi.toString().padStart(2, '0'))
            }
        }
    }
}
