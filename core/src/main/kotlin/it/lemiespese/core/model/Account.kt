package it.lemiespese.core.model

enum class AccountKind { CASH, CARD, BANK, SAVINGS, PREPAID, OTHER }

/**
 * Un conto: contanti, una carta, un conto corrente, un fondo da parte.
 *
 * Esiste perché la domanda "quanto ho speso" e la domanda "quanto mi resta" hanno
 * risposte diverse, e la seconda richiede di sapere dove stanno i soldi.
 *
 * [includedInTotal] è la parte non ovvia. Nasce da un caso concreto: una carta ADI
 * contiene denaro che non è liberamente spendibile, e sommarlo alla disponibilità reale
 * porta a credere di avere più di quanto si può effettivamente usare. Il denaro vincolato
 * però non va nascosto — sparirebbe dalla vista e ci si dimenticherebbe che esiste —
 * quindi resta visibile, ma in un totale separato. Vedi [it.lemiespese.core.ledger.Ledger].
 */
data class Account(
    val id: String,
    val name: String,
    val kind: AccountKind,
    val colorArgb: Int,
    /**
     * Saldo al momento in cui si è iniziato a usare l'app. Il saldo corrente è questo
     * più la somma dei movimenti: così non c'è mai un saldo "scritto" da tenere
     * sincronizzato, che è la classica fonte di numeri che divergono.
     */
    val openingBalance: Money = Money.ZERO,
    /** Se false il saldo finisce nel totale dei vincolati, non in quello disponibile. */
    val includedInTotal: Boolean = true,
    val icon: String? = null,
    val sortOrder: Int = 0,
    /** Un conto archiviato non compare nelle scelte ma i suoi movimenti restano. */
    val archived: Boolean = false,
) {
    val isCash: Boolean get() = kind == AccountKind.CASH
}

/**
 * I due conti con cui parte chiunque. Il resto lo aggiunge l'utente.
 *
 * Deliberatamente pochi: un'app che all'avvio chiede di configurare otto conti viene
 * chiusa prima di essere usata. Contanti e carta coprono il primo giorno, gli altri
 * si aggiungono quando servono.
 */
object DefaultAccounts {
    const val CASH_ID = "contanti"
    const val CARD_ID = "carta"

    val cash = Account(
        id = CASH_ID,
        name = "Contanti",
        kind = AccountKind.CASH,
        colorArgb = 0xFF12A374.toInt(),
        icon = "wallet",
        sortOrder = 0,
    )

    val card = Account(
        id = CARD_ID,
        name = "Carta",
        kind = AccountKind.CARD,
        colorArgb = 0xFF3B7BE8.toInt(),
        icon = "credit_card",
        sortOrder = 1,
    )

    val all: List<Account> = listOf(cash, card)
}
