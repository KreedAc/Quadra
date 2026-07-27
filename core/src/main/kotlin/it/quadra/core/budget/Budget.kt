package it.quadra.core.budget

import it.quadra.core.model.Money
import java.time.LocalDate
import java.time.YearMonth

/**
 * Quanto resta del budget del mese.
 *
 * Il budget è una cifra sola per tutto il mese e non una per categoria. Un budget per
 * categoria sembra più preciso ma chiede di prevedere come si spenderà prima di aver
 * cominciato, e quando poi si sfora in una e si avanza in un'altra non dice niente di
 * utile — mentre la domanda vera, quella che si fa davvero guardando il telefono la sera,
 * è una sola: quanto posso ancora spendere.
 */
data class Andamento(
    val speso: Money,
    val budget: Money,
    /** Negativo quando si è sforato: chi ha superato il budget vuole sapere di quanto. */
    val restano: Money,
    /** Da 0 a 1, tagliato a 1 anche quando si è speso il doppio: la barra non può eccedere. */
    val frazione: Float,
    val sforato: Boolean,
    /**
     * Quanto si potrebbe spendere ogni giorno da oggi alla fine del mese restando dentro.
     * Null quando il mese è finito o il budget è già esaurito: un numero negativo o
     * infinito qui non aiuterebbe nessuno.
     */
    val alGiorno: Money?,
) {
    companion object {

        /**
         * @param speso quanto è uscito nel mese, come valore positivo.
         * @param oggi serve per i giorni che restano; fuori dal mese osservato il
         *   residuo giornaliero non ha senso e resta null.
         */
        fun calcola(
            speso: Money,
            budget: Money,
            mese: YearMonth,
            oggi: LocalDate = LocalDate.now(),
        ): Andamento? {
            if (budget.cents <= 0) return null

            val uscito = speso.abs()
            val restano = budget - uscito
            val frazione = (uscito.cents.toDouble() / budget.cents).coerceIn(0.0, 1.0)

            // I giorni rimasti includono oggi: quello che si può ancora spendere vale
            // anche per stasera.
            val giorniRimasti = when {
                YearMonth.from(oggi) != mese -> 0
                else -> mese.lengthOfMonth() - oggi.dayOfMonth + 1
            }

            return Andamento(
                speso = uscito,
                budget = budget,
                restano = restano,
                frazione = frazione.toFloat(),
                sforato = restano.cents < 0,
                alGiorno = if (giorniRimasti > 0 && restano.cents > 0) {
                    Money(restano.cents / giorniRimasti)
                } else {
                    null
                },
            )
        }
    }
}
