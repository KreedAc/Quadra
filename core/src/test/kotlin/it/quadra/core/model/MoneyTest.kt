package it.quadra.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {

    @Test
    fun `formatta in stile italiano`() {
        assertEquals("0,00 €", Money(0).format())
        assertEquals("12,50 €", Money(1250).format())
        assertEquals("0,05 €", Money(5).format())
        assertEquals("999,99 €", Money(99999).format())
        assertEquals("1.000,00 €", Money(100000).format())
        assertEquals("1.234,56 €", Money(123456).format())
        assertEquals("12.345,67 €", Money(1234567).format())
        assertEquals("1.234.567,89 €", Money(123456789).format())
    }

    @Test
    fun `formatta i segni`() {
        assertEquals("-12,50 €", Money(-1250).format())
        assertEquals("+12,50 €", Money(1250).format(withSign = true))
        assertEquals("-12,50 €", Money(-1250).format(withSign = true))
        assertEquals("0,00 €", Money(0).format(withSign = true))
        assertEquals("12,50", Money(1250).format(withSymbol = false))
    }

    @Test
    fun `interpreta gli importi scritti all'italiana`() {
        assertEquals(Money(1250), Money.parse("12,50"))
        assertEquals(Money(1250), Money.parse("12,5"))
        assertEquals(Money(1200), Money.parse("12"))
        assertEquals(Money(123456), Money.parse("1.234,56"))
        assertEquals(Money(123456789), Money.parse("1.234.567,89"))
        assertEquals(Money(5), Money.parse("0,05"))
        assertEquals(Money(5), Money.parse(",05"))
    }

    @Test
    fun `interpreta simboli e spaziature`() {
        assertEquals(Money(1250), Money.parse("12,50 €"))
        assertEquals(Money(1250), Money.parse("€ 12,50"))
        assertEquals(Money(1250), Money.parse("  12,50€  "))
        assertEquals(Money(1250), Money.parse("12,50 EUR"))
        assertEquals(Money(1250), Money.parse("EUR 12,50"))
        assertEquals(Money(123456), Money.parse("1.234,56 €"))
    }

    @Test
    fun `interpreta i segni in tutte le posizioni`() {
        assertEquals(Money(-1250), Money.parse("-12,50"))
        assertEquals(Money(-1250), Money.parse("- 12,50"))
        assertEquals(Money(1250), Money.parse("+12,50"))
        assertEquals(Money(-1250), Money.parse("12,50-"))
        assertEquals(Money(-1250), Money.parse("(12,50)"))
        assertEquals(Money(-1250), Money.parse("-12,50 €"))
    }

    @Test
    fun `distingue il punto decimale dal separatore delle migliaia`() {
        // Il caso ambiguo: tre cifre dopo il punto significano migliaia.
        assertEquals(Money(123400), Money.parse("1.234"))
        // Una o due cifre significano decimali, come nei CSV in formato anglosassone.
        assertEquals(Money(1250), Money.parse("12.50"))
        assertEquals(Money(1250), Money.parse("12.5"))
        // Con entrambi i separatori vince l'ultimo come decimale.
        assertEquals(Money(123456), Money.parse("1,234.56"))
        assertEquals(Money(123456), Money.parse("1.234,56"))
    }

    @Test
    fun `arrotonda oltre il centesimo`() {
        assertEquals(Money(1250), Money.parse("12,499"))
        assertEquals(Money(1250), Money.parse("12,495"))
        assertEquals(Money(1249), Money.parse("12,494"))
        assertEquals(Money(-1250), Money.parse("-12,495"))
    }

    @Test
    fun `rifiuta ciò che non è un importo`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("   "))
        assertNull(Money.parse("€"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("12,5a"))
        assertNull(Money.parse("1a2,50"))
        assertNull(Money.parse("-"))
    }

    @Test
    fun `il giro completo scrittura-lettura è stabile`() {
        listOf(0L, 1L, 99L, 100L, 1250L, -1250L, 123456789L, -123456789L).forEach { cents ->
            val money = Money(cents)
            assertEquals(money, Money.parse(money.format()), "giro fallito per $cents")
        }
    }

    @Test
    fun `aritmetica e segno`() {
        assertEquals(Money(1500), Money(1000) + Money(500))
        assertEquals(Money(500), Money(1000) - Money(500))
        assertEquals(Money(-500), Money(500) - Money(1000))
        assertEquals(Money(3000), Money(1000) * 3)
        assertEquals(Money(-1000), -Money(1000))
        assertEquals(Money(1000), Money(-1000).abs())
        assertEquals(Money(-1000), Money(1000).asExpense())
        assertEquals(Money(-1000), Money(-1000).asExpense())
        assertEquals(Money(1000), Money(-1000).asIncome())
    }

    @Test
    fun `somma di una lista tiene conto dei segni`() {
        val movimenti = listOf(Money(-1250), Money(-800), Money(200000))
        assertEquals(Money(197950), movimenti.sum())
        assertEquals(Money.ZERO, emptyList<Money>().sum())
    }

    @Test
    fun `costruzione da unità e centesimi`() {
        assertEquals(Money(1250), Money.of(12, 50))
        assertEquals(Money(1200), Money.of(12))
        assertEquals(Money(-1250), Money.of(-12, 50))
    }
}
