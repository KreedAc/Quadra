package it.quadra.core.input

import it.quadra.core.model.Money

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
) {
    val vuota: Boolean get() = intero.isEmpty() && decimali == null

    val haVirgola: Boolean get() = decimali != null

    val importo: Money
        get() {
            val euro = intero.toLongOrNull() ?: 0L
            val centesimi = (decimali ?: "").padEnd(2, '0').toLongOrNull() ?: 0L
            return Money(euro * 100 + centesimi)
        }

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

    /** Cancella l'ultimo tasto premuto, virgola compresa, nell'ordine in cui è stato premuto. */
    fun indietro(): Digitazione = when {
        decimali == null -> copy(intero = intero.dropLast(1))
        decimali.isNotEmpty() -> copy(decimali = decimali.dropLast(1))
        // La virgola c'è ma è vuota: il passo indietro è toglierla.
        else -> copy(decimali = null)
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
