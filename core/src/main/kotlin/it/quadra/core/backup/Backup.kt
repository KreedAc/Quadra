package it.quadra.core.backup

import it.quadra.core.model.Account
import it.quadra.core.model.AccountKind
import it.quadra.core.model.Category
import it.quadra.core.model.CategoryKind
import it.quadra.core.model.Money
import it.quadra.core.model.RecurrenceUnit
import it.quadra.core.model.RecurringRule
import it.quadra.core.model.Transaction
import it.quadra.core.model.TransactionSource
import it.quadra.core.model.TransactionStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

/**
 * Il formato del backup.
 *
 * Tre decisioni lo governano, e sono tutte pensate per il momento in cui servirà
 * davvero — cioè quando qualcuno ha appena rotto il telefono.
 *
 * **È JSON leggibile, non una copia del database.** Chi è diffidente può aprirlo e
 * vedere con i propri occhi che contiene solo le sue spese e nient'altro: in un'app che
 * vende la privacy, questa è una prova e non un dettaglio tecnico. Ed è anche il motivo
 * pratico: un backup legato allo schema interno di SQLite smetterebbe di funzionare al
 * primo cambio di struttura.
 *
 * **Porta un numero di formato.** Quando cambierà, un'app vecchia saprà dire "questo
 * backup viene da una versione più recente" invece di leggere male e rovinare i dati.
 *
 * **I campi hanno nomi brevi e stabili.** Rinominarli romperebbe i backup già scritti,
 * quindi qui i nomi si aggiungono ma non si cambiano mai.
 */
@Serializable
data class BackupDocument(
    @SerialName("formato") val formato: Int = FORMATO_CORRENTE,
    @SerialName("app") val app: String = "Quadra",
    @SerialName("creato") val creatoIl: String,
    @SerialName("conti") val conti: List<ContoJson> = emptyList(),
    @SerialName("categorie") val categorie: List<CategoriaJson> = emptyList(),
    @SerialName("movimenti") val movimenti: List<MovimentoJson> = emptyList(),
    @SerialName("ricorrenti") val ricorrenti: List<RicorrenteJson> = emptyList(),
    /**
     * Le preferenze, per ora solo il budget.
     *
     * Aggiunto dopo il formato 1 senza cambiarne il numero, ed è il motivo per cui i
     * campi hanno un default: un backup vecchio letto oggi ottiene la mappa vuota, e un
     * backup di oggi letto da un'app vecchia ignora il campo e ripristina tutto il resto.
     */
    @SerialName("preferenze") val preferenze: Map<String, String> = emptyMap(),
) {
    companion object {
        const val FORMATO_CORRENTE = 1
    }
}

@Serializable
data class ContoJson(
    val id: String,
    val nome: String,
    val tipo: String,
    val colore: Int,
    val apertura: Long,
    val nelTotale: Boolean,
    val icona: String? = null,
    val ordine: Int = 0,
    val archiviato: Boolean = false,
)

@Serializable
data class CategoriaJson(
    val id: String,
    val nome: String,
    val natura: String,
    val madre: String? = null,
    val colore: Int,
    val icona: String? = null,
    val ordine: Int = 0,
    val diSistema: Boolean = false,
    val nascosta: Boolean = false,
)

@Serializable
data class MovimentoJson(
    val id: String,
    val importo: Long,
    val data: String,
    val categoria: String,
    val conto: String,
    val gruppoTrasferimento: String? = null,
    val descrizione: String = "",
    val esercente: String? = null,
    val note: String = "",
    val origine: String = "MANUAL",
    val stato: String = "SETTLED",
    val regola: String? = null,
    val chiaveEsterna: String? = null,
    val creato: Long = 0,
    val aggiornato: Long = 0,
)

@Serializable
data class RicorrenteJson(
    val id: String,
    val descrizione: String,
    val importo: Long,
    val categoria: String,
    val conto: String,
    val ogni: Int,
    val unita: String,
    val inizio: String,
    val fine: String? = null,
    val giornoDelMese: Int? = null,
    val attiva: Boolean = true,
    val dateSaltate: List<String> = emptyList(),
    /**
     * I rinvii, come "occorrenza>quando riproporla".
     *
     * Sostituisce `automatica`, che non esiste più: le ricorrenti non registrano niente
     * da sole. Un backup vecchio porta ancora quel campo e viene semplicemente ignorato.
     */
    val rinvii: List<String> = emptyList(),
)

/** Cosa può andare storto leggendo un file scelto dall'utente. */
sealed interface EsitoRipristino {
    data class Riuscito(val documento: BackupDocument) : EsitoRipristino

    /** Il file non è un backup: l'utente ha scelto la foto sbagliata dal selettore. */
    data object NonRiconosciuto : EsitoRipristino

    /** Backup scritto da una versione più recente: leggerlo rovinerebbe i dati. */
    data class FormatoTroppoNuovo(val trovato: Int) : EsitoRipristino
}

object Backup {

