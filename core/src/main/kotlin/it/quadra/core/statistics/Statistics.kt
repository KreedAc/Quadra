package it.quadra.core.statistics

import it.quadra.core.ledger.Ledger
import it.quadra.core.model.Money
import it.quadra.core.model.Transaction
import it.quadra.core.model.sum
import java.time.YearMonth

/** Quanto pesa una categoria in un periodo. */
data class CategoryTotal(
    val categoryId: String,
    val total: Money,
    /** Frazione del totale speso, fra 0 e 1. Serve alla lunghezza della barra. */
    val share: Double,
)

/** Quanto si è speso in un mese. */
data class MonthTotal(
    val month: YearMonth,
    val spent: Money,
)

/**
 * Le aggregazioni per le statistiche.
 *
 * Due regole valgono ovunque qui dentro, e sono la ragione per cui questo codice sta
 * nel nucleo invece che dentro una schermata.
 *
 * I trasferimenti non compaiono mai: spostare denaro fra i propri conti non è spesa, e
 * lasciarlo entrare gonfierebbe ogni grafico del doppio dell'importo spostato.
 *
 * Le entrate non compaiono nella spesa: un mese con lo stipendio dentro non è un mese in
 * cui si è speso meno. Vanno guardate separatamente.
 */
object Statistics {

    /**
     * Spesa per categoria di primo livello, dalla più pesante alla più leggera.
     *
     * Le sottocategorie vengono sommate nella loro radice: chi guarda le statistiche
     * vuole sapere quanto è andato in "Trasporti", non quanto in "Pedaggi e parcheggi".
     *
     * @param rootOf risolve una categoria nella sua radice; iniettata perché il nucleo
     *        non conosce il database. Se restituisce null la categoria resta com'è.
     */
    fun byRootCategory(
        transactions: List<Transaction>,
        rootOf: (String) -> String?,
    ): List<CategoryTotal> {
        val spese = Ledger.spending(transactions).filter { it.isExpense }
        if (spese.isEmpty()) return emptyList()

        val totale = spese.map { it.amount }.sum().abs()
        return spese
            .groupBy { rootOf(it.categoryId) ?: it.categoryId }
            .map { (categoria, movimenti) ->
                val somma = movimenti.map { it.amount }.sum().abs()
                CategoryTotal(
                    categoryId = categoria,
                    total = somma,
                    share = if (totale.isZero) 0.0 else somma.cents.toDouble() / totale.cents,
                )
            }
            .sortedByDescending { it.total.cents }
    }

    /**
     * Spesa mese per mese, nell'ordine cronologico dei mesi richiesti.
     *
     * I mesi senza movimenti compaiono comunque a zero: un buco nel grafico è
     * un'informazione, e toglierlo farebbe sembrare consecutivi due mesi che non lo sono.
     */
    fun monthlySpending(transactions: List<Transaction>, months: List<YearMonth>): List<MonthTotal> {
        val perMese = Ledger.spending(transactions)
            .filter { it.isExpense }
            .groupBy { YearMonth.from(it.date) }
        return months.map { mese ->
            MonthTotal(mese, perMese[mese].orEmpty().map { it.amount }.sum().abs())
        }
    }

    /** Gli ultimi [count] mesi fino a [last] compreso, dal più vecchio al più recente. */
    fun lastMonths(last: YearMonth, count: Int): List<YearMonth> {
        require(count >= 1) { "Servono almeno un mese, richiesti $count" }
        return (count - 1 downTo 0).map { last.minusMonths(it.toLong()) }
    }

    /** Media giornaliera di spesa su un mese, per il confronto fra mesi di lunghezza diversa. */
    fun dailyAverage(transactions: List<Transaction>, month: YearMonth): Money {
        val speso = Ledger.totalSpent(transactions.filter { YearMonth.from(it.date) == month })
        return Money(speso.cents / month.lengthOfMonth())
    }
}
