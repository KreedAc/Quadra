package it.quadra.data

import androidx.room.withTransaction
import it.quadra.core.backup.Backup
import it.quadra.core.backup.Backup.toDomain
import it.quadra.core.backup.BackupDocument
import it.quadra.core.ledger.Edits
import it.quadra.core.ledger.Ledger
import it.quadra.core.ledger.Totals
import it.quadra.core.model.Account
import it.quadra.core.model.Category
import it.quadra.core.model.DefaultAccounts
import it.quadra.core.model.DefaultCategories
import it.quadra.core.model.Money
import it.quadra.core.model.RecurringRule
import it.quadra.core.model.Transaction
import it.quadra.core.model.TransactionSource
import it.quadra.core.scadenze.Scadenza
import it.quadra.core.scadenze.Scadenze
import it.quadra.data.db.AppDatabase
import it.quadra.data.db.ImpostazioneEntity
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
     * Le gambe di un trasferimento se ne vanno insieme, che è l'unico caso in cui
     * cancellare una riga sola romperebbe i conti.
     *
     * Se il movimento era nato da una scadenza ricorrente, cancellarlo la rimette in
     * attesa — ed è quello che si vuole: si cancella proprio perché quel pagamento non è
     * avvenuto. Non serve segnare niente da nessuna parte, perché lo stato di una
     * scadenza è sempre ricalcolato dai movimenti che esistono.
     */
    suspend fun delete(transaction: Transaction) {
        db.transactions().deleteWithLegs(transaction.toEntity())
    }

    /** Rimette un movimento cancellato, per la finestra di annullamento. */
    suspend fun restore(transactions: List<Transaction>) {
        db.transactions().upsert(transactions.map { it.toEntity() })
    }

    /** Le gambe che verrebbero cancellate insieme a questo movimento. */
    suspend fun legsOf(transaction: Transaction): List<Transaction> =
        transaction.transferGroupId
            ?.let { db.transactions().legsOfTransfer(it).map { e -> e.toDomain() } }
            ?: listOf(transaction)

    /** Applica le correzioni fatte nel foglio di dettaglio. */
    suspend fun modifica(
        movimento: Transaction,
        importo: Money,
        categoriaId: String,
        contoId: String,
        data: LocalDate,
        descrizione: String,
        note: String,
    ) {
        db.transactions().upsert(
            Edits.edit(
                transaction = movimento,
                amount = importo,
                categoryId = categoriaId,
                accountId = contoId,
                date = data,
                description = descrizione,
                notes = note,
                now = Instant.now(),
            ).toEntity()
        )
    }

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

    // ──────────────────────────────────── categorie e conti personalizzati

    suspend fun salvaCategoria(categoria: Category) {
        db.categories().upsert(categoria.toEntity())
    }

    suspend fun salvaConto(conto: Account) {
        db.accounts().upsert(conto.toEntity())
    }

    /**
     * Toglie una categoria dalla circolazione.
     *
     * Se è già stata usata da qualche movimento non viene cancellata ma nascosta:
     * eliminarla lascerebbe quei movimenti a puntare a un identificativo che non esiste
     * più, e i totali per categoria si romperebbero senza che l'utente capisca perché.
     * Quelle mai usate spariscono davvero, insieme alle loro sottocategorie.
     *
     * @return true se è stata cancellata, false se è stata solo nascosta.
     */
    suspend fun rimuoviCategoria(categoria: Category): Boolean {
        val figlie = db.categories().observeAll().first()
            .map { it.toDomain() }
            .filter { it.parentId == categoria.id }
        val famiglia = (figlie + categoria).map { it.id }
        val usata = db.transactions().observeAll().first().any { it.categoryId in famiglia }

        return if (usata) {
            (figlie + categoria).forEach { salvaCategoria(it.copy(hidden = true)) }
            false
        } else {
            db.categories().deleteByIds(famiglia)
            true
        }
    }

    /** Come [rimuoviCategoria], ma per i conti: archiviati se usati, cancellati se mai visti. */
    suspend fun rimuoviConto(conto: Account): Boolean {
        val usato = db.transactions().observeAll().first().any { it.accountId == conto.id }
        return if (usato) {
            salvaConto(conto.copy(archived = true))
            false
        } else {
            db.accounts().delete(conto.toEntity())
            true
        }
    }

    // ──────────────────────────────────────────────────────────── backup

    /**
     * Tutto quello che c'è, in un file JSON leggibile.
     *
     * L'utente sceglie dove metterlo col selettore di sistema: Drive, Dropbox, la
     * memoria del telefono, una chiavetta. Il file è suo e va dove decide lui, senza
     * che l'app chieda un permesso o un accesso alla rete.
     */
    suspend fun esporta(): String {
        val documento = Backup.componi(
            conti = db.accounts().observeAll().first().map { it.toDomain() },
            categorie = db.categories().observeAll().first().map { it.toDomain() },
            movimenti = db.transactions().observeAll().first().map { it.toDomain() },
            ricorrenti = db.recurringRules().observeAll().first().map { it.toDomain() },
            preferenze = db.impostazioni().tutte().associate { it.chiave to it.valore },
        )
        return Backup.scrivi(documento)
    }

    /**
     * Rimpiazza tutto il contenuto con quello del backup.
     *
     * È una sostituzione e non una fusione, ed è una scelta: fondere due archivi
     * significa decidere cosa fare quando lo stesso movimento esiste da entrambe le
     * parti con importi diversi, e qualunque regola si scelga produce sorprese. Un
     * ripristino che rimpiazza è prevedibile — l'utente sa esattamente cosa avrà dopo.
     *
     * Avviene dentro una transazione: se qualcosa va storto a metà, il database resta
     * com'era invece di ritrovarsi mezzo vuoto.
     */
    suspend fun ripristina(documento: BackupDocument) = db.withTransaction {
        db.transactions().deleteAll()
        db.recurringRules().deleteAll()
        db.categories().deleteAll()
        db.accounts().deleteAll()

        db.accounts().upsert(documento.conti.map { it.toDomain().toEntity() })
        db.categories().upsert(documento.categorie.map { it.toDomain().toEntity() })
        db.transactions().upsert(documento.movimenti.map { it.toDomain().toEntity() })
        db.recurringRules().upsert(documento.ricorrenti.map { it.toDomain().toEntity() })

        // Le preferenze si sostituiscono solo se il backup ne porta: un backup scritto
        // da una versione che non le conosceva non deve cancellare quelle di qui.
        if (documento.preferenze.isNotEmpty()) {
            db.impostazioni().deleteAll()
            db.impostazioni().scrivi(
                documento.preferenze.map { (chiave, valore) -> ImpostazioneEntity(chiave, valore) }
            )
        }
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

    // ───────────────────────────────────────────── ricorrenti

    fun observeRecurring(): Flow<List<RecurringRule>> =
        db.recurringRules().observeAll().map { righe -> righe.map { it.toDomain() } }

    suspend fun salvaRicorrente(regola: RecurringRule) {
        db.recurringRules().upsert(regola.toEntity())
    }

    /** Le scadenze già confermate, per capire cosa resta in attesa. */
    fun observeScadenzeRegistrate(): Flow<Set<String>> =
        db.transactions().observeChiaviRicorrenti().map { it.toSet() }

    /**
     * Registra il pagamento di una scadenza, con l'importo che l'utente ha davvero pagato.
     *
     * La data del movimento è quella in cui si è pagato, che può non essere quella della
     * scadenza: la rata del 10 saldata il 12 è una spesa del 12. Ma resta legata alla
     * scadenza del 10 tramite la chiave, altrimenti l'app tornerebbe a chiederla.
     */
    suspend fun confermaScadenza(
        scadenza: Scadenza,
        importo: Money,
        data: LocalDate = LocalDate.now(),
    ): Transaction {
        val adesso = Instant.now()
        val movimento = Transaction(
            id = UUID.randomUUID().toString(),
            amount = importo.asExpense(),
            date = data,
            categoryId = scadenza.regola.categoryId,
            accountId = scadenza.regola.accountId,
            description = scadenza.regola.description,
            source = TransactionSource.RECURRING,
            recurringRuleId = scadenza.regola.id,
            externalKey = scadenza.chiave,
            createdAt = adesso,
            updatedAt = adesso,
        )
        db.transactions().upsert(movimento.toEntity())
        return movimento
    }

    /** Rimanda una scadenza: torna fra [giorni] giorni, la cadenza non si tocca. */
    suspend fun rimandaScadenza(scadenza: Scadenza, giorni: Long) {
        db.recurringRules().upsert(
            Scadenze.rimanda(scadenza.regola, scadenza.occorrenza, giorni).toEntity()
        )
    }

    /** Salta una scadenza: quel mese non è stato pagato e non lo sarà. */
    suspend fun saltaScadenza(scadenza: Scadenza) {
        db.recurringRules().upsert(Scadenze.salta(scadenza.regola, scadenza.occorrenza).toEntity())
    }

    /**
     * Toglie una regola, lasciando i movimenti che ha già generato.
     *
     * Cancellarli sarebbe riscrivere il passato: quelle spese sono avvenute davvero, e
     * il saldo dei conti le comprende. Smettere di generarne di nuove è tutto ciò che
     * si sta chiedendo.
     */
    suspend fun rimuoviRicorrente(regola: RecurringRule) {
        db.recurringRules().delete(regola.toEntity())
    }

}
