package it.quadra.core.scadenze

import it.quadra.core.model.RecurringRule
import it.quadra.core.recurrence.RecurrenceEngine
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Una scadenza di una regola: un pagamento previsto in una certa data.
 *
 * Non è un movimento. Diventa un movimento solo se e quando l'utente conferma di aver
 * pagato, e con l'importo che ha pagato davvero.
 */
data class Scadenza(
    val regola: RecurringRule,
    val occorrenza: LocalDate,
    /** Zero il giorno stesso, positivo dopo. Serve a dire "in ritardo di 3 giorni". */
    val giorniDiRitardo: Int,
) {
    val chiave: String get() = Scadenze.chiave(regola.id, occorrenza)
}

/**
 * Cosa c'è da confermare e cosa sta per arrivare.
 *
 * Lo stato di una scadenza non è memorizzato da nessuna parte: si ricava ogni volta dalle
 * regole, dalle date saltate, dai rinvii e dai movimenti già registrati. È la stessa
 * scelta del saldo, che non è mai un numero scritto ma sempre una somma — e per lo stesso
 * motivo: uno stato memorizzato può divergere dalla realtà, uno calcolato no. Cancellare
 * il movimento di una bolletta la rimette in attesa senza che nessuno debba ricordarsi di
 * aggiornare una seconda tabella.
 */
object Scadenze {

    /**
     * L'identità di una scadenza dentro i movimenti.
     *
     * Finisce in `externalKey`, che esiste apposta per legare un movimento a qualcosa di
     * esterno. Non si usa la data del movimento perché non coincide: si può pagare la
     * rata del 10 il giorno 12, e resta la rata del 10.
     */
    fun chiave(regolaId: String, occorrenza: LocalDate): String = "ric:$regolaId:$occorrenza"

    /**
     * Quanto indietro si guarda per gli arretrati.
     *
     * Senza un limite, una regola mensile creata con data di partenza vecchia di tre anni
     * proporrebbe trentasei conferme in fila al primo avvio. Un anno copre anche le
     * scadenze annuali — bollo, assicurazione — che sono le più lente che esistano.
     */
    const val GIORNI_ARRETRATI = 365L

    /**
     * Le scadenze già passate che aspettano una conferma, dalla più vecchia.
     *
     * @param registrate le chiavi dei movimenti già nati da una scadenza.
     */
    fun daConfermare(
        regole: List<RecurringRule>,
        registrate: Set<String>,
        oggi: LocalDate = LocalDate.now(),
    ): List<Scadenza> {
        val da = oggi.minusDays(GIORNI_ARRETRATI)
        return regole.flatMap { regola ->
            RecurrenceEngine.occurrences(regola, maxOf(da, regola.startDate), oggi)
                .filter { it !in regola.skippedDates }
                .filter { chiave(regola.id, it) !in registrate }
                // Una scadenza rimandata torna il giorno che l'utente ha scelto, non prima.
                .filter { occorrenza ->
                    val rinviata = regola.rimandi[occorrenza]
                    rinviata == null || !rinviata.isAfter(oggi)
                }
                .map { Scadenza(regola, it, ChronoUnit.DAYS.between(it, oggi).toInt()) }
        }.sortedWith(compareBy({ it.occorrenza }, { it.regola.description }))
    }

    /**
     * Le scadenze che devono ancora arrivare, entro [giorni] da oggi.
     *
     * Servono alla striscia "in arrivo": vedere che fra quattro giorni escono 145 € è
     * quello che permette di non spenderli, ed è l'unica parte utile che restava
     * all'inserimento automatico.
     */
    fun inArrivo(
        regole: List<RecurringRule>,
        registrate: Set<String>,
        giorni: Long = 10,
        oggi: LocalDate = LocalDate.now(),
    ): List<Scadenza> {
        val fino = oggi.plusDays(giorni)
        return regole.flatMap { regola ->
            RecurrenceEngine.occurrences(regola, oggi.plusDays(1), fino)
                .filter { it !in regola.skippedDates }
                .filter { chiave(regola.id, it) !in registrate }
                .map { Scadenza(regola, it, ChronoUnit.DAYS.between(it, oggi).toInt()) }
        }.sortedWith(compareBy({ it.occorrenza }, { it.regola.description }))
    }

    /**
     * Rimanda una scadenza di [giorni] giorni.
     * La cadenza della regola non si tocca: cambia solo quando viene richiesta.
     */
    fun rimanda(
        regola: RecurringRule,
        occorrenza: LocalDate,
        giorni: Long,
        oggi: LocalDate = LocalDate.now(),
    ): RecurringRule = regola.copy(rimandi = regola.rimandi + (occorrenza to oggi.plusDays(giorni)))

    /**
     * Salta una scadenza: quel mese non è stato pagato e non lo sarà.
     * Toglie anche l'eventuale rinvio, che non avrebbe più niente da riproporre.
     */
    fun salta(regola: RecurringRule, occorrenza: LocalDate): RecurringRule = regola.copy(
        skippedDates = regola.skippedDates + occorrenza,
        rimandi = regola.rimandi - occorrenza,
    )
}
