package it.lemiespese.core.model

import java.time.Instant
import java.time.LocalDate

/**
 * Da dove arriva un movimento.
 *
 * Serve a tre cose concrete: mostrare all'utente perché una riga è comparsa senza che
 * la scrivesse lui, evitare di reimportare due volte lo stesso movimento, e permettere
 * di aggiungere in futuro nuove origini senza migrare lo schema del database.
 *
 * [NOTIFICATION] è dichiarata ma non usata: la cattura automatica dalle notifiche è
 * fuori dalla v1, per scelta. Tenerla qui costa nulla oggi ed evita una migrazione domani.
 */
enum class TransactionSource {
    MANUAL,
    RECURRING,
    CSV_IMPORT,
    NOTIFICATION,
}

/**
 * Stato di un movimento rispetto alla sua definitività.
 *
 * Non è pignoleria: al distributore di benzina la preautorizzazione blocca un importo
 * (spesso 100 €) diverso dall'addebito reale, e lo stesso vale per hotel e noleggi.
 * Un'app che tratta l'autorizzazione come definitiva mostra totali sbagliati proprio
 * nel caso più frequente, e l'utente smette di fidarsi dei numeri.
 */
enum class TransactionStatus {
    /** Importo non ancora definitivo: escluso dai totali "certi", mostrato a parte. */
    PENDING,
    SETTLED,
}

/**
 * Un movimento: una spesa o un'entrata.
 *
 * [amount] è firmato — negativo per le uscite. Vedi [Money] per il perché.
 *
 * [id] è un UUID e non un intero autoincrementale, perché i movimenti attraversano
 * backup e ripristini su dispositivi diversi e devono restare identificabili senza
 * dipendere da una sequenza locale.
 */
data class Transaction(
    val id: String,
    val amount: Money,
    val date: LocalDate,
    val categoryId: String,
    val description: String = "",
    /** Esercente o controparte, quando è nota. Separata da [description] per poterla raggruppare. */
    val merchant: String? = null,
    val notes: String = "",
    val source: TransactionSource = TransactionSource.MANUAL,
    val status: TransactionStatus = TransactionStatus.SETTLED,
    /** Regola che ha generato il movimento, se [source] è [TransactionSource.RECURRING]. */
    val recurringRuleId: String? = null,
    /**
     * Impronta dell'origine, usata per la deduplica in import successivi.
     * Per il CSV è un hash di data, importo e descrizione grezza della riga.
     */
    val externalKey: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
) {
    val isExpense: Boolean get() = amount.isExpense
    val isIncome: Boolean get() = amount.isIncome
}
