package it.lemiespese.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultCategoriesTest {

    @Test
    fun `gli id sono unici`() {
        val ids = DefaultCategories.all.map { it.id }
        val duplicati = ids.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue(duplicati.isEmpty(), "id duplicati: $duplicati")
    }

    @Test
    fun `la griglia sta in una schermata`() {
        // Il primo livello deve restare visibile tutto insieme: è la premessa da cui
        // dipende la velocità di inserimento. La griglia è di quattro colonne e ne regge
        // quattro righe, meno una casella riservata a "Personalizza". Oltre quel numero
        // comincia a scorrere e si torna a dover cercare, cioè si perde esattamente la
        // proprietà per cui è stata disegnata.
        val n = DefaultCategories.topLevel.size
        assertTrue(n in 8..15, "il primo livello ha $n voci, la griglia ne regge al massimo 15")
    }

    @Test
    fun `ogni voce di primo livello ha icona e posizione`() {
        DefaultCategories.topLevel.forEach {
            assertNotNull(it.icon, "${it.id} è di primo livello e deve avere un'icona")
        }
        assertEquals(
            DefaultCategories.topLevel.indices.toList(),
            DefaultCategories.topLevel.map { it.sortOrder },
            "le posizioni nella griglia devono essere consecutive e senza buchi",
        )
    }

    @Test
    fun `ogni sottocategoria punta a un genitore esistente di primo livello`() {
        val figlie = DefaultCategories.all.filter { !it.isTopLevel }
        assertTrue(figlie.isNotEmpty())
        figlie.forEach { figlia ->
            val genitore = DefaultCategories.byId(figlia.parentId!!)
            assertNotNull(genitore, "${figlia.id} punta al genitore inesistente ${figlia.parentId}")
            assertTrue(genitore.isTopLevel, "l'albero deve avere due soli livelli, ${genitore.id} non è radice")
        }
    }

    @Test
    fun `ogni voce di primo livello ha almeno una sottocategoria`() {
        DefaultCategories.topLevel.forEach {
            assertTrue(
                DefaultCategories.childrenOf(it.id).isNotEmpty(),
                "${it.id} non ha sottocategorie: il secondo tocco non avrebbe nulla da mostrare",
            )
        }
    }

    @Test
    fun `le sottocategorie ereditano colore e natura del genitore`() {
        DefaultCategories.all.filter { !it.isTopLevel }.forEach { figlia ->
            val genitore = DefaultCategories.byId(figlia.parentId!!)!!
            assertEquals(genitore.colorArgb, figlia.colorArgb, "colore diverso dal genitore in ${figlia.id}")
            assertEquals(genitore.kind, figlia.kind, "natura diversa dal genitore in ${figlia.id}")
        }
    }

    @Test
    fun `gli id derivati dai nomi restano leggibili in un backup`() {
        assertNotNull(DefaultCategories.byId("trasporti.bollo_auto"))
        assertNotNull(DefaultCategories.byId("casa.condominio"))
        assertNotNull(DefaultCategories.byId("bar.caffe"))
        assertNotNull(DefaultCategories.byId("tasse.imu"))
        assertNotNull(DefaultCategories.byId("salute.visite_e_analisi"))
        // Niente accenti, spazi o maiuscole negli identificativi.
        DefaultCategories.all.forEach {
            assertTrue(
                it.id.all { ch -> ch.isLowerCase() || ch.isDigit() || ch == '.' || ch == '_' },
                "id non normalizzato: ${it.id}",
            )
        }
    }

    @Test
    fun `esiste una sola famiglia di entrate e il resto sono uscite`() {
        val radiciEntrata = DefaultCategories.topLevel.filter { it.isIncome }
        assertEquals(listOf("entrate"), radiciEntrata.map { it.id })
        assertTrue(DefaultCategories.topLevel.count { it.isExpense } >= 8)
    }

    @Test
    fun `le voci italiane che ci distinguono ci sono`() {
        val nomi = DefaultCategories.all.map { it.name }
        listOf("Condominio", "Bollo auto", "IMU", "TARI", "Commercialista", "Mutuo")
            .forEach { assertTrue(it in nomi, "manca la voce «$it»") }
    }

    @Test
    fun `rootOf risale al primo livello e si ferma lì`() {
        assertEquals("trasporti", DefaultCategories.rootOf("trasporti.carburante")?.id)
        assertEquals("trasporti", DefaultCategories.rootOf("trasporti")?.id)
        assertNull(DefaultCategories.rootOf("categoria.inesistente"))
    }

    @Test
    fun `i ripieghi per l'import esistono e sono di primo livello`() {
        assertTrue(DefaultCategories.fallbackExpense.isTopLevel)
        assertTrue(DefaultCategories.fallbackExpense.isExpense)
        assertTrue(DefaultCategories.fallbackIncome.isTopLevel)
        assertTrue(DefaultCategories.fallbackIncome.isIncome)
    }
}
