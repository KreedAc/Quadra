package it.quadra.core.ledger

import it.quadra.core.model.Money
import it.quadra.core.model.Transaction
import it.quadra.core.model.TransactionSource
import java.time.Instant
import java.time.LocalDate

/**
 * Correggere e cancellare i movimenti.
 *
 * **Cancellare significa cancellare.** Niente stato "annullato", niente riga barrata,
 * niente movimento di storno: chi ha sbagliato a inserire vuole che quella riga non sia
 * mai esistita, non vuole un promemoria del proprio errore in mezzo alle spese. Il credito
 * torna al suo posto da solo, perché il saldo è sempre ricalcolato come apertura più
 * movimenti e non è mai un numero scritto da qualche parte — vedi [Ledger.balanceOf].
 *
 * La scelta è sostenibile perché non esiste sincronizzazione fra dispositivi: il backup è
 * un'istantanea completa, non un registro di differenze, quindi non serve nessuna lapide
 * per propagare una cancellazione altrove.
 *
 * Restano due casi in cui cancellare o modificare ingenuamente romperebbe i conti, ed è
 * il motivo per cui questo file esiste invece di lasciare fare `copy()` al chiamante.
 */
object Edits {

    /**
     * Le due gambe di un trasferimento, o il solo movimento se non lo è.
     */
    fun legsOf(transaction: Transaction, all: List<Transaction>): List<Transaction> {
        val group = transaction.transferGroupId ?: return listOf(transaction)
        return all.filter { it.transferGroupId == group }
    }

    /**
     * Gli identificativi da rimuovere per cancellare davvero questo movimento.
     *
     * Prima trappola: un trasferimento è fatto di due gambe su due conti diversi.
     * Cancellarne una sola lascerebbe l'altra orfana, e i cento euro comparirebbero dal
     * nulla su un conto senza uscire dall'altro. Le gambe si cancellano insieme, sempre,
     * da qualunque delle due l'utente sia partito.
     */
    fun deletionSet(transaction: Transaction, all: List<Transaction>): Set<String> =
        legsOf(transaction, all).map { it.id }.toSet()

    /**
     * Applica la cancellazione a un elenco di movimenti.
     * Restituisce l'elenco senza le righe rimosse: il saldo si riallinea da sé.
     */
    fun delete(transaction: Transaction, all: List<Transaction>): List<Transaction> {
        val toRemove = deletionSet(transaction, all)
        return all.filterNot { it.id in toRemove }
    }

    /**
     * Se il movimento cancellato era stato generato da una regola ricorrente, la data va
     * segnata come saltata.
     *
     * Seconda trappola: senza questo, la generazione delle ricorrenti — che gira a ogni
     * avvio ed è idempotente sulle date già presenti — ricreerebbe al riavvio successivo
     * esattamente il movimento appena cancellato. L'utente lo cancella, riapre l'app, e
     * se lo ritrova. Restituisce null quando non c'è nessuna regola da avvisare.
     */
    fun skipForRule(transaction: Transaction): Pair<String, LocalDate>? =
        if (transaction.source == TransactionSource.RECURRING && transaction.recurringRuleId != null) {
            transaction.recurringRuleId to transaction.date
        } else {
            null
        }

    /**
     * Sposta un movimento su un altro conto — il caso "l'avevo messo sulla carta X ma era
     * la Y".
     *
     * Non serve nessuna compensazione: cambiando il conto, entrambi i saldi si ricalcolano
     * corretti al giro successivo. Il patrimonio complessivo non cambia perché il denaro
     * non si crea né si distrugge, si sposta.
     *
     * Rifiuta le gambe di trasferimento: quelle si correggono con [editTransfer], perché
     * cambiare un solo lato lascerebbe entrambe le gambe sullo stesso conto.
     */
    fun moveToAccount(
        transaction: Transaction,
        accountId: String,
        now: Instant = Instant.EPOCH,
    ): Transaction {
        require(!transaction.isTransfer) {
            "Le gambe di un trasferimento si correggono con editTransfer, non una alla volta"
        }
        return transaction.copy(accountId = accountId, updatedAt = now)
    }

    /**
     * Riscrive un movimento con quello che l'utente ha corretto nel foglio di dettaglio.
     *
     * L'importo arriva come grandezza e il verso viene dal movimento di partenza: chi
     * corregge uno stipendio da 1.200 a 1.250 non sta dicendo che ora è un'uscita, e
     * chiedere all'utente di digitare il segno sarebbe un modo garantito per sbagliarlo.
     * Il verso si cambia scegliendo una categoria dell'altro tipo, che è una decisione
     * esplicita, non un carattere digitato di sfuggita.
     *
     * L'identificativo e la data di creazione non si toccano mai: il movimento resta lo
     * stesso oggetto dentro i backup già scritti, corretto ma non sostituito.
     *
     * Rifiuta le gambe di trasferimento come [moveToAccount], e per lo stesso motivo.
     */
    fun edit(
        transaction: Transaction,
        amount: Money,
        categoryId: String,
        accountId: String,
        date: LocalDate,
        description: String = transaction.description,
        notes: String = transaction.notes,
        now: Instant = Instant.EPOCH,
    ): Transaction {
        require(!transaction.isTransfer) {
            "Le gambe di un trasferimento si correggono con editTransfer, non una alla volta"
        }
        require(!amount.isZero) { "Un movimento da zero non ha senso: si cancella" }
        return transaction.copy(
            amount = if (transaction.amount.isIncome) amount.asIncome() else amount.asExpense(),
            categoryId = categoryId,
            accountId = accountId,
            date = date,
            description = description.trim(),
            notes = notes.trim(),
            updatedAt = now,
        )
    }

    /**
     * Riscrive entrambe le gambe di un trasferimento in modo che restino coerenti.
     *
     * L'identificativo di ciascuna gamba non cambia mai: i movimenti finiscono nei backup
     * e devono restare la stessa cosa prima e dopo una correzione.
     */
    fun editTransfer(
        legs: List<Transaction>,
        fromAccountId: String,
        toAccountId: String,
        amount: Money,
        date: LocalDate,
        description: String? = null,
        now: Instant = Instant.EPOCH,
    ): List<Transaction> {
        require(legs.size == 2) { "Un trasferimento ha esattamente due gambe, ricevute ${legs.size}" }
        require(legs.all { it.isTransfer }) { "Tutte le gambe devono appartenere a un trasferimento" }
        require(fromAccountId != toAccountId) { "Un trasferimento richiede due conti diversi" }
        require(!amount.isZero) { "Un trasferimento di zero non ha effetto" }

        val magnitude = amount.abs()
        // Si riconoscono per segno e non per posizione: l'elenco può arrivare in
        // qualsiasi ordine da una query sul database.
        val outgoing = legs.first { it.amount.isExpense }
        val incoming = legs.first { it.amount.isIncome }

        return listOf(
            outgoing.copy(
                amount = -magnitude,
                accountId = fromAccountId,
                date = date,
                description = description ?: outgoing.description,
                updatedAt = now,
            ),
            incoming.copy(
                amount = magnitude,
                accountId = toAccountId,
                date = date,
                description = description ?: incoming.description,
                updatedAt = now,
            ),
        )
    }
}