    private val json = Json {
        prettyPrint = true
        // Un backup più recente può avere campi che questa versione non conosce:
        // ignorarli permette di ripristinare comunque tutto il resto.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun componi(
        conti: List<Account>,
        categorie: List<Category>,
        movimenti: List<Transaction>,
        ricorrenti: List<RecurringRule>,
        preferenze: Map<String, String> = emptyMap(),
        adesso: Instant = Instant.now(),
    ): BackupDocument = BackupDocument(
        creatoIl = adesso.toString(),
        conti = conti.map { it.toJson() },
        categorie = categorie.map { it.toJson() },
        movimenti = movimenti.map { it.toJson() },
        ricorrenti = ricorrenti.map { it.toJson() },
        preferenze = preferenze,
    )

    fun scrivi(documento: BackupDocument): String = json.encodeToString(documento)

    /**
     * Legge un file scelto dall'utente.
     *
     * Non solleva eccezioni: il selettore di sistema permette di scegliere qualunque
     * file, quindi ricevere spazzatura è la normalità e non un caso eccezionale.
     */
    fun leggi(contenuto: String): EsitoRipristino {
        val documento = try {
            json.decodeFromString<BackupDocument>(contenuto)
        } catch (e: Exception) {
            return EsitoRipristino.NonRiconosciuto
        }
        if (documento.formato > BackupDocument.FORMATO_CORRENTE) {
            return EsitoRipristino.FormatoTroppoNuovo(documento.formato)
        }
        return EsitoRipristino.Riuscito(documento)
    }

    // ───────────────────────────────────────────────── verso il formato

    private fun Account.toJson() = ContoJson(
        id = id, nome = name, tipo = kind.name, colore = colorArgb,
        apertura = openingBalance.cents, nelTotale = includedInTotal,
        icona = icon, ordine = sortOrder, archiviato = archived,
    )

    private fun Category.toJson() = CategoriaJson(
        id = id, nome = name, natura = kind.name, madre = parentId, colore = colorArgb,
        icona = icon, ordine = sortOrder, diSistema = isSystem, nascosta = hidden,
    )

    private fun Transaction.toJson() = MovimentoJson(
        id = id, importo = amount.cents, data = date.toString(), categoria = categoryId,
        conto = accountId, gruppoTrasferimento = transferGroupId, descrizione = description,
        esercente = merchant, note = notes, origine = source.name, stato = status.name,
        regola = recurringRuleId, chiaveEsterna = externalKey,
        creato = createdAt.toEpochMilli(), aggiornato = updatedAt.toEpochMilli(),
    )

    private fun RecurringRule.toJson() = RicorrenteJson(
        id = id, descrizione = description, importo = amount.cents, categoria = categoryId,
        conto = accountId, ogni = every, unita = unit.name, inizio = startDate.toString(),
        fine = endDate?.toString(), giornoDelMese = dayOfMonth, attiva = active,
        dateSaltate = skippedDates.sorted().map { it.toString() },
        rinvii = rimandi.toSortedMap().map { (occorrenza, quando) -> "$occorrenza>$quando" },
    )

    // ───────────────────────────────────────────────── verso il dominio

    fun ContoJson.toDomain() = Account(
        id = id, name = nome, kind = enumOrDefault(tipo, AccountKind.OTHER),
        colorArgb = colore, openingBalance = Money(apertura), includedInTotal = nelTotale,
        icon = icona, sortOrder = ordine, archived = archiviato,
    )

    fun CategoriaJson.toDomain() = Category(
        id = id, name = nome, kind = enumOrDefault(natura, CategoryKind.EXPENSE),
        parentId = madre, colorArgb = colore, icon = icona, sortOrder = ordine,
        isSystem = diSistema, hidden = nascosta,
    )

    fun MovimentoJson.toDomain() = Transaction(
        id = id, amount = Money(importo), date = LocalDate.parse(data), categoryId = categoria,
        accountId = conto, transferGroupId = gruppoTrasferimento, description = descrizione,
        merchant = esercente, notes = note,
        source = enumOrDefault(origine, TransactionSource.MANUAL),
        status = enumOrDefault(stato, TransactionStatus.SETTLED),
        recurringRuleId = regola, externalKey = chiaveEsterna,
        createdAt = Instant.ofEpochMilli(creato), updatedAt = Instant.ofEpochMilli(aggiornato),
    )

    fun RicorrenteJson.toDomain() = RecurringRule(
        id = id, description = descrizione, amount = Money(importo), categoryId = categoria,
        accountId = conto, every = ogni, unit = enumOrDefault(unita, RecurrenceUnit.MONTH),
        startDate = LocalDate.parse(inizio), endDate = fine?.let(LocalDate::parse),
        dayOfMonth = giornoDelMese, active = attiva,
        skippedDates = dateSaltate.map(LocalDate::parse).toSet(),
        rimandi = rinvii.mapNotNull { riga ->
            val pezzi = riga.split('>')
            if (pezzi.size == 2) LocalDate.parse(pezzi[0]) to LocalDate.parse(pezzi[1]) else null
        }.toMap(),
    )

    private inline fun <reified T : Enum<T>> enumOrDefault(nome: String, riserva: T): T =
        enumValues<T>().firstOrNull { it.name == nome } ?: riserva
}
