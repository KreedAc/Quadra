package it.quadra.core.input

import it.quadra.core.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DigitazioneTest {

    private fun digita(tasti: String): Digitazione =
        tasti.fold(Digitazione()) { stato, tasto ->
            when (tasto) {
                ',' -> stato.virgola()
                '<' -> stato.indietro()
                '+' -> stato.operazione(Operazione.PIU)
                '-' -> stato.operazione(Operazione.MENO)
                '*' -> stato.operazione(Operazione.PER)
                '/' -> stato.operazione(Operazione.DIVISO)
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

class OperazioniTest {

    private fun digita(tasti: String): Digitazione =
        tasti.fold(Digitazione()) { stato, tasto ->
            when (tasto) {
                ',' -> stato.virgola()
                '<' -> stato.indietro()
                '+' -> stato.operazione(Operazione.PIU)
                '-' -> stato.operazione(Operazione.MENO)
                '*' -> stato.operazione(Operazione.PER)
                '/' -> stato.operazione(Operazione.DIVISO)
                else -> stato.cifra(tasto)
            }
        }

    @Test
    fun `la colazione al bar si somma senza farla a mente`() {
        // Il caso vero: caffè, cornetto e spremuta pagati in una volta sola.
        assertEquals(Money.of(8, 30), digita("4,5+1,4+2,4").importo)
    }

    @Test
    fun `la formula mostra i termini come sono stati scritti`() {
        assertEquals("4,5 + 1,4 + 2,4", digita("4,5+1,4+2,4").formula)
        // Senza operazioni non c'è niente da mostrare: resta il numero e basta.
        assertNull(digita("20").formula)
    }

    @Test
    fun `il totale si vede già prima di scrivere il termine dopo`() {
        // Premuto il più, il conto vale quello che c'è: moltiplicare per zero un termine
        // ancora da scrivere azzererebbe tutto mentre si sta ancora digitando.
        assertEquals(Money.of(5, 90), digita("4,5+1,4+").importo)
        assertEquals(Money.of(4, 50), digita("4,5*").importo)
    }

    @Test
    fun `la sottrazione toglie`() {
        assertEquals(Money.of(7, 50), digita("10-2,5").importo)
    }

    @Test
    fun `tre caffè si moltiplicano`() {
        assertEquals(Money.of(3, 60), digita("1,2*3").importo)
    }

    @Test
    fun `il conto diviso in tre arrotonda al centesimo`() {
        assertEquals(Money.of(3, 33), digita("10/3").importo)
        assertEquals(Money.of(10), digita("30/3").importo)
    }

    @Test
    fun `dividere per zero lascia il conto com'era invece di esplodere`() {
        assertEquals(Money.of(10), digita("10/0").importo)
        assertEquals(Money.of(10), digita("10/").importo)
    }

    @Test
    fun `le operazioni si concatenano da sinistra`() {
        // Nessuna precedenza: si legge come una striscia di scontrino, non come algebra.
        assertEquals(Money.of(9), digita("10-4+3").importo)
        assertEquals(Money.of(6), digita("1+1*3").importo)
    }

    @Test
    fun `premere due operatori di fila cambia idea invece di sbagliare`() {
        assertEquals(Money.of(7, 50), digita("10+-2,5").importo)
        assertEquals("10 −", digita("10+-").formula)
    }

    @Test
    fun `cancellare su un termine vuoto annulla l'operazione`() {
        // Senza, chi preme il più per sbaglio resta bloccato con un operatore appeso.
        val dopo = digita("4,5+<")
        assertEquals(Money.of(4, 50), dopo.importo)
        assertNull(dopo.formula)
        assertEquals("4,50", dopo.testo())
    }

    @Test
    fun `cancellare dentro un termine non tocca il conto già fatto`() {
        val dopo = digita("4,5+1,45<")
        assertEquals("4,5 + 1,4", dopo.formula)
        assertEquals(Money.of(5, 90), dopo.importo)
    }

    @Test
    fun `un risultato negativo non è salvabile`() {
        // 4,50 meno 10 non è una spesa: il pulsante resta spento invece di scrivere
        // un movimento che non ha senso.
        val sottozero = digita("4,5-10")
        assertEquals(Money.of(-5, 50), sottozero.importo)
        assertFalse(sottozero.valido)
        assertTrue(digita("4,5+1,4").valido)
        assertFalse(digita("").valido)
    }

    @Test
    fun `azzerare toglie anche le operazioni in corso`() {
        assertEquals(Digitazione(), digita("4,5+1,4").azzera())
    }

    @Test
    fun `il denaro resta in centesimi anche passando per le operazioni`() {
        // Nessun Double di mezzo: 0,1 + 0,2 deve fare esattamente 0,30.
        assertEquals(Money.of(0, 30), digita("0,1+0,2").importo)
        assertEquals(Money.of(0, 1), digita("0,03/3").importo)
    }
}
