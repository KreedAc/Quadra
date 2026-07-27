package it.lemiespese.core.recurrence

import it.lemiespese.core.model.RecurrenceUnit
import it.lemiespese.core.model.RecurringRule
import it.lemiespese.core.model.Transaction
import it.lemiespese.core.model.TransactionSource
import java.time.LocalDate

/**
 * Calcola quando cade una regola ricorrente e ne materializza i movimenti.
 *
 * Due proprietà che il codice qui sotto garantisce e che i test verificano:
 *
 * 1. Nessuna deriva. Le occorrenze si calcolano sempre come `startDate + n*intervallo`,
 *    mai sommando all'occorrenza precedente. Sommare un mese alla volta partendo dal
 *    31 gennaio porta a febbraio 28, marzo 28, aprile 28: la regola "il 31 di ogni mese"
 *    scivolerebbe silenziosamente al 28 per sempre.
 *
 * 2. Idempotenza. [materialize] riceve le occorrenze già presenti e non le riproduce,
 *    così può girare a ogni avvio dell'app senza duplicare nulla. Rispetta anche le date
 *    che l'utente ha cancellato a mano, altrimenti ciò che si cancella oggi ricompare
 *    domani.
 */
object RecurrenceEngine {

    /**
     * Date in cui la regola cade nell'intervallo [from]..[to], estremi inclusi.
     * Le regole non attive restituiscono lista vuota.
     */
    fun occurrences(rule: RecurringRule, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (!rule.active) return emptyList()
        if (to < from) return emptyList()

        val hardEnd = rule.endDate?.let { if (it < to) it else to } ?: to
        if (hardEnd < rule.startDate) return emptyList()

        val result = mutableListOf<LocalDate>()
        var n = firstIndexAtOrAfter(rule, from)
        while (true) {
            val date = occurrenceAt(rule, n)
            if (date > hardEnd) break
            if (date >= from && date >= rule.startDate) result += date
            n++
        }
        return result
    }

    /** Prossima scadenza a partire da [after] compreso, o null se la regola è finita. */
    fun nextOccurrence(rule: RecurringRule, after: LocalDate): LocalDate? {
        if (!rule.active) return null
        var n = firstIndexAtOrAfter(rule, after)
        // Due tentativi bastano: il clamping di fine mese può solo anticipare una data,
        // mai spingerla oltre l'occorrenza successiva.
        repeat(2) {
            val date = occurrenceAt(rule, n)
            if (date >= after && date >= rule.startDate) {
                return if (rule.endDate != null && date > rule.endDate) null else date
            }
            n++
        }
        return null
    }

    /**
     * Crea i movimenti mancanti per la regola fino a [upTo] compreso.
     *
     * @param existingDates date per cui il movimento esiste già; vengono saltate.
     * @param idFactory genera l'id del nuovo movimento — iniettato invece che chiamare
     *        UUID.randomUUID() qui dentro, così i test sono deterministici.
     */
    fun materialize(
        rule: RecurringRule,
        upTo: LocalDate,
        existingDates: Set<LocalDate> = emptySet(),
        idFactory: (RecurringRule, LocalDate) -> String,
    ): List<Transaction> {
        if (!rule.autoInsert) return emptyList()
        return occurrences(rule, rule.startDate, upTo)
            .filter { it !in existingDates && it !in rule.skippedDates }
            .map { date ->
                Transaction(
                    id = idFactory(rule, date),
                    amount = rule.amount,
                    date = date,
                    categoryId = rule.categoryId,
                    accountId = rule.accountId,
                    description = rule.description,
                    source = TransactionSource.RECURRING,
                    recurringRuleId = rule.id,
                )
            }
    }

    /** N-esima occorrenza della regola, contando da zero su [RecurringRule.startDate]. */
    private fun occurrenceAt(rule: RecurringRule, index: Int): LocalDate {
        val step = index.toLong() * rule.every
        return when (rule.unit) {
            RecurrenceUnit.DAY -> rule.startDate.plusDays(step)
            RecurrenceUnit.WEEK -> rule.startDate.plusWeeks(step)
            RecurrenceUnit.MONTH -> rule.startDate.plusMonths(step).withPreferredDay(rule)
            RecurrenceUnit.YEAR -> rule.startDate.plusYears(step).withPreferredDay(rule)
        }
    }

    /**
     * Riporta la data al giorno del mese voluto, limitandolo alla lunghezza del mese.
     * `plusMonths` di java.time già accorcia il 31 a fine mese, ma non lo ripristina
     * al mese successivo: senza questo passaggio il 31 gennaio diventa 28 febbraio e
     * poi resta 28 per sempre.
     */
    private fun LocalDate.withPreferredDay(rule: RecurringRule): LocalDate {
        val preferred = rule.dayOfMonth ?: rule.startDate.dayOfMonth
        return withDayOfMonth(minOf(preferred, lengthOfMonth()))
    }

    /**
     * Indice della prima occorrenza che potrebbe cadere in [target] o dopo.
     * Stima aritmetica: parte da una sottostima e poi arretra finché serve, così
     * l'iterazione successiva non deve partire da zero su regole molto vecchie.
     */
    private fun firstIndexAtOrAfter(rule: RecurringRule, target: LocalDate): Int {
        if (target <= rule.startDate) return 0
        val elapsed = when (rule.unit) {
            RecurrenceUnit.DAY -> java.time.temporal.ChronoUnit.DAYS.between(rule.startDate, target)
            RecurrenceUnit.WEEK -> java.time.temporal.ChronoUnit.WEEKS.between(rule.startDate, target)
            RecurrenceUnit.MONTH -> java.time.temporal.ChronoUnit.MONTHS.between(rule.startDate, target)
            RecurrenceUnit.YEAR -> java.time.temporal.ChronoUnit.YEARS.between(rule.startDate, target)
        }
        var index = (elapsed / rule.every).toInt()
        while (index > 0 && occurrenceAt(rule, index) >= target) index--
        return index
    }
}
