package it.quadra.core.ledger

import it.quadra.core.model.Account
import it.quadra.core.model.AccountKind
import it.quadra.core.model.Money
import it.quadra.core.model.RecurrenceUnit
import it.quadra.core.model.RecurringRule
import it.quadra.core.model.Transaction
import it.quadra.core.model.TransactionSource
import it.quadra.core.recurrence.RecurrenceEngine
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditsTest {

    private val oggi = LocalDate.parse("2026-07-26")

    private val contanti = Account("contanti", "Contanti", AccountKind.CASH, 0, Money.of(145))
    private val cartaX = Account("carta_x", "Carta X", AccountKind.CARD, 0, Money.of(500))
    private val cartaY = Account("carta_y", "Carta Y", AccountKind.CARD, 0, Money.of(200))

    private fun spesa(id: String, cents: Long, account: String) = Transaction(
        id = id, amount = Money(cents), date = oggi, categoryId = "spesa", accountId = account,
    )

    private fun trasferimento(): Pair<Transaction, Transaction> = Ledger.transfer(
        from = cartaX, to = contanti, amount = Money.of(100), date = oggi,
        groupId = "g1", idFactory = { "leg$it" },
    )

    // ------------------------------------------------------------ correggere

    private val entrata = Transaction(
        id = "e1", amount = Money.of(1200), date = oggi,
        categoryId = "entrate.stipendio", accountId = "carta_x",
    )

    @Test
    fun `correggere cambia quello che si è toccato e nient'altro`() {
        val prima = spesa("a", -4780, "carta_x")
        val dopo = Edits.edit(
            prima, Money.of(52, 30), "spesa.supermercato", "carta_y", oggi.minusDays(1),
            description = "Esselunga",
        )
        assertEquals(Money.of(-52, 30), dopo.amount)
        assertEquals("spesa.supermercato", dopo.categoryId)
        assertEquals("carta_y", dopo.accountId)
        assertEquals(oggi.minusDays(1), dopo.date)
        assertEquals("Esselunga", dopo.description)
        // Identità e origine non si toccano: è lo stesso movimento, corretto.
        assertEquals(prima.id, dopo.id)
        assertEquals(prima.createdAt, dopo.createdAt)
        assertEquals(prima.source, dopo.source)
    }

    @Test
    fun `l'importo si scrive senza segno e il verso resta quello che era`() {
        // Chi corregge uno stipendio da 1200 a 1250 non sta dicendo che ora è un'uscita.
        assertTrue(Edits.edit(entrata, Money.of(1250), "entrate.stipendio", "carta_x", oggi).amount.isIncome)
        assertTrue(Edits.edit(spesa("a", -100, "carta_x"), Money.of(120), "spesa", "carta_x", oggi).amount.isExpense)
    }

    @Test
    fun `correggere non altera il patrimonio complessivo per sbaglio`() {
        val movimenti = listOf(spesa("a", -4780, "carta_x"))
        val corretto = Edits.edit(movimenti[0], Money.of(30), "spesa", "carta_y", oggi)
        val dopo = listOf(corretto)
        // I cinquanta euro tolti alla carta X ricompaiono: nessuna compensazione serve.
        assertEquals(Money.of(500), Ledger.balanceOf(cartaX, dopo))
        assertEquals(Money.of(170), Ledger.balanceOf(cartaY, dopo))
    }

    @Test
    fun `una gamba di trasferimento non si corregge da sola`() {
        val (uscita, _) = trasferimento()
        assertFailsWith<IllegalArgumentException> {
            Edits.edit(uscita, Money.of(50), "spesa", "carta_y", oggi)
        }
    }

    @Test
    fun `un movimento da zero si rifiuta invece di essere salvato`() {
        // Zero non è una correzione, è una cancellazione mascherata.
        assertFailsWith<IllegalArgumentException> {
            Edits.edit(spesa("a", -100, "carta_x"), Money.ZERO, "spesa", "carta_x", oggi)
        }
    }

    @Test
    fun `gli spazi intorno a descrizione e note vengono tolti`() {
        val dopo = Edits.edit(
            spesa("a", -100, "carta_x"), Money.of(1), "spesa", "carta_x", oggi,
            description = "  Bar  ", notes = "  con Marco ",
        )
        assertEquals("Bar", dopo.description)
        assertEquals("con Marco", dopo.notes)
    }

    // ------------------------------------------------------------ cancellare

    @Test
    fun `cancellare restituisce il credito e non lascia traccia`() {
        val movimenti = listOf(spesa("a", -4780, "carta_x"), spesa("b", -6200, "carta_x"))
        assertEquals(Money.of(390, 20), Ledger.balanceOf(cartaX, movimenti))

        val dopo = Edits.delete(movimenti[0], movimenti)

        // Il saldo torna com'era: non serve nessuno storno, perché il saldo non è mai
        // un numero scritto ma sempre apertura più movimenti.
        assertEquals(Money.of(438), Ledger.balanceOf(cartaX, dopo))
        // E la riga non esiste più in nessuna forma: niente stato "annullato".
        assertEquals(listOf("b"), dopo.map { it.id })
        assertTrue(dopo.none { it.id == "a" })
    }

    @Test
    fun `cancellare toglie il movimento anche dalle statistiche`() {
        val movimenti = listOf(spesa("a", -4780, "carta_x"), spesa("b", -6200, "carta_x"))
        assertEquals(Money.of(109, 80), Ledger.totalSpent(movimenti))
        assertEquals(Money.of(62), Ledger.totalSpent(Edits.delete(movimenti[0], movimenti)))
    }

    @Test
    fun `cancellare una gamba di trasferimento le cancella entrambe`() {
        // Cancellarne una sola farebbe comparire cento euro dal nulla sui contanti
        // senza che escano dalla carta.
        val (uscita, entrata) = trasferimento()
        val movimenti = listOf(spesa("a", -4780, "carta_x"), uscita, entrata)

        val dopo = Edits.delete(uscita, movimenti)

        assertEquals(listOf("a"), dopo.map { it.id })
        assertEquals(Money.of(452, 20), Ledger.balanceOf(cartaX, dopo))
        assertEquals(Money.of(145), Ledger.balanceOf(contanti, dopo))
    }

    @Test
    fun `cancellare partendo dall'altra gamba dà lo stesso risultato`() {
        val (uscita, entrata) = trasferimento()
        val movimenti = listOf(uscita, entrata)
        assertEquals(Edits.delete(uscita, movimenti), Edits.delete(entrata, movimenti))
        assertTrue(Edits.delete(entrata, movimenti).isEmpty())
    }

    @Test
    fun `una ricorrente cancellata non rinasce al riavvio`() {
        val regola = RecurringRule(
            id = "r1", description = "Netflix", amount = Money.of(-12, 99),
            categoryId = "svago.abbonamenti_digitali", accountId = "carta_x",
            every = 1, unit = RecurrenceUnit.MONTH, startDate = LocalDate.parse("2026-05-10"),
        )
        val idFactory = { r: RecurringRule, d: LocalDate -> "${r.id}@$d" }
        val generati = RecurrenceEngine.materialize(regola, oggi, idFactory = idFactory)
        assertEquals(3, generati.size)

        val daCancellare = generati.first { it.date == LocalDate.parse("2026-06-10") }
        val rimasti = Edits.delete(daCancellare, generati)

        // Senza avvisare la regola, il giro successivo lo ricrea.
        val ingenuo = RecurrenceEngine.materialize(
            regola, oggi, existingDates = rimasti.map { it.date }.toSet(), idFactory = idFactory,
        )
        assertEquals(listOf(LocalDate.parse("2026-06-10")), ingenuo.map { it.date })

        // Segnando la data come saltata, resta cancellato.
        val (ruleId, data) = Edits.skipForRule(daCancellare)!!
        assertEquals("r1", ruleId)
        val aggiornata = regola.copy(skippedDates = regola.skippedDates + data)
        val corretto = RecurrenceEngine.materialize(
            aggiornata, oggi, existingDates = rimasti.map { it.date }.toSet(), idFactory = idFactory,
        )
        assertTrue(corretto.isEmpty())
    }

    @Test
    fun `un movimento manuale non ha nessuna regola da avvisare`() {
        assertNull(Edits.skipForRule(spesa("a", -1000, "carta_x")))
    }

    // ------------------------------------------------------------- correggere

    @Test
    fun `spostare un movimento sulla carta giusta aggiusta entrambi i saldi`() {
        // "L'avevo messo sulla carta X ma in realtà era la Y."
        val sbagliato = spesa("a", -4780, "carta_x")
        val movimenti = listOf(sbagliato)
        assertEquals(Money.of(452, 20), Ledger.balanceOf(cartaX, movimenti))
        assertEquals(Money.of(200), Ledger.balanceOf(cartaY, movimenti))

        val corretti = listOf(Edits.moveToAccount(sbagliato, "carta_y"))

        assertEquals(Money.of(500), Ledger.balanceOf(cartaX, corretti))
        assertEquals(Money.of(152, 20), Ledger.balanceOf(cartaY, corretti))
    }

    @Test
    fun `spostare un movimento non cambia il patrimonio né la spesa totale`() {
        val sbagliato = spesa("a", -4780, "carta_x")
        val conti = listOf(contanti, cartaX, cartaY)
        val prima = Ledger.totals(conti, listOf(sbagliato))
        val dopo = Ledger.totals(conti, listOf(Edits.moveToAccount(sbagliato, "carta_y")))

        assertEquals(prima.overall, dopo.overall)
        assertEquals(Money.of(47, 80), Ledger.totalSpent(listOf(Edits.moveToAccount(sbagliato, "carta_y"))))
    }

    @Test
    fun `correggere non cambia mai l'identificativo`() {
        // I movimenti finiscono nei backup: devono restare la stessa cosa prima e dopo.
        val originale = spesa("a", -4780, "carta_x")
        assertEquals("a", Edits.moveToAccount(originale, "carta_y").id)
    }

    @Test
    fun `una gamba di trasferimento non si sposta da sola`() {
        val (uscita, _) = trasferimento()
        assertFailsWith<IllegalArgumentException> { Edits.moveToAccount(uscita, "carta_y") }
    }

    @Test
    fun `correggere un trasferimento tiene le due gambe coerenti`() {
        val (uscita, entrata) = trasferimento()
        val corrette = Edits.editTransfer(
            legs = listOf(uscita, entrata),
            fromAccountId = "carta_y", toAccountId = "contanti",
            amount = Money.of(150), date = LocalDate.parse("2026-07-20"),
        )

        assertEquals(Money.ZERO, corrette[0].amount + corrette[1].amount)
        assertEquals(Money.of(-150), corrette[0].amount)
        assertEquals("carta_y", corrette[0].accountId)
        assertEquals("contanti", corrette[1].accountId)
        assertTrue(corrette.all { it.date == LocalDate.parse("2026-07-20") })
        assertEquals(setOf("leg0", "leg1"), corrette.map { it.id }.toSet())
        assertTrue(corrette.all { it.source == TransactionSource.TRANSFER })
    }

    @Test
    fun `le gambe si riconoscono dal segno e non dall'ordine`() {
        // Una query sul database può restituirle in qualsiasi ordine.
        val (uscita, entrata) = trasferimento()
        val a = Edits.editTransfer(listOf(uscita, entrata), "carta_y", "contanti", Money.of(150), oggi)
        val b = Edits.editTransfer(listOf(entrata, uscita), "carta_y", "contanti", Money.of(150), oggi)
        assertEquals(a, b)
    }

    @Test
    fun `un trasferimento corretto male viene rifiutato`() {
        val (uscita, entrata) = trasferimento()
        val gambe = listOf(uscita, entrata)
        assertFailsWith<IllegalArgumentException> {
            Edits.editTransfer(gambe, "carta_y", "carta_y", Money.of(50), oggi)
        }
        assertFailsWith<IllegalArgumentException> {
            Edits.editTransfer(gambe, "carta_y", "contanti", Money.ZERO, oggi)
        }
        assertFailsWith<IllegalArgumentException> {
            Edits.editTransfer(listOf(uscita), "carta_y", "contanti", Money.of(50), oggi)
        }
    }
}
