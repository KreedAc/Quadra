package it.quadra.core.ledger

import it.quadra.core.model.Money

/**
 * Quanto del proprio denaro se n'è andato in questo mese.
 *
 * Il riferimento è il saldo disponibile, non un budget deciso in anticipo. Un budget
 * chiede di prevedere quanto si spenderà prima di aver cominciato, e chi lo sbaglia — o
 * chi semplicemente non lo imposta — resta senza barra e senza informazione. Il saldo
 * invece c'è sempre, è un numero vero e non un'intenzione, e risponde alla domanda che
 * ci si fa davvero guardando il telefono la sera: quanto mi resta.
 *
 * I conti vincolati non entrano. La carta di un sussidio contiene denaro reale ma a
 * destinazione d'uso: sommarlo farebbe credere di avere più margine di quanto se ne ha,
 * ed è esattamente l'errore che questa app esiste per non far fare.
 *
 * La barra si riempie sul denaro che c'era all'inizio del mese, ricostruito come
 * disponibile più speso. È una ricostruzione e non una fotografia presa il primo del
 * mese: ricalcolarla dal saldo di adesso la rende sempre coerente con quello che si
 * vede, anche dopo aver corretto o cancellato un movimento vecchio.
 */
data class Andamento(
    /** Uscite del mese, come valore positivo. */
    val speso: Money,
    /** Quello che c'è adesso sui conti spendibili. */
    val disponibile: Money,
    /** Quello che c'era prima di cominciare a spendere: disponibile più speso. */
    val partenza: Money,
    /** Da 0 a 1: la parte di [partenza] già consumata. */
    val frazione: Float,
    /** Vero quando i conti spendibili sono sotto zero. */
    val inRosso: Boolean,
) {
    companion object {

        /**
         * @param speso le uscite del mese; il segno non conta.
         * @param disponibile il saldo dei conti spendibili, che può essere negativo.
         */
        fun calcola(speso: Money, disponibile: Money): Andamento {
            val uscito = speso.abs()
            val partenza = disponibile + uscito

            // Con una partenza a zero o sotto non c'è una proporzione da mostrare: la
            // barra si riempie del tutto, che è il modo onesto di dire "non ne resta".
            val frazione = when {
                partenza.cents <= 0L -> 1.0
                else -> (uscito.cents.toDouble() / partenza.cents).coerceIn(0.0, 1.0)
            }

            return Andamento(
                speso = uscito,
                disponibile = disponibile,
                partenza = partenza,
                frazione = frazione.toFloat(),
                inRosso = disponibile.cents < 0L,
            )
        }
    }
}
