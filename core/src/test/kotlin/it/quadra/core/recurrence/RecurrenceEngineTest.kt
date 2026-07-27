package it.quadra.core.recurrence

import it.quadra.core.model.Money
import it.quadra.core.model.RecurrenceUnit
import it.quadra.core.model.RecurringRule
import it.quadra.core.model.TransactionSource
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurrenceEngineTest {

    private fun rule(
        every: Int = 1,
        unit: RecurrenceUnit = RecurrenceUnit.MONTH,
        start: String = "2026-01-15",
        end: String? = null,
        dayOfMonth: Int? = null,
        active: Boolean = true,
        autoInsert: Boolean = true,
    ) = RecurringRule(
        id = "r1",
        description = "Affitto",
        amount = Money.of(-750),
        categoryId = "casa.affitto",
        accountId = "carta",
        every = every,
        unit = unit,
        startDate = LocalDate.parse(start),
        endDate = end?.let(LocalDate::parse),
        dayOfMonth = dayOfMonth,
        active = active,
        autoInsert = autoInsert,
    )

    private fun dates(vararg iso: String) = iso.map(LocalDate::parse)

    @Test
    fun `mensile semplice`() {
        val occorrenze = RecurrenceEngine.occurrences(
            rule(),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-04-30"),
        )
        assertEquals(dates("2026-01-15", "2026-02-15", "2026-03-15", "2026-04-15"), occorrenze)
    }

    @Test
    fun `bolletta bimestrale`() {
        val occorrenze = RecurrenceEngine.occurrences(
            rule(every = 2, start = "2026-01-10"),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-12-31"),
        )
        assertEquals(
            dates("2026-01-10", "2026-03-10", "2026-05-10", "2026-07-10", "2026-09-10", "2026-11-10"),
            occorrenze,
        )
    }

    @Test
    fun `bollo auto annuale`() {
        val occorrenze = RecurrenceEngine.occurrences(
            rule(every = 1, unit = RecurrenceUnit.YEAR, start = "2026-03-31"),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2029-12-31"),
        )
        assertEquals(dates("2026-03-31", "2027-03-31", "2028-03-31", "2029-03-31"), occorrenze)
    }

    @Test
    fun `IMU semestrale a giugno e dicembre`() {
        val occorrenze = RecurrenceEngine.occurrences(
            rule(every = 6, start = "2026-06-16"),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2027-12-31"),
        )
        assertEquals(dates("2026-06-16", "2026-12-16", "2027-06-16", "2027-12-16"), occorrenze)
    }

    @Test
    fun `il giorno 31 non scivola in avanti dopo febbraio`() {
        // Questa è la regressione classica: sommando un mese alla volta si otterrebbe
        // 31 gen, 28 feb, 28 mar, 28 apr… e la regola "il 31" sparirebbe per sempre.
        val occorrenze = RecurrenceEngine.occurrences(
            rule(start = "2026-01-31"),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-06-30"),
        )
        assertEquals(
            dates("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31", "2026-06-30"),
            occorrenze,
        )
    }

    @Test
    fun `il 29 febbraio è gestito negli anni bisestili`() {
        val occorrenze = RecurrenceEngine.occurrences(
            rule(start = "2027-12-29", dayOfMonth = 29),
            LocalDate.parse("2028-01-01"),
            LocalDate.parse("2028-04-30"),
        )
        // 2028 è bisestile, quindi il 29 febbraio esiste davvero.
        assertEquals(dates("2028-01-29", "2028-02-29", "2028-03-29", "2028-04-29"), occorrenze)
    }

    @Test
    fun `dayOfMonth esplicito ha la precedenza sul giorno di partenza`() {
        val occorrenze = RecurrenceEngine.occurrences(
            rule(start = "2026-01-05", dayOfMonth = 27),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-03-31"),
        )
        assertEquals(dates("2026-01-27", "2026-02-27", "2026-03-27"), occorrenze)
    }

    @Test
    fun `settimanale`() {
        val occorrenze = RecurrenceEngine.occurrences(
            rule(every = 1, unit = RecurrenceUnit.WEEK, start = "2026-02-02"),
            LocalDate.parse("2026-02-01"),
            LocalDate.parse("2026-03-01"),
        )
        assertEquals(dates("2026-02-02", "2026-02-09", "2026-02-16", "2026-02-23"), occorrenze)
    }

    @Test
    fun `la finestra richiesta viene rispettata su entrambi i lati`() {
        val r = rule(start = "2026-01-15")
        val occorrenze = RecurrenceEngine.occurrences(
            r,
            LocalDate.parse("2026-03-01"),
            LocalDate.parse("2026-05-01"),
        )
        assertEquals(dates("2026-03-15", "2026-04-15"), occorrenze)
    }

    @Test
    fun `la data di fine è inclusiva`() {
        val occorrenze = RecurrenceEngine.occurrences(
            rule(start = "2026-01-15", end = "2026-03-15"),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-12-31"),
        )
        assertEquals(dates("2026-01-15", "2026-02-15", "2026-03-15"), occorrenze)
    }

    @Test
    fun `niente occorrenze prima dell'inizio`() {
        val occorrenze = RecurrenceEngine.occurrences(
            rule(start = "2026-06-01"),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-05-31"),
        )
        assertTrue(occorrenze.isEmpty())
    }

    @Test
    fun `una regola disattivata non produce nulla`() {
        val occorrenze = RecurrenceEngine.occurrences(
            rule(active = false),
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-12-31"),
        )
        assertTrue(occorrenze.isEmpty())
    }

    @Test
    fun `intervallo invertito restituisce lista vuota`() {
        val occorrenze = RecurrenceEngine.occurrences(
            rule(),
            LocalDate.parse("2026-12-31"),
            LocalDate.parse("2026-01-01"),
        )
        assertTrue(occorrenze.isEmpty())
    }

    @Test
    fun `prossima scadenza`() {
        val r = rule(start = "2026-01-15")
        assertEquals(LocalDate.parse("2026-01-15"), RecurrenceEngine.nextOccurrence(r, LocalDate.parse("2026-01-01")))
        assertEquals(LocalDate.parse("2026-01-15"), RecurrenceEngine.nextOccurrence(r, LocalDate.parse("2026-01-15")))
        assertEquals(LocalDate.parse("2026-02-15"), RecurrenceEngine.nextOccurrence(r, LocalDate.parse("2026-01-16")))
        assertEquals(LocalDate.parse("2027-01-15"), RecurrenceEngine.nextOccurrence(r, LocalDate.parse("2026-12-20")))
    }

    @Test
    fun `prossima scadenza è nulla oltre la fine`() {
        val r = rule(start = "2026-01-15", end = "2026-03-15")
        assertNull(RecurrenceEngine.nextOccurrence(r, LocalDate.parse("2026-04-01")))
    }

    @Test
    fun `materialize crea i movimenti mancanti`() {
        val r = rule(start = "2026-01-15")
        val movimenti = RecurrenceEngine.materialize(
            rule = r,
            upTo = LocalDate.parse("2026-03-31"),
            idFactory = { rule, date -> "${rule.id}@$date" },
        )
        assertEquals(3, movimenti.size)
        assertEquals(dates("2026-01-15", "2026-02-15", "2026-03-15"), movimenti.map { it.date })
        assertTrue(movimenti.all { it.source == TransactionSource.RECURRING })
        assertTrue(movimenti.all { it.recurringRuleId == "r1" })
        assertTrue(movimenti.all { it.amount == Money.of(-750) })
    }

    @Test
    fun `materialize non duplica ciò che esiste già`() {
        val r = rule(start = "2026-01-15")
        val movimenti = RecurrenceEngine.materialize(
            rule = r,
            upTo = LocalDate.parse("2026-03-31"),
            existingDates = setOf(LocalDate.parse("2026-01-15"), LocalDate.parse("2026-02-15")),
            idFactory = { rule, date -> "${rule.id}@$date" },
        )
        assertEquals(dates("2026-03-15"), movimenti.map { it.date })
    }

    @Test
    fun `materialize è idempotente se rieseguito`() {
        val r = rule(start = "2026-01-15")
        val upTo = LocalDate.parse("2026-03-31")
        val idFactory = { rule: RecurringRule, date: LocalDate -> "${rule.id}@$date" }

        val primaEsecuzione = RecurrenceEngine.materialize(r, upTo, idFactory = idFactory)
        val secondaEsecuzione = RecurrenceEngine.materialize(
            r, upTo, existingDates = primaEsecuzione.map { it.date }.toSet(), idFactory = idFactory,
        )
        assertTrue(secondaEsecuzione.isEmpty())
    }

    @Test
    fun `le regole non automatiche non materializzano nulla`() {
        // Le bollette hanno importo variabile: l'app le propone, non le inventa.
        val movimenti = RecurrenceEngine.materialize(
            rule = rule(autoInsert = false),
            upTo = LocalDate.parse("2026-12-31"),
            idFactory = { _, _ -> "x" },
        )
        assertTrue(movimenti.isEmpty())
    }

    @Test
    fun `una regola molto vecchia non degrada e resta corretta`() {
        val r = rule(start = "2005-01-10")
        val occorrenze = RecurrenceEngine.occurrences(
            r,
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-03-31"),
        )
        assertEquals(dates("2026-01-10", "2026-02-10", "2026-03-10"), occorrenze)
    }
}
