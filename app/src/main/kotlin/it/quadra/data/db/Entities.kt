package it.quadra.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Le tabelle.
 *
 * Sono deliberatamente separate dai modelli di :core: il modello di dominio non deve
 * portarsi dietro annotazioni di persistenza, e lo schema del database non deve
 * cambiare ogni volta che si aggiunge un metodo al dominio. La traduzione sta in
 * [it.quadra.data.Mappers].
 *
 * Gli importi sono interi di centesimi, le date stringhe ISO ordinabili
 * lessicograficamente — così un `ORDER BY date` funziona senza conversioni.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val colorArgb: Int,
    val openingBalanceCents: Long,
    val includedInTotal: Boolean,
    val icon: String?,
    val sortOrder: Int,
    val archived: Boolean,
)

@Entity(
    tableName = "categories",
    indices = [Index("parentId")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val parentId: String?,
    val colorArgb: Int,
    val icon: String?,
    val sortOrder: Int,
    val isSystem: Boolean,
    val hidden: Boolean,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("date"),
        Index("accountId"),
        Index("categoryId"),
        Index("transferGroupId"),
        Index("recurringRuleId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val amountCents: Long,
    /** ISO-8601, "2026-07-26". Ordinabile come stringa, niente conversioni in query. */
    val date: String,
    val categoryId: String,
    val accountId: String,
    val transferGroupId: String?,
    val description: String,
    val merchant: String?,
    val notes: String,
    val source: String,
    val status: String,
    val recurringRuleId: String?,
    val externalKey: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey val id: String,
    val description: String,
    val amountCents: Long,
    val categoryId: String,
    val accountId: String,
    val every: Int,
    val unit: String,
    val startDate: String,
    val endDate: String?,
    val dayOfMonth: Int?,
    val active: Boolean,
    /** Date ISO separate da virgola. Poche per definizione, non serve una tabella. */
    val skippedDates: String,
    val autoInsert: Boolean,
)
