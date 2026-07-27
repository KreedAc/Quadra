package it.quadra.core.budget

import it.quadra.core.model.Money
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BudgetTest {

    private val luglio = YearMonth.of(2026, 7)

    private fun calcola(speso: Long, budget: Long, giorno: Int = 15) =
        Andamento.calcola(
            speso = Money.of(speso),
            budget = Money.of(budget),
            mese = luglio,
            oggi = LocalDate.of(2026, 7, giorno),
        )

    @Test
    fun `senza budget non c'è niente da mostrare`() {
        assertNull(calcola(speso = 100, budget = 0))
        assertNull(Andamento.calcola(Money.of(100), Money.of(-50), luglio))
    }

    @Test
    fun `quanto resta è la differenza`() {
        val a = calcola(speso = 1284, budget = 1800)!!
        assertEquals(Money.of(516), a.restano)
        assertFalse(a.sforato)
    }

    @Test
    fun `il segno dello speso non conta`() {
        // I movimenti sono negativi: chi chiama non deve ricordarsi di girarli.
        val a = Andamento.calcola(Money.of(-1284), Money.of(1800), luglio)!!
        assertEquals(Money.of(516), a.restano)
        assertEquals(Money.of(1284), a.speso)
    }

    @Test
    fun `la frazione riempie la barra in proporzione`() {
        assertEquals(0f, calcola(speso = 0, budget = 1000)!!.frazione)
        assertEquals(0.5f, calcola(speso = 500, budget = 1000)!!.frazione)
        assertEquals(1f, calcola(speso = 1000, budget = 1000)!!.frazione)
    }

    @Test
    fun `spendendo il doppio la barra resta piena, non esce dal riquadro`() {
        val a = calcola(speso = 2000, budget = 1000)!!
        assertEquals(1f, a.frazione)
        assertTrue(a.sforato)
        assertEquals(Money.of(-1000), a.restano)
    }

    @Test
    fun `chi ha sforato vede di quanto`() {
        val a = calcola(speso = 1900, budget = 1800)!!
        assertTrue(a.sforato)
        assertEquals(Money.of(-100), a.restano)
    }

    @Test
    fun `il residuo giornaliero divide per i giorni che restano, oggi compreso`() {
        // Il 15 luglio restano 17 giorni: 340 / 17 = 20 al giorno.
        val a = calcola(speso = 1460, budget = 1800, giorno = 15)!!
        assertEquals(Money.of(20), a.alGiorno)
    }

    @Test
    fun `l'ultimo giorno del mese il residuo è tutto quello che resta`() {
        val a = calcola(speso = 1750, budget = 1800, giorno = 31)!!
        assertEquals(Money.of(50), a.alGiorno)
    }

    @Test
    fun `chi ha già sforato non ha un residuo giornaliero`() {
        assertNull(calcola(speso = 2000, budget = 1800)!!.alGiorno)
    }

    @Test
    fun `guardando un mese che non è quello di oggi il residuo giornaliero non ha senso`() {
        val passato = Andamento.calcola(
            Money.of(500), Money.of(1800), YearMonth.of(2026, 3),
            oggi = LocalDate.of(2026, 7, 15),
        )!!
        assertNull(passato.alGiorno)
        // Ma quanto resta si vede lo stesso: serve a capire com'è andato quel mese.
        assertEquals(Money.of(1300), passato.restano)
    }

    @Test
    fun `un mese senza spese ha il budget intero`() {
        val a = calcola(speso = 0, budget = 1800, giorno = 1)!!
        assertEquals(Money.of(1800), a.restano)
        assertEquals(0f, a.frazione)
        // 1800 su 31 giorni.
        assertEquals(Money(180_000 / 31), a.alGiorno)
    }
}
