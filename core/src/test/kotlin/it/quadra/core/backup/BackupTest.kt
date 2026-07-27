package it.quadra.core.backup

import it.quadra.core.backup.Backup.toDomain
import it.quadra.core.model.Account
import it.quadra.core.model.AccountKind
import it.quadra.core.model.DefaultCategories
import it.quadra.core.model.Money
import it.quadra.core.model.RecurrenceUnit
import it.quadra.core.model.RecurringRule
import it.quadra.core.model.Transaction
import it.quadra.core.model.TransactionSource
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupTest {

    private val conti = listOf(
        Account("contanti", "Contanti", AccountKind.CASH, 0xFF12A374.toInt(), Money.of(145)),
        Account(
            "adi", "Carta ADI", AccountKind.PREPAID, 0xFF6B7A89.toInt(),
            Money.of(380), includedInTotal = false,
        ),
    )

    private val movimenti = listOf(
        Transaction(
            id = "m1", amount = Money.of(-47, 80), date = LocalDate.parse("2026-07-26"),
            categoryId = "spesa.supermercato", accountId = "contanti",
            description = "Esselunga", source = TransactionSource.MANUAL,
            createdAt = Instant.ofEpochMilli(1_700_000_000_000),
        ),
        Transaction(
            id = "m2", amount = Money.of(-100), date = LocalDate.parse("2026-07-25"),
            categoryId = "rettifica", accountId = "adi", transferGroupId = "g1",
            source = TransactionSource.TRANSFER,
        ),
    )

    private val ricorrenti = listOf(
        RecurringRule(
            id = "r1", description = "Affitto", amount = Money.of(-750),
            categoryId = "casa.affitto", accountId = "contanti",
            every = 1, unit = RecurrenceUnit.MONTH, startDate = LocalDate.parse("2026-01-15"),
            skippedDates = setOf(LocalDate.parse("2026-03-15")),
        )
    )

    private fun documento() = Backup.componi(
        conti, DefaultCategories.all, movimenti, ricorrenti,
        adesso = Instant.parse("2026-07-27T10:00:00Z"),
    )

    @Test
    fun `il giro completo scrittura-lettura restituisce gli stessi dati`() {
        val esito = Backup.leggi(Backup.scrivi(documento()))
        assertIs<EsitoRipristino.Riuscito>(esito)
        val d = esito.documento

        assertEquals(conti, d.conti.map { it.toDomain() })
        assertEquals(DefaultCategories.all, d.categorie.map { it.toDomain() })
        assertEquals(movimenti, d.movimenti.map { it.toDomain() })
        assertEquals(ricorrenti, d.ricorrenti.map { it.toDomain() })
    }

    @Test
    fun `il denaro sopravvive al giro senza perdere centesimi`() {
        val esito = Backup.leggi(Backup.scrivi(documento())) as EsitoRipristino.Riuscito
        assertEquals(Money.of(-47, 80), esito.documento.movimenti.first().toDomain().amount)
        assertEquals(Money.of(380), esito.documento.conti[1].toDomain().openingBalance)
    }

    @Test
    fun `i conti vincolati restano vincolati`() {
        // Se questa proprietà si perdesse, dopo un ripristino il denaro a destinazione
        // d'uso tornerebbe a gonfiare la disponibilità.
        val esito = Backup.leggi(Backup.scrivi(documento())) as EsitoRipristino.Riuscito
        val adi = esito.documento.conti.map { it.toDomain() }.first { it.id == "adi" }
        assertTrue(!adi.includedInTotal)
    }

    @Test
    fun `le gambe dei trasferimenti restano collegate`() {
        // Perdendo il gruppo, dopo un ripristino i trasferimenti tornerebbero a contare
        // come spese e ogni statistica risulterebbe gonfiata.
        val esito = Backup.leggi(Backup.scrivi(documento())) as EsitoRipristino.Riuscito
        val gamba = esito.documento.movimenti.map { it.toDomain() }.first { it.id == "m2" }
        assertEquals("g1", gamba.transferGroupId)
        assertTrue(gamba.isTransfer)
    }

    @Test
    fun `le date saltate delle ricorrenti sopravvivono`() {
        // Senza, una ricorrente cancellata rinascerebbe al primo avvio dopo il ripristino.
        val esito = Backup.leggi(Backup.scrivi(documento())) as EsitoRipristino.Riuscito
        assertEquals(
            setOf(LocalDate.parse("2026-03-15")),
            esito.documento.ricorrenti.first().toDomain().skippedDates,
        )
    }

    @Test
    fun `il file è leggibile a occhio nudo`() {
        // Chi è diffidente deve poterlo aprire e vedere che contiene solo le sue spese.
        val testo = Backup.scrivi(documento())
        assertTrue("\"formato\": 1" in testo)
        assertTrue("Esselunga" in testo)
        assertTrue("Carta ADI" in testo)
        assertTrue(testo.lines().size > 20, "dovrebbe essere indentato, non una riga sola")
    }

    @Test
    fun `un file che non è un backup viene respinto senza esplodere`() {
        // Il selettore di sistema permette di scegliere qualunque cosa.
        listOf("", "   ", "non sono json", "{}", "[1,2,3]", "<html></html>").forEach {
            val esito = Backup.leggi(it)
            assertTrue(
                esito is EsitoRipristino.NonRiconosciuto || esito is EsitoRipristino.Riuscito,
                "input «$it» ha prodotto $esito",
            )
        }
        assertIs<EsitoRipristino.NonRiconosciuto>(Backup.leggi("non sono json"))
        assertIs<EsitoRipristino.NonRiconosciuto>(Backup.leggi(""))
    }

    @Test
    fun `un backup di una versione futura viene riconosciuto e rifiutato`() {
        val futuro = Backup.scrivi(documento()).replace("\"formato\": 1", "\"formato\": 99")
        val esito = Backup.leggi(futuro)
        assertIs<EsitoRipristino.FormatoTroppoNuovo>(esito)
        assertEquals(99, esito.trovato)
    }

    @Test
    fun `campi sconosciuti non impediscono il ripristino`() {
        // Un backup scritto da una versione più recente deve restare recuperabile.
        val conExtra = Backup.scrivi(documento())
            .replaceFirst("\"app\":", "\"campoDelFuturo\": \"qualcosa\",\n    \"app\":")
        assertIs<EsitoRipristino.Riuscito>(Backup.leggi(conExtra))
    }

    @Test
    fun `le preferenze fanno il giro come tutto il resto`() {
        val con = Backup.componi(
            conti, DefaultCategories.all, movimenti, ricorrenti,
            preferenze = mapOf("budget.mensile.centesimi" to "180000"),
        )
        val esito = Backup.leggi(Backup.scrivi(con)) as EsitoRipristino.Riuscito
        assertEquals("180000", esito.documento.preferenze["budget.mensile.centesimi"])
    }

    @Test
    fun `un backup scritto prima delle preferenze resta leggibile`() {
        // Il campo è stato aggiunto senza cambiare il numero di formato: i file già
        // salvati devono continuare a funzionare, con la mappa vuota.
        val vecchio = Backup.scrivi(documento())
            .lines().filterNot { "preferenze" in it }.joinToString("\n")
            .replace(",\n}", "\n}")
        val esito = Backup.leggi(vecchio)
        assertIs<EsitoRipristino.Riuscito>(esito)
        assertTrue(esito.documento.preferenze.isEmpty())
        assertEquals(movimenti.size, esito.documento.movimenti.size)
    }

    @Test
    fun `un backup vuoto è valido`() {
        val vuoto = Backup.componi(emptyList(), emptyList(), emptyList(), emptyList())
        val esito = Backup.leggi(Backup.scrivi(vuoto))
        assertIs<EsitoRipristino.Riuscito>(esito)
        assertTrue(esito.documento.movimenti.isEmpty())
    }
}
