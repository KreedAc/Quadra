package it.lemiespese.core.ledger

import it.lemiespese.core.model.Account
import it.lemiespese.core.model.AccountKind
import it.lemiespese.core.model.Money
import it.lemiespese.core.model.Transaction
import it.lemiespese.core.model.TransactionSource
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerTest {

    private val oggi = LocalDate.parse("2026-07-26")

    private val contanti = Account(
        id = "contanti", name = "Contanti", kind = AccountKind.CASH,
        colorArgb = 0, openingBalance = Money.of(290),
    )
    private val carta = Account(
        id = "carta", name = "Revolut", kind = AccountKind.CARD,
        colorArgb = 0, openingBalance = Money.of(500),
    )

    /** Denaro vincolato: c'è, si vede, ma non è liberamente spendibile. */
    private val adi = Account(
        id = "adi", name = "Carta ADI", kind = AccountKind.PREPAID,
        colorArgb = 0, openingBalance = Money.of(380), includedInTotal = false,
    )

    private fun spesa(cents: Long, account: String = "carta", categoria: String = "spesa") =
        Transaction(
            id = "t${cents}_$account", amount = Money(cents), date = oggi,
            categoryId = categoria, accountId = account,
        )

    // ---------------------------------------------------------------- saldi

    @Test
    fun `il saldo è apertura più movimenti del solo conto`() {
        val movimenti = listOf(spesa(-5000, "carta"), spesa(-1000, "contanti"), spesa(-2000, "carta"))
        assertEquals(Money.of(430), Ledger.balanceOf(carta, movimenti))
        assertEquals(Money.of(280), Ledger.balanceOf(contanti, movimenti))
    }

    @Test
    fun `il saldo di un conto senza movimenti è la sola apertura`() {
        assertEquals(Money.of(380), Ledger.balanceOf(adi, emptyList()))
    }

    @Test
    fun `balances calcola tutti i conti in un colpo solo`() {
        val movimenti = listOf(spesa(-5000, "carta"), spesa(-1000, "contanti"))
        val saldi = Ledger.balances(listOf(contanti, carta, adi), movimenti)
        assertEquals(Money.of(280), saldi["contanti"])
        assertEquals(Money.of(450), saldi["carta"])
        assertEquals(Money.of(380), saldi["adi"])
    }

    // -------------------------------------------------- disponibile e vincolato

    @Test
    fun `il denaro vincolato non entra nella disponibilità`() {
        // È il caso della carta ADI: quei soldi esistono ma non si possono usare per
        // qualsiasi cosa, e sommarli fa credere di avere più di quanto si ha davvero.
        val totali = Ledger.totals(listOf(contanti, carta, adi), emptyList())
        assertEquals(Money.of(790), totali.available)
        assertEquals(Money.of(380), totali.constrained)
        assertEquals(Money.of(1170), totali.overall)
    }

    @Test
    fun `i conti archiviati non contano in nessuno dei due totali`() {
        val totali = Ledger.totals(listOf(contanti, carta.copy(archived = true), adi), emptyList())
        assertEquals(Money.of(290), totali.available)
        assertEquals(Money.of(380), totali.constrained)
    }

    // ------------------------------------------------------------ trasferimenti

    @Test
    fun `un trasferimento produce due gambe che si annullano`() {
        val (uscita, entrata) = Ledger.transfer(
            from = carta, to = contanti, amount = Money.of(100), date = oggi,
            groupId = "g1", idFactory = { "leg$it" },
        )
        assertEquals(Money.of(-100), uscita.amount)
        assertEquals(Money.of(100), entrata.amount)
        assertEquals(Money.ZERO, uscita.amount + entrata.amount)
        assertEquals("carta", uscita.accountId)
        assertEquals("contanti", entrata.accountId)
        assertEquals("g1", uscita.transferGroupId)
        assertEquals("g1", entrata.transferGroupId)
        assertTrue(uscita.isTransfer && entrata.isTransfer)
        assertTrue(uscita.source == TransactionSource.TRANSFER)
    }

    @Test
    fun `il segno dell'importo passato non conta`() {
        val (uscita, _) = Ledger.transfer(
            from = carta, to = contanti, amount = Money.of(-100), date = oggi,
            groupId = "g", idFactory = { "l$it" },
        )
        assertEquals(Money.of(-100), uscita.amount)
    }

    @Test
    fun `il prelievo al bancomat è un trasferimento e sposta il denaro`() {
        // Nessuna operazione "preleva" separata: prelevare è spostare dal conto ai
        // contanti. Due nomi per la stessa cosa costringono solo a scegliere ogni volta.
        val (uscita, entrata) = Ledger.transfer(
            from = carta, to = contanti, amount = Money.of(200), date = oggi,
            groupId = "g", idFactory = { "l$it" },
        )
        val movimenti = listOf(uscita, entrata)
        assertEquals(Money.of(300), Ledger.balanceOf(carta, movimenti))
        assertEquals(Money.of(490), Ledger.balanceOf(contanti, movimenti))
    }

    @Test
    fun `un trasferimento non cambia il patrimonio complessivo`() {
        val (uscita, entrata) = Ledger.transfer(
            from = carta, to = contanti, amount = Money.of(200), date = oggi,
            groupId = "g", idFactory = { "l$it" },
        )
        val prima = Ledger.totals(listOf(contanti, carta), emptyList())
        val dopo = Ledger.totals(listOf(contanti, carta), listOf(uscita, entrata))
        assertEquals(prima.overall, dopo.overall)
        assertEquals(prima.available, dopo.available)
    }

    @Test
    fun `un trasferimento non è una spesa`() {
        // Il difetto più insidioso di questo genere di app: senza questa regola,
        // spostare 200 € risulterebbe come 200 € spesi.
        val (uscita, entrata) = Ledger.transfer(
            from = carta, to = contanti, amount = Money.of(200), date = oggi,
            groupId = "g", idFactory = { "l$it" },
        )
        val movimenti = listOf(spesa(-4780), uscita, entrata)

        assertEquals(Money.of(47, 80), Ledger.totalSpent(movimenti))
        assertEquals(listOf("t-4780_carta"), Ledger.spending(movimenti).map { it.id })
    }

    @Test
    fun `trasferire su sé stessi o a zero è un errore`() {
        assertFailsWith<IllegalArgumentException> {
            Ledger.transfer(carta, carta, Money.of(10), oggi, groupId = "g", idFactory = { "l$it" })
        }
        assertFailsWith<IllegalArgumentException> {
            Ledger.transfer(carta, contanti, Money.ZERO, oggi, groupId = "g", idFactory = { "l$it" })
        }
    }

    // -------------------------------------------------------------- rettifiche

    @Test
    fun `allineare il saldo genera la differenza invece di sovrascrivere`() {
        // Saldo calcolato 450, reale 437,50: mancano 12,50 che non erano stati segnati.
        val movimenti = listOf(spesa(-5000, "carta"))
        val rettifica = Ledger.reconcile(carta, movimenti, Money.of(437, 50), oggi, "r1")

        assertNotNull(rettifica)
        assertEquals(Money.of(-12, 50), rettifica.amount)
        assertEquals("carta", rettifica.accountId)
        assertEquals(Ledger.ADJUSTMENT_CATEGORY_ID, rettifica.categoryId)
        assertEquals(TransactionSource.ADJUSTMENT, rettifica.source)
        // E dopo la rettifica il saldo torna quello vero.
        assertEquals(Money.of(437, 50), Ledger.balanceOf(carta, movimenti + rettifica))
    }

    @Test
    fun `l'utente scrive il saldo reale, mai la differenza`() {
        // È il punto di tutta l'operazione: guardi l'app della banca, leggi il numero,
        // lo scrivi. Il calcolo — comprese le virgole, che a mente sono la parte
        // fastidiosa — lo fa la funzione.
        val movimenti = listOf(spesa(-12463, "carta"))   // saldo calcolato: 375,37
        val rettifica = Ledger.reconcile(carta, movimenti, Money.of(362, 92), oggi, "r")

        assertNotNull(rettifica)
        assertEquals(Money.of(-12, 45), rettifica.amount)
        assertEquals(Money.of(362, 92), Ledger.balanceOf(carta, movimenti + rettifica))
    }

    @Test
    fun `la rettifica funziona anche in aumento`() {
        val rettifica = Ledger.reconcile(carta, emptyList(), Money.of(520), oggi, "r")
        assertNotNull(rettifica)
        assertEquals(Money.of(20), rettifica.amount)
    }

    @Test
    fun `allineare un saldo già giusto non crea nulla`() {
        assertNull(Ledger.reconcile(carta, emptyList(), Money.of(500), oggi, "r"))
    }

    @Test
    fun `la rettifica resta visibile nelle spese`() {
        // Sovrascrivere il saldo farebbe tornare il conto e mentire alle statistiche.
        // Così invece la differenza è tracciata: se la voce cresce, significa che si
        // sta dimenticando di segnare.
        val rettifica = Ledger.reconcile(carta, emptyList(), Money.of(487, 50), oggi, "r")!!
        assertFalse(rettifica.isTransfer)
        assertEquals(Money.of(12, 50), Ledger.totalSpent(listOf(rettifica)))
    }

    // ------------------------------------------------------------- aggregazioni

    @Test
    fun `speso ed entrato si contano separatamente`() {
        val movimenti = listOf(
            spesa(-4780), spesa(-6200), spesa(214000, "carta", "entrate.stipendio"),
        )
        assertEquals(Money.of(109, 80), Ledger.totalSpent(movimenti))
        assertEquals(Money.of(2140), Ledger.totalEarned(movimenti))
    }

    @Test
    fun `senza movimenti i totali sono zero e non esplodono`() {
        assertEquals(Money.ZERO, Ledger.totalSpent(emptyList()))
        assertEquals(Money.ZERO, Ledger.totalEarned(emptyList()))
        assertEquals(Money.ZERO, Ledger.totals(emptyList(), emptyList()).overall)
    }
}
