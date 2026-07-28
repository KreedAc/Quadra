package it.quadra.core.ledger

import it.quadra.core.model.Account
import it.quadra.core.model.AccountKind
import it.quadra.core.model.Money
import it.quadra.core.model.Transaction
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndamentoTest {

    @Test
    fun `quello che resta è il saldo, non un residuo da calcolare`() {
        val a = Andamento.calcola(speso = Money.of(300), disponibile = Money.of(855, 76))
        assertEquals(Money.of(855, 76), a.disponibile)
        assertEquals(Money.of(1155, 76), a.partenza)
    }

    @Test
    fun `il segno dello speso non conta`() {
        // I movimenti sono negativi: chi chiama non deve ricordarsi di girarli.
        val a = Andamento.calcola(Money.of(-300), Money.of(700))
        assertEquals(Money.of(300), a.speso)
        assertEquals(Money.of(1000), a.partenza)
    }

    @Test
    fun `la barra si riempie in proporzione a quello che c'era all'inizio`() {
        assertEquals(0f, Andamento.calcola(Money.ZERO, Money.of(1000)).frazione)
        assertEquals(0.5f, Andamento.calcola(Money.of(500), Money.of(500)).frazione)
        assertEquals(0.8f, Andamento.calcola(Money.of(800), Money.of(200)).frazione)
    }

    @Test
    fun `finito il denaro la barra è piena`() {
        val a = Andamento.calcola(Money.of(1000), Money.ZERO)
        assertEquals(1f, a.frazione)
        assertFalse(a.inRosso)
    }

    @Test
    fun `chi è sotto zero lo vede, e la barra non esce dal riquadro`() {
        val a = Andamento.calcola(Money.of(1200), Money.of(-200))
        assertEquals(1f, a.frazione)
        assertTrue(a.inRosso)
        assertEquals(Money.of(-200), a.disponibile)
    }

    @Test
    fun `un mese senza spese e senza soldi non divide per zero`() {
        val a = Andamento.calcola(Money.ZERO, Money.ZERO)
        assertEquals(1f, a.frazione)
        assertFalse(a.inRosso)
    }

    @Test
    fun `i conti vincolati non gonfiano la disponibilità`() {
        // È il motivo per cui si passa Totals.available e non la somma di tutto: la
        // carta di un sussidio è denaro vero ma non spendibile per il resto.
        val contanti = Account("contanti", "Contanti", AccountKind.CASH, 0, Money.of(100))
        val adi = Account(
            "adi", "Carta ADI", AccountKind.PREPAID, 0, Money.of(585, 76),
            includedInTotal = false,
        )
        val movimenti = listOf(
            Transaction("m1", Money.of(-30), LocalDate.parse("2026-07-26"), "spesa", "contanti")
        )
        val totali = Ledger.totals(listOf(contanti, adi), movimenti)
        val a = Andamento.calcola(Ledger.totalSpent(movimenti), totali.available)

        assertEquals(Money.of(70), a.disponibile)
        assertEquals(Money.of(100), a.partenza)
        assertEquals(0.3f, a.frazione)
    }

    @Test
    fun `i trasferimenti non contano come speso`() {
        // Ledger li esclude già: qui si verifica che il giro completo resti coerente.
        val da = Account("da", "Carta", AccountKind.CARD, 0, Money.of(500))
        val a = Account("a", "Contanti", AccountKind.CASH, 0, Money.ZERO)
        val (uscita, entrata) = Ledger.transfer(
            from = da, to = a, amount = Money.of(100),
            date = LocalDate.parse("2026-07-26"), groupId = "g1", idFactory = { "leg$it" },
        )
        val movimenti = listOf(uscita, entrata)
        val totali = Ledger.totals(listOf(da, a), movimenti)
        val andamento = Andamento.calcola(Ledger.totalSpent(movimenti), totali.available)

        assertEquals(Money.ZERO, andamento.speso)
        assertEquals(Money.of(500), andamento.disponibile)
        assertEquals(0f, andamento.frazione)
    }
}
