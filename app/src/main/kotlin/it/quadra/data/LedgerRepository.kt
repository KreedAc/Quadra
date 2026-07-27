package it.quadra.data

import it.quadra.core.ledger.Edits
import it.quadra.core.ledger.Ledger
import it.quadra.core.ledger.Totals
import it.quadra.core.model.Account
import it.quadra.core.model.Category
import it.quadra.core.model.DefaultAccounts
import it.quadra.core.model.DefaultCategories
import it.quadra.core.model.Money
import it.quadra.core.model.Transaction
import it.quadra.core.model.TransactionSource
import it.quadra.core.recurrence.RecurrenceEngine
import it.quadra.data.db.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * L'unico punto in cui il database incontra il dominio.
 *
 * Le regole non stanno qui: stanno in :core e sono già coperte da test. Questo strato
 * si limita a leggere, tradurre, chiamare la regola e riscrivere. Se qui dentro compare
 * un calcolo, è nel posto sbagliato.
 */
class LedgerRepository(private val db: AppDatabase) {

    // ─────────────────────────────────────────────────────────── letture

    fun observeAccounts(): Flow<List<Account>> =
        db.accounts().observeActive().map { list -> list.map { it.toDomain() } }

    fun observeCategories(): Flow<List<Category>> =
        db.categories().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeMonth(month: YearMonth): Flow<List<Transaction>> =
        db.transactions()
            .observeBetween(month.atDay(1).toString(), month.atEndOfMonth().toString())
            .map { list -> list.map { it.toDomain() } }

    fun observeAllTransactions(): Flow<List<Transaction>> =
        db.transactions().observeAll().map { list -> list.map { it.toDomain() } }

    /** Saldo di ogni conto, ricalcolato a ogni variazione. Mai memorizzato. */
    fun observeBalances(): Flow<Map<String, Money>> =
        combine(observeAccounts(), observeAllTransactions()) { accounts, transactions ->
            Ledger.balances(accounts, transactions)
        }

    /** Disponibile e vincolato, ricalcolati a ogni variazione di conti o movimenti. */
    fun observeTotals(): Flow<Totals> =
        combine(observeAccounts(), observeAllTransactions()) { accounts, transactions ->
            Ledger.totals(accounts, transactions)
        }

    // ───────────────────────────────────────────────────────── scritture

    suspend fun add(
        amount: Money,
        categoryId: String,
        accountId: String,
        date: LocalDate = LocalDate.now(),
        description: String = "",
        notes: String = "",
    ): Transaction {
        val now = Instant.now()
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            amount = amount,
            date = date,
            categoryId = categoryId,
            accountId = accountId,
            description = description,
            notes = notes,
            source = TransactionSource.MANUAL,
            createdAt = now,
            updatedAt = now,
        )
        db.transactions().upsert(transaction.toEntity())
        return transaction
    }

    suspend fun update(transaction: Transaction) {
        db.transactions().upsert(transaction.copy(updatedAt = Instant.now()).toEntity())
    }

    /**
     * Cancella davvero, senza lasciare traccia.
     *
     * Due cose che non si vedono ma servono: le gambe di un trasferimento se ne vanno
     * insieme, e se il movimento veniva da una regola ricorrente la data viene segnata
     * come saltata — altrimenti la generazione al prossimo avvio lo farebbe rinascere.
     */
    suspend fun delete(transaction: Transaction) {
        db.transactions().deleteWithLegs(transaction.toEntity())

        Edits.skipForRule(transaction)?.let { (ruleId, date) ->
            db.recurringRules().byId(ruleId)?.let { entity ->
                val rule = entity.toDomain()
                db.recurringRules().upsert(
                    rule.copy(skippedDates = rule.skippedDates + date).toEntity()
                )
            }
        }
    }

    /** Rimette un movimento cancellato, per la finestra di annullamento. */
    suspend fun restore(transactions: List<Transaction>) {
        db.transactions().upsert(transactions.map { it.toEntity() })
        transactions.forEach { transaction ->
            Edits.skipForRule(transaction)?.let { (ruleId, date) ->
                db.recurringRules().byId(ruleId)?.let { entity ->
                    val rule = entity.toDomain()
                    db.recurringRules().upsert(
                        rule.copy(skippedDates = rule.skippedDates - date).toEntity()
                    )
                }
            }
        }
    }

    /** Le gambe che verrebbero cancellate insieme a questo movimento. */
    suspend fun legsOf(transaction: Transaction): List<Transaction> =
        transaction.transferGroupId
            ?.let { db.transactions().legsOfTransfer(it).map { e -> e.toDomain() } }
            ?: listOf(transaction)

    suspend fun moveToAccount(transaction: Transaction, accountId: String) {
        db.transactions().upsert(
            Edits.moveToAccount(transaction, accountId, Instant.now()).toEntity()
        )
    }

    suspend fun transfer(from: Account, to: Account, amount: Money, date: LocalDate = LocalDate.now()) {
        val group = UUID.randomUUID().toString()
        val (outgoing, incoming) = Ledger.transfer(
            from = from,
            to = to,
            amount = amount,
            date = date,
            groupId = group,
            idFactory = { UUID.randomUUID().toString() },
        )
        val now = Instant.now()
        db.transactions().upsert(
            listOf(outgoing, incoming).map { it.copy(createdAt = now, updatedAt = now).toEntity() }
        )
    }

    /**
     * Allinea il saldo di un conto a quello reale.
     * L'utente scrive quanto ha; la differenza la calcola [Ledger.reconcile].
     */
    suspend fun reconcile(account: Account, realBalance: Money, date: LocalDate = LocalDate.now()): Transaction? {
        val transactions = db.transactions().observeForAccount(account.id)
            .first()
            .map { it.toDomain() }
        val now = Instant.now()
        val adjustment = Ledger.reconcile(
            account = account,
            transactions = transactions,
            realBalance = realBalance,
            date = date,
            id = UUID.randomUUID().toString(),
        ) ?: return null
        db.transactions().upsert(adjustment.copy(createdAt = now, updatedAt = now).toEntity())
        return adjustment
    }

    // ───────────────────────────────────────────── primo avvio e ricorrenti

    /**
     * Popola conti e categorie predefiniti la prima volta.
     * Idempotente: se c'è già qualcosa non tocca niente.
     */
    suspend fun seedIfEmpty() {
        if (db.accounts().count() == 0) {
            db.accounts().upsert(DefaultAccounts.all.map { it.toEntity() })
        }
        if (db.categories().count() == 0) {
            db.categories().upsert(DefaultCategories.all.map { it.toEntity() })
        }
    }

    /**
     * Crea i movimenti delle regole ricorrenti scadute.
     * Gira a ogni avvio ed è idempotente: le date già presenti e quelle cancellate a
     * mano non vengono riprodotte.
     */
    suspend fun materializeRecurring(upTo: LocalDate = LocalDate.now()) {
        val now = Instant.now()
        db.recurringRules().activeRules().forEach { entity ->
            val rule = entity.toDomain()
            val existing = db.transactions().datesGeneratedBy(rule.id)
                .map(LocalDate::parse)
                .toSet()
            val nuovi = RecurrenceEngine.materialize(
                rule = rule,
                upTo = upTo,
                existingDates = existing,
                idFactory = { _, _ -> UUID.randomUUID().toString() },
            )
            if (nuovi.isNotEmpty()) {
                db.transactions().upsert(
                    nuovi.map { it.copy(createdAt = now, updatedAt = now).toEntity() }
                )
            }
        }
    }
}
