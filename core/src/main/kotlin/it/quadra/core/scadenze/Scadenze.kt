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
    /** Zero il giorno stesso, positivo dopo, negativo per quelle che devono arrivare. */
    val giorniDiRitardo: Int,
    /** Quando è stata rimandata, se il giorno scelto non è ancora arrivato. */
    val rimandataAl: LocalDate? = null,
) {
    val inRitardo: Boolean get() = giorniDiRitardo >= 0 && rimandataAl == null
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
     * Vale insieme alla data di creazione, che è il limite vero: questa resta come rete
     * per una regola tenuta ferma a lungo e poi riattivata. Un anno copre anche le
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
    ): List<Scadenza> = passate(regole, registrate, oggi)
        // Una scadenza rimandata torna il giorno che l'utente ha scelto, non prima.
        .filter { it.rimandataAl == null }

    /**
     * Le scadenze passate che l'utente ha rimandato e che aspettano il loro giorno.
     *
     * Restano visibili. Rimandare non è cancellare, e una scadenza che sparisce dalla
     * schermata dopo un rinvio è indistinguibile da una scadenza persa: l'utente non ha
     * modo di sapere se tornerà, né di cambiare idea prima che torni.
     */
    fun rimandate(
        regole: List<RecurringRule>,
        registrate: Set<String>,
        oggi: LocalDate = LocalDate.now(),
    ): List<Scadenza> = passate(regole, registrate, oggi)
        .filter { it.rimandataAl != null }

    private fun passate(
        regole: List<RecurringRule>,
        registrate: Set<String>,
        oggi: LocalDate,
    ): List<Scadenza> {
        val finestra = oggi.minusDays(GIORNI_ARRETRATI)
        return regole.flatMap { regola ->
            // Mai prima di quando la regola è nata: le occorrenze anteriori al
            // promemoria non sono arretrati, sono descrizione di come funziona la spesa.
            val da = maxOf(finestra, regola.startDate, regola.creatoIl)
            RecurrenceEngine.occurrences(regola, da, oggi)
                .filter { it !in regola.skippedDates }
                .filter { chiave(regola.id, it) !in registrate }
                .map { occorrenza ->
                    val rinviata = regola.rimandi[occorrenza]
                    Scadenza(
                        regola = regola,
                        occorrenza = occorrenza,
                        giorniDiRitardo = ChronoUnit.DAYS.between(occorrenza, oggi).toInt(),
                        rimandataAl = rinviata?.takeIf { it.isAfter(oggi) },
                    )
                }
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
