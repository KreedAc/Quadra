package it.quadra.core.model

import java.time.LocalDate

enum class RecurrenceUnit { DAY, WEEK, MONTH, YEAR }

/**
 * Promemoria per le spese che si ripetono.
 *
 * Ricorda, non registra. È una distinzione che sembra sottile e non lo è: una regola
 * ricorrente descrive un pagamento *previsto*, e l'app non ha modo di sapere se è
 * davvero avvenuto. Un abbonamento può non partire per fondi insufficienti e venire
 * addebitato due giorni dopo; un bonifico può essere rifiutato. Se l'app scrivesse il
 * movimento alla scadenza, mostrerebbe un saldo che non esiste — e una carta in negativo
 * per un addebito mai avvenuto porta chi la guarda a decidere male su soldi veri.
 *
 * Quindi alla scadenza l'app chiede, e il movimento nasce solo quando l'utente conferma
 * quanto ha pagato davvero. Questo risolve anche le bollette, dove l'importo cambia ogni
 * volta e un valore fisso sarebbe sbagliato per definizione.
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
     * Scadenze rimandate: da quale occorrenza a quando riproporla.
     *
     * Rimandare sposta il promemoria, mai la cadenza. Chi rinvia l'affitto del 15 di tre
     * giorni vuole che gli venga richiesto il 18, non che l'affitto diventi una spesa del
     * 18 di ogni mese: sommare i rinvii alla regola la farebbe scivolare di mese in mese
     * fino a non somigliare più a niente.
     */
    val rimandi: Map<LocalDate, LocalDate> = emptyMap(),
    /**
     * Quando la regola è stata creata.
     *
     * Serve a non chiedere conferma per scadenze anteriori al promemoria stesso. Chi
     * scrive oggi che l'assicurazione parte dal 2 luglio sta descrivendo un impegno,
     * non confessando di aver saltato la rata di luglio: quelle occorrenze non sono mai
     * passate da qui, e chiederne conto è rumore.
     *
     * Resta distinto da [startDate] perché gli arretrati veri servono ancora: una regola
     * attiva da mesi, con l'app non aperta per tre settimane, deve chiedere tutto quello
     * che è maturato nel frattempo.
     */
    val creatoIl: LocalDate = startDate,
) {
    init {
        require(every >= 1) { "L'intervallo di ricorrenza deve essere almeno 1, ricevuto $every" }
        require(dayOfMonth == null || dayOfMonth in 1..31) {
            "Il giorno del mese deve stare fra 1 e 31, ricevuto $dayOfMonth"
        }
    }
}
