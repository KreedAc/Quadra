package it.quadra.core.scadenze

import it.quadra.core.model.Money
import it.quadra.core.model.RecurrenceUnit
import it.quadra.core.model.RecurringRule
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScadenzeTest {

    private val oggi = LocalDate.parse("2026-07-20")

    private fun regola(
        id: String = "affitto",
        descrizione: String = "Affitto",
        inizio: String = "2026-01-15",
        ogni: Int = 1,
        unita: RecurrenceUnit = RecurrenceUnit.MONTH,
        giorno: Int? = 15,
        saltate: Set<LocalDate> = emptySet(),
        rimandi: Map<LocalDate, LocalDate> = emptyMap(),
        attiva: Boolean = true,
        creata: String? = null,
    ) = RecurringRule(
        id = id,
        description = descrizione,
        amount = Money.of(-550),
        categoryId = "casa.affitto",
        accountId = "carta",
        every = ogni,
        unit = unita,
        startDate = LocalDate.parse(inizio),
        dayOfMonth = giorno,
        active = attiva,
        skippedDates = saltate,
        rimandi = rimandi,
        creatoIl = creata?.let(LocalDate::parse) ?: LocalDate.parse(inizio),
    )

    // ------------------------------------------------------- da confermare

    @Test
    fun `una scadenza passata e mai registrata aspetta una conferma`() {
        val attese = Scadenze.daConfermare(listOf(regola(inizio = "2026-07-15")), emptySet(), oggi)
        assertEquals(1, attese.size)
        assertEquals(LocalDate.parse("2026-07-15"), attese[0].occorrenza)
        assertEquals(5, attese[0].giorniDiRitardo)
    }

    @Test
    fun `la scadenza di oggi si chiede oggi, senza ritardo`() {
        val attese = Scadenze.daConfermare(listOf(regola(inizio = "2026-07-20", giorno = 20)), emptySet(), oggi)
        assertEquals(1, attese.size)
        assertEquals(0, attese[0].giorniDiRitardo)
    }

    @Test
    fun `una scadenza futura non si chiede`() {
        val attese = Scadenze.daConfermare(listOf(regola(inizio = "2026-07-25", giorno = 25)), emptySet(), oggi)
        assertTrue(attese.isEmpty())
    }

    @Test
    fun `una scadenza già registrata non torna`() {
        // È il punto per cui la chiave esiste: cancellare il movimento la rimette in
        // attesa, e registrarla la toglie, senza nessuno stato da tenere allineato.
        val r = regola(inizio = "2026-07-15")
        val chiave = Scadenze.chiave(r.id, LocalDate.parse("2026-07-15"))
        assertTrue(Scadenze.daConfermare(listOf(r), setOf(chiave), oggi).isEmpty())
        assertEquals(1, Scadenze.daConfermare(listOf(r), emptySet(), oggi).size)
    }

    @Test
    fun `una scadenza saltata non torna`() {
        val saltata = LocalDate.parse("2026-07-15")
        val attese = Scadenze.daConfermare(listOf(regola(saltate = setOf(saltata))), emptySet(), oggi)
        assertTrue(attese.none { it.occorrenza == saltata })
    }

    @Test
    fun `gli arretrati arrivano in ordine, dal più vecchio`() {
        // Sei mesi di affitto mai confermati: si parte da gennaio.
        val attese = Scadenze.daConfermare(listOf(regola()), emptySet(), oggi)
        assertEquals(7, attese.size)
        assertEquals(LocalDate.parse("2026-01-15"), attese.first().occorrenza)
        assertEquals(LocalDate.parse("2026-07-15"), attese.last().occorrenza)
    }

    @Test
    fun `oltre un anno di arretrati non si propone`() {
        // Una regola vecchia di tre anni non deve aprire trentasei conferme in fila.
        val attese = Scadenze.daConfermare(listOf(regola(inizio = "2023-01-15")), emptySet(), oggi)
        assertEquals(12, attese.size)
        assertTrue(attese.first().occorrenza.isAfter(oggi.minusDays(Scadenze.GIORNI_ARRETRATI + 1)))
    }

    @Test
    fun `una regola spenta non chiede niente`() {
        assertTrue(Scadenze.daConfermare(listOf(regola(attiva = false)), emptySet(), oggi).isEmpty())
    }

    @Test
    fun `l'assicurazione ogni quattro mesi cade nei mesi giusti`() {
        // Il caso vero: tre rate all'anno, e quali tre dipende da quando parte la prima.
        // Senza poter scegliere il mese di partenza, "ogni 4 mesi" non direbbe quali.
        val assicurazione = regola(
            id = "assic", descrizione = "Assicurazione auto",
            inizio = "2026-03-12", ogni = 4, giorno = 12,
        )
        val entroUnAnno = Scadenze.daConfermare(
            listOf(assicurazione), emptySet(), LocalDate.parse("2027-03-11"),
        )
        assertEquals(
            listOf("2026-03-12", "2026-07-12", "2026-11-12", "2027-03-12").dropLast(1).map(LocalDate::parse),
            entroUnAnno.map { it.occorrenza },
        )
    }

    @Test
    fun `partire da un mese diverso sposta tutte le scadenze`() {
        val aprile = regola(id = "a", inizio = "2026-04-12", ogni = 4, giorno = 12)
        val maggio = regola(id = "m", inizio = "2026-05-12", ogni = 4, giorno = 12)
        val fine = LocalDate.parse("2026-12-31")
        assertEquals(
            listOf("2026-04-12", "2026-08-12", "2026-12-12").map(LocalDate::parse),
            Scadenze.daConfermare(listOf(aprile), emptySet(), fine).map { it.occorrenza },
        )
        assertEquals(
            listOf("2026-05-12", "2026-09-12").map(LocalDate::parse),
            Scadenze.daConfermare(listOf(maggio), emptySet(), fine).map { it.occorrenza },
        )
    }

    @Test
    fun `una scadenza a quattro mesi partita dal 31 non scivola`() {
        // Il 31 luglio esiste, il 30 novembre no: la regola torna al 31 a marzo invece
        // di restare inchiodata al 30 per sempre.
        val r = regola(inizio = "2026-07-31", ogni = 4, giorno = 31)
        val date = Scadenze.daConfermare(listOf(r), emptySet(), LocalDate.parse("2027-04-01"))
            .map { it.occorrenza }
        assertEquals(
            listOf("2026-07-31", "2026-11-30", "2027-03-31").map(LocalDate::parse),
            date,
        )
    }

    @Test
    fun `una regola creata oggi non chiede conto delle scadenze prima di sé`() {
        // Chi scrive oggi che l'assicurazione parte dal 2 luglio sta descrivendo un
        // impegno, non confessando di aver saltato la rata di luglio.
        val appena = regola(inizio = "2026-01-15", creata = oggi.toString())
        assertTrue(Scadenze.daConfermare(listOf(appena), emptySet(), oggi).isEmpty())
    }

    @Test
    fun `gli arretrati veri restano, perché l'app può non essere stata aperta`() {
        // Regola creata a gennaio, app non aperta da allora: tutto quello che è maturato
        // nel frattempo deve comparire.
        val vecchia = regola(inizio = "2026-01-15", creata = "2026-01-15")
        assertEquals(7, Scadenze.daConfermare(listOf(vecchia), emptySet(), oggi).size)
    }

    // ------------------------------------------------------------- rinvii

    @Test
    fun `una scadenza rimandata sparisce finché non arriva il giorno scelto`() {
        val r = Scadenze.rimanda(regola(inizio = "2026-07-15"), LocalDate.parse("2026-07-15"), 3, oggi)
        assertTrue(Scadenze.daConfermare(listOf(r), emptySet(), oggi).isEmpty())
        assertTrue(Scadenze.daConfermare(listOf(r), emptySet(), oggi.plusDays(2)).isEmpty())
        assertEquals(1, Scadenze.daConfermare(listOf(r), emptySet(), oggi.plusDays(3)).size)
    }

    @Test
    fun `rimandare non sposta la cadenza`() {
        // È la proprietà che tiene in piedi tutto: rinviare l'affitto di tre giorni non
        // deve farlo diventare una spesa del 18 di ogni mese.
        val r = Scadenze.rimanda(regola(), LocalDate.parse("2026-07-15"), 3, oggi)
        val agosto = Scadenze.daConfermare(listOf(r), emptySet(), LocalDate.parse("2026-08-20"))
        assertTrue(agosto.any { it.occorrenza == LocalDate.parse("2026-08-15") })
        assertTrue(agosto.none { it.occorrenza == LocalDate.parse("2026-08-18") })
    }

    @Test
    fun `il rinvio vale solo per la scadenza rimandata`() {
        val r = Scadenze.rimanda(regola(), LocalDate.parse("2026-07-15"), 30, oggi)
        val attese = Scadenze.daConfermare(listOf(r), emptySet(), oggi)
        assertTrue(attese.none { it.occorrenza == LocalDate.parse("2026-07-15") })
        assertTrue(attese.any { it.occorrenza == LocalDate.parse("2026-06-15") })
    }

    @Test
    fun `una rimandata resta visibile, con il giorno in cui tornerà`() {
        // Rimandare non è cancellare: una scadenza che sparisce dalla schermata dopo un
        // rinvio è indistinguibile da una persa, e non si può nemmeno cambiare idea.
        val occorrenza = LocalDate.parse("2026-07-15")
        val r = Scadenze.rimanda(regola(inizio = "2026-07-15"), occorrenza, 3, oggi)
        assertTrue(Scadenze.daConfermare(listOf(r), emptySet(), oggi).isEmpty())

        val rimandate = Scadenze.rimandate(listOf(r), emptySet(), oggi)
        assertEquals(1, rimandate.size)
        assertEquals(occorrenza, rimandate[0].occorrenza)
        assertEquals(oggi.plusDays(3), rimandate[0].rimandataAl)
        assertFalse(rimandate[0].inRitardo)
    }

    @Test
    fun `arrivato il giorno, la rimandata torna fra quelle da confermare`() {
        val r = Scadenze.rimanda(regola(inizio = "2026-07-15"), LocalDate.parse("2026-07-15"), 3, oggi)
        val giorno = oggi.plusDays(3)
        assertTrue(Scadenze.rimandate(listOf(r), emptySet(), giorno).isEmpty())
        assertEquals(1, Scadenze.daConfermare(listOf(r), emptySet(), giorno).size)
    }

    @Test
    fun `registrare una rimandata la toglie da entrambe le liste`() {
        val r = Scadenze.rimanda(regola(inizio = "2026-07-15"), LocalDate.parse("2026-07-15"), 3, oggi)
        val chiave = Scadenze.chiave(r.id, LocalDate.parse("2026-07-15"))
        assertTrue(Scadenze.rimandate(listOf(r), setOf(chiave), oggi).isEmpty())
        assertTrue(Scadenze.daConfermare(listOf(r), setOf(chiave), oggi).isEmpty())
    }

    @Test
    fun `saltare toglie anche il rinvio`() {
        val occorrenza = LocalDate.parse("2026-07-15")
        val r = Scadenze.salta(Scadenze.rimanda(regola(), occorrenza, 3, oggi), occorrenza)
        assertTrue(occorrenza in r.skippedDates)
        assertTrue(occorrenza !in r.rimandi)
    }

    // ---------------------------------------------------------- in arrivo

    @Test
    fun `in arrivo mostra il futuro vicino e non il passato`() {
        val condominio = regola(id = "cond", descrizione = "Condominio", inizio = "2026-07-25", giorno = 25)
        val prossime = Scadenze.inArrivo(listOf(regola(), condominio), emptySet(), giorni = 10, oggi = oggi)
        assertEquals(1, prossime.size)
        assertEquals(LocalDate.parse("2026-07-25"), prossime[0].occorrenza)
        assertEquals("Condominio", prossime[0].regola.description)
    }

    @Test
    fun `in arrivo non arriva oltre la finestra`() {
        val lontana = regola(inizio = "2026-08-15", giorno = 15)
        assertTrue(Scadenze.inArrivo(listOf(lontana), emptySet(), giorni = 10, oggi = oggi).isEmpty())
        assertEquals(1, Scadenze.inArrivo(listOf(lontana), emptySet(), giorni = 30, oggi = oggi).size)
    }

    @Test
    fun `in arrivo non ripropone quello che è già stato registrato`() {
        val r = regola(inizio = "2026-07-25", giorno = 25)
        val chiave = Scadenze.chiave(r.id, LocalDate.parse("2026-07-25"))
        assertTrue(Scadenze.inArrivo(listOf(r), setOf(chiave), giorni = 10, oggi = oggi).isEmpty())
    }

    // ------------------------------------------------------------ chiavi

    @Test
    fun `la chiave distingue regola e occorrenza`() {
        assertEquals("ric:affitto:2026-07-15", Scadenze.chiave("affitto", LocalDate.parse("2026-07-15")))
        val luglio = Scadenze.chiave("affitto", LocalDate.parse("2026-07-15"))
        val agosto = Scadenze.chiave("affitto", LocalDate.parse("2026-08-15"))
        assertTrue(luglio != agosto)
    }

    @Test
    fun `pagare in ritardo resta la rata del giorno previsto`() {
        // La chiave non contiene la data del pagamento: la rata del 15 pagata il 17 è
        // sempre la rata del 15, altrimenti tornerebbe a chiedere.
        val r = regola(inizio = "2026-07-15")
        val chiave = Scadenze.chiave(r.id, LocalDate.parse("2026-07-15"))
        val dopo = Scadenze.daConfermare(listOf(r), setOf(chiave), LocalDate.parse("2026-07-17"))
        assertTrue(dopo.isEmpty())
    }
}
