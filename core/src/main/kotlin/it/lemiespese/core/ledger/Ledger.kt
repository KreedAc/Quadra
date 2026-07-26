package it.lemiespese.core.ledger

import it.lemiespese.core.model.Account
import it.lemiespese.core.model.Money
import it.lemiespese.core.model.Transaction
import it.lemiespese.core.model.TransactionSource
import it.lemiespese.core.model.sum
import java.time.LocalDate

/** Come si ripartisce il denaro fra ciò che si può spendere e ciò che è vincolato. */
data class Totals(
    /** Somma dei conti che entrano nella disponibilità reale. */
    val available: Money,
    /**
     * Somma dei conti esclusi dal totale: buoni, carte a destinazione d'uso, fondi
     * accantonati. Restano visibili di proposito — nasconderli farebbe dimenticare
     * che quel denaro esiste.
     */
    val constrained: Money,
) {
    val overall: Money get() = available + constrained
}

/**
 * Le regole che tengono onesti i numeri.
 *
 * Tre decisioni sono concentrate qui perché sono le tre in cui questo tipo di app
 * sbaglia più spesso.
 *
 * **I trasferimenti non sono spese.** Spostare denaro dalla carta ai contanti non
 * consuma nulla, ma se le due gambe finiscono nelle statistiche il mese risulta gonfiato
 * del doppio dell'importo spostato. [spending] le esclude.
 *
 * **Il prelievo non esiste come concetto separato.** Prelevare al bancomat è un
 * trasferimento dal conto ai contanti, né più né meno. Averne due nomi diversi
 * nell'interfaccia costringe l'utente a chiedersi ogni volta quale usare, senza che la
 * differenza produca nulla di utile.
 *
 * **Il saldo non si riscrive, si rettifica.** Quando il saldo reale non corrisponde
 * perché si è dimenticato di segnare qualcosa, sovrascrivere il numero fa tornare il
 * conto e mentire alle statistiche. [reconcile] genera invece un movimento di rettifica:
 * il saldo torna giusto e la differenza resta visibile. Se la voce "Rettifiche" cresce di
 * mese in mese, quella è un'informazione — significa che si sta dimenticando di segnare.
 */
object Ledger {

    /** Categoria dei movimenti generati da [reconcile]. */
    const val ADJUSTMENT_CATEGORY_ID = "rettifica"

    /**
     * Saldo corrente di un conto: apertura più tutti i suoi movimenti.
     *
     * Il saldo non è mai un valore memorizzato da tenere aggiornato — è sempre ricalcolato.
     * Un saldo scritto da qualche parte è la classica fonte di numeri che divergono dopo
     * una cancellazione o un ripristino da backup.
     */
    fun balanceOf(account: Account, transactions: List<Transaction>): Money =
        account.openingBalance + transactions.filter { it.accountId == account.id }.map { it.amount }.sum()

    fun balances(accounts: List<Account>, transactions: List<Transaction>): Map<String, Money> {
        val perAccount = transactions.groupBy { it.accountId }
        return accounts.associate { account ->
            account.id to account.openingBalance + (perAccount[account.id].orEmpty().map { it.amount }.sum())
        }
    }

    /** Ripartisce i saldi fra disponibile e vincolato. I conti archiviati non contano. */
    fun totals(accounts: List<Account>, transactions: List<Transaction>): Totals {
        val byId = balances(accounts, transactions)
        var available = Money.ZERO
        var constrained = Money.ZERO
        accounts.filterNot { it.archived }.forEach { account ->
            val balance = byId[account.id] ?: Money.ZERO
            if (account.includedInTotal) available += balance else constrained += balance
        }
        return Totals(available, constrained)
    }

    /**
     * I soli movimenti che rappresentano denaro davvero consumato o guadagnato.
     * Esclude le gambe dei trasferimenti, che spostano e basta.
     */
    fun spending(transactions: List<Transaction>): List<Transaction> =
        transactions.filterNot { it.isTransfer }

    /** Totale speso nel periodo, come numero positivo. Le entrate non lo riducono. */
    fun totalSpent(transactions: List<Transaction>): Money =
        spending(transactions).filter { it.isExpense }.map { it.amount }.sum().abs()

    /** Totale entrato nel periodo. */
    fun totalEarned(transactions: List<Transaction>): Money =
        spending(transactions).filter { it.isIncome }.map { it.amount }.sum()

    /**
     * Trasferisce denaro fra due conti, producendo le due gambe collegate.
     *
     * È l'unica operazione di spostamento: il prelievo al bancomat è questo, con il conto
     * di destinazione impostato sui contanti.
     *
     * @param amount importo da spostare, in valore assoluto; il segno lo mette la funzione.
     */
    fun transfer(
        from: Account,
        to: Account,
        amount: Money,
        date: LocalDate,
        description: String = "",
        groupId: String,
        idFactory: (leg: Int) -> String,
    ): Pair<Transaction, Transaction> {
        require(from.id != to.id) { "Un trasferimento richiede due conti diversi" }
        require(!amount.isZero) { "Un trasferimento di zero non ha effetto" }
        val magnitude = amount.abs()
        val label = description.ifBlank { "Da ${from.name} a ${to.name}" }

        val outgoing = Transaction(
            id = idFactory(0),
            amount = -magnitude,
            date = date,
            categoryId = ADJUSTMENT_CATEGORY_ID,
            accountId = from.id,
            transferGroupId = groupId,
            description = label,
            source = TransactionSource.TRANSFER,
        )
        val incoming = outgoing.copy(
            id = idFactory(1),
            amount = magnitude,
            accountId = to.id,
        )
        return outgoing to incoming
    }

    /**
     * Allinea il saldo di un conto a quello reale, generando il movimento di differenza.
     *
     * Restituisce null se il saldo è già allineato, così chiamarla è sempre sicuro.
     *
     * Non sovrascrive nulla: la differenza diventa un movimento visibile, categorizzato
     * come rettifica. Il saldo torna giusto e la storia resta vera.
     */
    fun reconcile(
        account: Account,
        transactions: List<Transaction>,
        realBalance: Money,
        date: LocalDate,
        id: String,
    ): Transaction? {
        val delta = realBalance - balanceOf(account, transactions)
        if (delta.isZero) return null
        return Transaction(
            id = id,
            amount = delta,
            date = date,
            categoryId = ADJUSTMENT_CATEGORY_ID,
            accountId = account.id,
            description = if (delta.isExpense) "Rettifica in meno" else "Rettifica in più",
            source = TransactionSource.ADJUSTMENT,
        )
    }
}
