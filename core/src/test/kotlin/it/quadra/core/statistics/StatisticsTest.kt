package it.quadra.core.statistics

import it.quadra.core.ledger.Ledger
import it.quadra.core.model.Account
import it.quadra.core.model.AccountKind
import it.quadra.core.model.DefaultCategories
import it.quadra.core.model.Money
import it.quadra.core.model.Transaction
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatisticsTest {

    private val luglio = YearMonth.of(2026, 7)

    private fun mov(cents: Long, categoria: String, giorno: String = "2026-07-10") = Transaction(
        id = "t$cents$categoria$giorno", amount = Money(cents),
        date = LocalDate.parse(giorno), categoryId = categoria, accountId = "carta",
    )

    private val radice = { id: String -> DefaultCategories.rootOf(id)?.id }

    // ──────────────────────────────────────────── dentro una categoria

    @Test
    fun `una famiglia si scompone nelle sue voci, dalla piu pesante`() {
        val movimenti = listOf(
            mov(-1500, "trasporti.pedaggi_e_parcheggi"),
            mov(-6200, "trasporti.carburante"),
            mov(-4780, "spesa.supermercato"),
        )
        val dentro = Statistics.bySubcategory(movimenti, "trasporti", radice)

        assertEquals(listOf("trasporti.carburante", "trasporti.pedaggi_e_parcheggi"), dentro.map { it.categoryId })
        assertEquals(Money(-6200).abs(), dentro[0].total)
        // La spesa di un'altra famiglia non entra nel conto.
        assertEquals(2, dentro.size)
    }

    @Test
    fun `le quote sono sul totale della famiglia, non su quello del mese`() {
        // Aperta una categoria si sta guardando dentro quella: barre riferite alla
        // spesa complessiva del mese non tornerebbero con il numero scritto sopra.
        val movimenti = listOf(
            mov(-7500, "trasporti.carburante"),
            mov(-2500, "trasporti.pedaggi_e_parcheggi"),
            mov(-90000, "casa.affitto"),
        )
        val dentro = Statistics.bySubcategory(movimenti, "trasporti", radice)

        assertEquals(0.75, dentro[0].share, 0.0001)
        assertEquals(0.25, dentro[1].share, 0.0001)
        assertEquals(1.0, dentro.sumOf { it.share }, 0.0001)
    }

    @Test
    fun `la spesa messa sulla radice non sparisce`() {
        // Chi sceglie "Trasporti" senza dire quale: quei soldi restano nel conto, con
        // l'identificativo della radice.
        val movimenti = listOf(
            mov(-3000, "trasporti"),
            mov(-1000, "trasporti.carburante"),
        )
        val dentro = Statistics.bySubcategory(movimenti, "trasporti", radice)

        assertEquals(listOf("trasporti", "trasporti.carburante"), dentro.map { it.categoryId })
        assertEquals(Money.of(30), dentro[0].total)
        assertEquals(Money.of(10), dentro[1].total)
    }

    @Test
    fun `una famiglia senza spese non ha voci`() {
        val movimenti = listOf(mov(-1000, "casa.affitto"))
        assertTrue(Statistics.bySubcategory(movimenti, "trasporti", radice).isEmpty())
    }

    @Test
    fun `i trasferimenti restano fuori anche qui`() {
        val carta = Account("carta", "Carta", AccountKind.CARD, 0)
        val contanti = Account("contanti", "Contanti", AccountKind.CASH, 0)
        val (uscita, entrata) = Ledger.transfer(
            from = carta, to = contanti, amount = Money.of(200),
            date = LocalDate.parse("2026-07-10"), groupId = "g", idFactory = { "l$it" },
        )
        val movimenti = listOf(mov(-1000, "trasporti.carburante"), uscita, entrata)

        val dentro = Statistics.bySubcategory(movimenti, "trasporti", radice)
        assertEquals(1, dentro.size)
        assertEquals(Money.of(10), dentro[0].total)
    }

    // ─────────────────────────────────────────────────── per categoria

    @Test
    fun `le sottocategorie si sommano nella loro radice`() {
        // Chi guarda le statistiche vuole sapere quanto è andato in Trasporti,
        // non quanto in "Pedaggi e parcheggi".
        val movimenti = listOf(
            mov(-6200, "trasporti.carburante"),
            mov(-1500, "trasporti.pedaggi_e_parcheggi"),
            mov(-4780, "spesa.supermercato"),
        )
        val per = Statistics.byRootCategory(movimenti, radice)

        assertEquals(listOf("trasporti", "spesa"), per.map { it.categoryId })
        assertEquals(Money.of(77), per[0].total)
        assertEquals(Money.of(47, 80), per[1].total)
    }

    @Test
    fun `le quote sommano a uno`() {
        val movimenti = listOf(
            mov(-7500, "casa.affitto"),
            mov(-2500, "spesa.supermercato"),
        )
        val per = Statistics.byRootCategory(movimenti, radice)
        assertEquals(0.75, per[0].share, 1e-9)
        assertEquals(0.25, per[1].share, 1e-9)
        assertEquals(1.0, per.sumOf { it.share }, 1e-9)
    }

    @Test
    fun `l'ordine va dalla categoria più pesante alla più leggera`() {
        val movimenti = listOf(
            mov(-1000, "bar.caffe"),
            mov(-9000, "casa.affitto"),
            mov(-5000, "salute.farmacia"),
        )
        assertEquals(
            listOf("casa", "salute", "bar"),
            Statistics.byRootCategory(movimenti, radice).map { it.categoryId },
        )
    }

    @Test
    fun `i trasferimenti non compaiono nelle statistiche`() {
        // Spostare denaro fra i propri conti non è spesa: se entrasse qui,
        // ogni grafico risulterebbe gonfiato del doppio dell'importo spostato.
        val contanti = Account("contanti", "Contanti", AccountKind.CASH, 0)
        val carta = Account("carta", "Carta", AccountKind.CARD, 0)
        val (uscita, entrata) = Ledger.transfer(
            from = carta, to = contanti, amount = Money.of(200),
            date = LocalDate.parse("2026-07-10"), groupId = "g", idFactory = { "l$it" },
        )
        val movimenti = listOf(mov(-4780, "spesa.supermercato"), uscita, entrata)

        val per = Statistics.byRootCategory(movimenti, radice)
        assertEquals(listOf("spesa"), per.map { it.categoryId })
        assertEquals(Money.of(47, 80), per[0].total)
    }

    @Test
    fun `le entrate non riducono la spesa per categoria`() {
        val movimenti = listOf(
            mov(-4780, "spesa.supermercato"),
            mov(214000, "entrate.stipendio"),
        )
        val per = Statistics.byRootCategory(movimenti, radice)
        assertEquals(listOf("spesa"), per.map { it.categoryId })
    }

    @Test
    fun `senza movimenti non esplode`() {
        assertTrue(Statistics.byRootCategory(emptyList(), radice).isEmpty())
    }

    @Test
    fun `una categoria senza radice nota resta com'è`() {
        val per = Statistics.byRootCategory(listOf(mov(-1000, "categoria.inventata")), radice)
        assertEquals(listOf("categoria.inventata"), per.map { it.categoryId })
    }

    // ────────────────────────────────────────────────────── per mese

    @Test
    fun `la spesa mensile rispetta l'ordine dei mesi richiesti`() {
        val movimenti = listOf(
            mov(-1000, "bar.caffe", "2026-05-03"),
            mov(-2000, "bar.caffe", "2026-06-14"),
            mov(-3000, "bar.caffe", "2026-07-21"),
        )
        val mesi = Statistics.lastMonths(luglio, 3)
        val totali = Statistics.monthlySpending(movimenti, mesi)

        assertEquals(listOf(YearMonth.of(2026, 5), YearMonth.of(2026, 6), luglio), totali.map { it.month })
        assertEquals(listOf(Money.of(10), Money.of(20), Money.of(30)), totali.map { it.spent })
    }

    @Test
    fun `un mese senza movimenti compare a zero e non sparisce`() {
        // Togliere i mesi vuoti farebbe sembrare consecutivi due mesi che non lo sono.
        val movimenti = listOf(mov(-3000, "bar.caffe", "2026-07-21"))
        val totali = Statistics.monthlySpending(movimenti, Statistics.lastMonths(luglio, 3))

        assertEquals(3, totali.size)
        assertEquals(Money.ZERO, totali[0].spent)
        assertEquals(Money.ZERO, totali[1].spent)
        assertEquals(Money.of(30), totali[2].spent)
    }

    @Test
    fun `lastMonths attraversa il capodanno`() {
        assertEquals(
            listOf(YearMonth.of(2025, 12), YearMonth.of(2026, 1), YearMonth.of(2026, 2)),
            Statistics.lastMonths(YearMonth.of(2026, 2), 3),
        )
    }

    @Test
    fun `lastMonths con un solo mese restituisce quello`() {
        assertEquals(listOf(luglio), Statistics.lastMonths(luglio, 1))
    }

    // ───────────────────────────────────────────────── media giornaliera

    @Test
    fun `la media giornaliera usa la lunghezza vera del mese`() {
        // Luglio ha 31 giorni: 310,00 € diventano esattamente 10,00 € al giorno.
        val movimenti = listOf(mov(-31000, "spesa.supermercato"))
        assertEquals(Money.of(10), Statistics.dailyAverage(movimenti, luglio))

        // Febbraio 2026 ne ha 28, quindi lo stesso importo pesa di più.
        val febbraio = YearMonth.of(2026, 2)
        val aFebbraio = listOf(mov(-28000, "spesa.supermercato", "2026-02-10"))
        assertEquals(Money.of(10), Statistics.dailyAverage(aFebbraio, febbraio))
    }

    // ------------------------------------------------------------- entrate

    @Test
    fun `le entrate si raggruppano per voce, dalla più grossa`() {
        val movimenti = listOf(
            mov(183100, "entrate.stipendio"),
            mov(58576, "entrate.sussidi"),
            mov(113242, "entrate.lavoro_autonomo"),
            mov(20000, "entrate.stipendio", "2026-07-11"),
            mov(-5000, "spesa"),
        )
        val voci = Statistics.incomeByCategory(movimenti)
        assertEquals(3, voci.size)
        assertEquals("entrate.stipendio", voci[0].categoryId)
        assertEquals(Money.of(2031), voci[0].total)
        assertEquals("entrate.lavoro_autonomo", voci[1].categoryId)
        assertEquals("entrate.sussidi", voci[2].categoryId)
    }

    @Test
    fun `le spese non entrano nel conto delle entrate`() {
        val movimenti = listOf(mov(100000, "entrate.stipendio"), mov(-30000, "spesa"))
        assertEquals(Money.of(1000), Statistics.totalIncome(movimenti))
        assertEquals(1, Statistics.incomeByCategory(movimenti).size)
    }

    @Test
    fun `un trasferimento non è un'entrata`() {
        // La gamba in arrivo di un trasferimento è denaro spostato, non guadagnato:
        // contarla gonferebbe il totale del mese di una cifra mai entrata.
        val (uscita, arrivo) = Ledger.transfer(
            from = Account("a", "Carta", AccountKind.CARD, 0, Money.of(500)),
            to = Account("b", "Contanti", AccountKind.CASH, 0, Money.ZERO),
            amount = Money.of(100),
            date = LocalDate.parse("2026-07-10"),
            groupId = "g1",
            idFactory = { "leg$it" },
        )
        assertEquals(Money.ZERO, Statistics.totalIncome(listOf(uscita, arrivo)))
        assertTrue(Statistics.incomeByCategory(listOf(uscita, arrivo)).isEmpty())
    }

    @Test
    fun `senza entrate non c'è niente da mostrare`() {
        assertTrue(Statistics.incomeByCategory(emptyList()).isEmpty())
        assertEquals(Money.ZERO, Statistics.totalIncome(emptyList()))
    }

    @Test
    fun `le quote delle entrate sommano a uno`() {
        val movimenti = listOf(
            mov(75000, "entrate.stipendio"),
            mov(25000, "entrate.sussidi"),
        )
        val voci = Statistics.incomeByCategory(movimenti)
        assertEquals(0.75, voci[0].share, 0.0001)
        assertEquals(0.25, voci[1].share, 0.0001)
    }
}
