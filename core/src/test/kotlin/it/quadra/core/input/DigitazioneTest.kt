package it.quadra.core.input

import it.quadra.core.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DigitazioneTest {

    private fun digita(tasti: String): Digitazione =
        tasti.fold(Digitazione()) { stato, tasto ->
            when (tasto) {
                ',' -> stato.virgola()
                '<' -> stato.indietro()
                else -> stato.cifra(tasto)
            }
        }

    @Test
    fun `venti tasti due e zero fa venti euro, non venti centesimi`() {
        // È il punto di tutta la classe: prima si scriveva il numero al contrario.
        assertEquals(Money.of(20), digita("20").importo)
        assertEquals("20", digita("20").testo())
    }

    @Test
    fun `i centesimi arrivano solo dopo la virgola`() {
        assertEquals(Money.of(20, 50), digita("20,50").importo)
        assertEquals(Money.of(4, 5), digita("4,05").importo)
    }

    @Test
    fun `una sola cifra dopo la virgola vale decine di centesimi`() {
        // "4,5" è quattro euro e cinquanta, non quattro e cinque.
        assertEquals(Money.of(4, 50), digita("4,5").importo)
    }

    @Test
    fun `il testo mostra i tasti premuti, non l'importo formattato`() {
        // Se dopo la virgola comparisse "20,00" i due zeri sembrerebbero già inseriti.
        assertEquals("20", digita("20").testo())
        assertEquals("20,", digita("20,").testo())
        assertEquals("20,5", digita("20,5").testo())
        assertEquals("20,50", digita("20,50").testo())
    }

    @Test
    fun `oltre due decimali i tasti non fanno niente`() {
        assertEquals(Money.of(20, 50), digita("20,5099").importo)
        assertEquals("20,50", digita("20,5099").testo())
    }

    @Test
    fun `la virgola premuta due volte non cambia niente`() {
        assertEquals(digita("20,5"), digita("20,,5"))
    }

    @Test
    fun `la virgola per prima vale zero virgola`() {
        assertEquals(Money.of(0, 50), digita(",50").importo)
        assertEquals("0,50", digita(",50").testo())
    }

    @Test
    fun `si cancella nell'ordine in cui si è scritto, virgola compresa`() {
        assertEquals("20,5", digita("20,50<").testo())
        assertEquals("20,", digita("20,50<<").testo())
        assertEquals("20", digita("20,50<<<").testo())
        assertEquals("2", digita("20,50<<<<").testo())
        assertTrue(digita("20,50<<<<<").vuota)
    }

    @Test
    fun `cancellare la virgola riporta i tasti sulla parte intera`() {
        // Senza questo passo si resterebbe intrappolati nei decimali.
        val dopo = digita("20,<5")
        assertEquals(Money.of(205), dopo.importo)
        assertEquals("205", dopo.testo())
    }

    @Test
    fun `cancellare a vuoto non rompe niente`() {
        assertTrue(digita("<<<<").vuota)
        assertEquals(Money.ZERO, digita("<<<<").importo)
    }

    @Test
    fun `lo zero iniziale non si accumula`() {
        assertEquals(Money.of(5), digita("05").importo)
        assertEquals("5", digita("05").testo())
        assertEquals(Money.of(0, 50), digita("0,50").importo)
    }

    @Test
    fun `le migliaia sono raggruppate mentre si scrive`() {
        assertEquals("1.284", digita("1284").testo())
        assertEquals("1.284,60", digita("1284,60").testo())
    }

    @Test
    fun `oltre nove cifre intere i tasti non fanno niente`() {
        // Senza limite un dito appoggiato sul tasto manda l'importo fuori scala.
        val tante = digita("1234567890123")
        assertEquals(9, tante.intero.length)
        assertEquals(Money.of(123_456_789), tante.importo)
    }

    @Test
    fun `lo stato vuoto vale zero e si vede come zero`() {
        assertTrue(Digitazione().vuota)
        assertEquals(Money.ZERO, Digitazione().importo)
        assertEquals("0", Digitazione().testo())
        assertFalse(Digitazione().haVirgola)
    }

    @Test
    fun `un importo esistente si riapre come lo si era scritto`() {
        // Serve alla modifica: chi riapre 20,00 non deve trovarsi "2000" da correggere.
        assertEquals("20", Digitazione.da(Money.of(20)).testo())
        assertEquals("20,50", Digitazione.da(Money.of(20, 50)).testo())
        assertEquals("4,05", Digitazione.da(Money.of(4, 5)).testo())
        assertEquals("1.284,60", Digitazione.da(Money.of(1284, 60)).testo())
    }

    @Test
    fun `riaprendo un importo si può continuare a digitare`() {
        val ripreso = Digitazione.da(Money.of(20, 50)).indietro().cifra('9')
        assertEquals(Money.of(20, 59), ripreso.importo)
    }

    @Test
    fun `il segno non entra nella digitazione`() {
        // Uscita o entrata lo decide la categoria, non il tastierino.
        assertEquals(Money.of(20), Digitazione.da(Money.of(-20)).importo)
    }
}
