package it.lemiespese.core.model

import java.time.LocalDate

enum class RecurrenceUnit { DAY, WEEK, MONTH, YEAR }

/**
 * Regola per le spese che si ripetono.
 *
 * È la risposta al problema di abbandono dei tracker manuali: affitto, condominio,
 * bollette, abbonamenti, rate, assicurazione e bollo sono la parte grossa e prevedibile
 * del bilancio di una famiglia, e l'app può inserirli da sola. L'utente resta a mano
 * solo sulle spese variabili, che sono quelle che gli interessa davvero guardare.
 *
 * [every] più [unit] copre tutti i casi reali senza un'enumerazione rigida:
 * mensile è (1, MONTH), le bollette gas bimestrali sono (2, MONTH), l'IMU semestrale
 * è (6, MONTH), il bollo auto è (1, YEAR).
 */
data class RecurringRule(
    val id: String,
    val description: String,
    val amount: Money,
    val categoryId: String,
    /** Conto su cui la regola genera i movimenti. */
    val accountId: String,
    val every: Int,
    val unit: RecurrenceUnit,
    val startDate: LocalDate,
    /** Inclusa: una regola che finisce il 31/12 genera anche l'occorrenza del 31/12. */
    val endDate: LocalDate? = null,
    /**
     * Giorno del mese preferito per le ricorrenze mensili e annuali.
     * Se null si usa il giorno di [startDate]. Viene sempre limitato alla lunghezza
     * del mese di destinazione: una regola al giorno 31 cade il 28 o 29 a febbraio.
     */
    val dayOfMonth: Int? = null,
    val active: Boolean = true,
    /**
     * Date in cui la regola non deve generare nulla, perché l'utente ha cancellato quella
     * specifica occorrenza. Senza questo elenco la generazione — che gira a ogni avvio ed
     * è idempotente sulle date già presenti — ricreerebbe al riavvio successivo proprio il
     * movimento appena cancellato.
     */
    val skippedDates: Set<LocalDate> = emptySet(),
    /**
     * Se true il movimento viene creato automaticamente alla scadenza.
     * Se false l'app si limita a proporlo, e l'utente conferma l'importo —
     * utile per le bollette, dove la cifra cambia ogni volta.
     */
    val autoInsert: Boolean = true,
) {
    init {
        require(every >= 1) { "L'intervallo di ricorrenza deve essere almeno 1, ricevuto $every" }
        require(dayOfMonth == null || dayOfMonth in 1..31) {
            "Il giorno del mese deve stare fra 1 e 31, ricevuto $dayOfMonth"
        }
    }
}
