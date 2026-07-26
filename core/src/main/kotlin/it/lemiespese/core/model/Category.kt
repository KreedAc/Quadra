package it.lemiespese.core.model

/** Distingue le categorie di uscita da quelle di entrata. */
enum class CategoryKind { EXPENSE, INCOME }

/**
 * Categoria di spesa o entrata.
 *
 * [id] è una stringa stabile e non un intero autoincrementale: le categorie finiscono
 * nei backup e devono poter essere riconciliate fra dispositivi diversi senza collisioni.
 * Le categorie di sistema hanno id leggibili ("casa.condominio") proprio per restare
 * riconoscibili in un file di backup aperto a mano.
 *
 * [icon] e [colorArgb] sono dati, non risorse Android: il modulo :core non conosce
 * R.drawable. Lo strato UI mappa la chiave sull'icona effettiva.
 */
data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val group: String,
    val icon: String,
    val colorArgb: Int,
    /** Le categorie di sistema non sono cancellabili, solo nascondibili. */
    val isSystem: Boolean = false,
    val hidden: Boolean = false,
) {
    val isExpense: Boolean get() = kind == CategoryKind.EXPENSE
    val isIncome: Boolean get() = kind == CategoryKind.INCOME
}

/**
 * Categorie predefinite, tarate sulle voci di spesa di una famiglia italiana.
 *
 * Questa lista è il primo contatto dell'utente con l'app: se si ritrova subito
 * "Condominio", "Bollo auto" e "IMU" senza doverle creare, l'app sembra pensata per lui.
 * È il motivo per cui il taglio italiano non è un dettaglio di localizzazione.
 */
object DefaultCategories {

    // Palette segnaposto, da sostituire con i token del design definitivo.
    private const val VERDE = 0xFF2E7D5B.toInt()
    private const val BLU = 0xFF2F6FB0.toInt()
    private const val VIOLA = 0xFF6C4FA3.toInt()
    private const val ARANCIO = 0xFFC1662F.toInt()
    private const val ROSSO = 0xFFB3453C.toInt()
    private const val TEAL = 0xFF2E7D7D.toInt()
    private const val OCRA = 0xFF9A7B2E.toInt()
    private const val GRIGIO = 0xFF5B6670.toInt()

    val all: List<Category> = listOf(
        // --- Casa -------------------------------------------------------------
        expense("casa.affitto_mutuo", "Affitto e mutuo", "Casa", "home", BLU),
        expense("casa.condominio", "Condominio", "Casa", "apartment", BLU),
        expense("casa.bollette", "Bollette", "Casa", "bolt", BLU),
        expense("casa.internet_telefono", "Internet e telefono", "Casa", "wifi", BLU),
        expense("casa.manutenzione", "Manutenzione casa", "Casa", "build", BLU),
        expense("casa.arredamento", "Arredamento", "Casa", "chair", BLU),

        // --- Spesa quotidiana -------------------------------------------------
        expense("spesa.alimentari", "Spesa alimentare", "Quotidiano", "cart", VERDE),
        expense("spesa.bar", "Bar e colazioni", "Quotidiano", "coffee", VERDE),
        expense("spesa.ristoranti", "Ristoranti", "Quotidiano", "restaurant", VERDE),
        expense("spesa.tabacchi", "Tabacchi ed edicola", "Quotidiano", "newspaper", VERDE),

        // --- Trasporti --------------------------------------------------------
        expense("trasporti.carburante", "Carburante", "Trasporti", "fuel", ARANCIO),
        expense("trasporti.mezzi", "Mezzi pubblici", "Trasporti", "train", ARANCIO),
        expense("trasporti.pedaggi", "Pedaggi e parcheggi", "Trasporti", "toll", ARANCIO),
        expense("trasporti.assicurazione", "Assicurazione veicolo", "Trasporti", "shield", ARANCIO),
        expense("trasporti.bollo", "Bollo auto", "Trasporti", "receipt", ARANCIO),
        expense("trasporti.manutenzione", "Officina e revisione", "Trasporti", "wrench", ARANCIO),

        // --- Tasse e tributi --------------------------------------------------
        expense("tasse.imu", "IMU", "Tasse e tributi", "account_balance", ROSSO),
        expense("tasse.tari", "TARI", "Tasse e tributi", "delete", ROSSO),
        expense("tasse.irpef", "Imposte sul reddito", "Tasse e tributi", "account_balance", ROSSO),
        expense("tasse.commercialista", "Commercialista e consulenze", "Tasse e tributi", "gavel", ROSSO),
        expense("tasse.altro", "Altri tributi", "Tasse e tributi", "account_balance", ROSSO),

        // --- Salute -----------------------------------------------------------
        expense("salute.farmacia", "Farmacia", "Salute", "pill", TEAL),
        expense("salute.visite", "Visite e analisi", "Salute", "stethoscope", TEAL),
        expense("salute.dentista", "Dentista", "Salute", "dental", TEAL),
        expense("salute.occhiali", "Occhiali e lenti", "Salute", "glasses", TEAL),

        // --- Persona e famiglia ----------------------------------------------
        expense("persona.abbigliamento", "Abbigliamento", "Persona", "shirt", VIOLA),
        expense("persona.cura", "Cura personale", "Persona", "spa", VIOLA),
        expense("persona.istruzione", "Scuola e istruzione", "Persona", "school", VIOLA),
        expense("persona.bambini", "Spese per i figli", "Persona", "child", VIOLA),
        expense("persona.animali", "Animali domestici", "Persona", "pets", VIOLA),
        expense("persona.regali", "Regali", "Persona", "gift", VIOLA),

        // --- Tempo libero -----------------------------------------------------
        expense("svago.abbonamenti", "Abbonamenti digitali", "Tempo libero", "subscriptions", OCRA),
        expense("svago.palestra", "Sport e palestra", "Tempo libero", "fitness", OCRA),
        expense("svago.cultura", "Cinema, libri e concerti", "Tempo libero", "movie", OCRA),
        expense("svago.viaggi", "Viaggi e vacanze", "Tempo libero", "flight", OCRA),

        // --- Finanza ----------------------------------------------------------
        expense("finanza.commissioni", "Commissioni bancarie", "Finanza", "bank", GRIGIO),
        expense("finanza.rate", "Rate e finanziamenti", "Finanza", "credit_card", GRIGIO),
        expense("finanza.risparmio", "Accantonamenti", "Finanza", "savings", GRIGIO),
        expense("finanza.beneficenza", "Donazioni", "Finanza", "volunteer", GRIGIO),

        expense("altro", "Altro", "Altro", "more", GRIGIO),

        // --- Entrate ----------------------------------------------------------
        income("entrate.stipendio", "Stipendio", "Entrate", "wallet", VERDE),
        income("entrate.autonomo", "Lavoro autonomo", "Entrate", "briefcase", VERDE),
        income("entrate.rimborsi", "Rimborsi", "Entrate", "undo", VERDE),
        income("entrate.bonus", "Bonus e sussidi", "Entrate", "star", VERDE),
        income("entrate.investimenti", "Rendite e investimenti", "Entrate", "trending_up", VERDE),
        income("entrate.altro", "Altre entrate", "Entrate", "more", VERDE),
    )

    val expenses: List<Category> get() = all.filter { it.isExpense }
    val incomes: List<Category> get() = all.filter { it.isIncome }

    /** Categoria di ripiego quando l'import non riesce ad attribuirne una. */
    val fallbackExpense: Category get() = all.first { it.id == "altro" }
    val fallbackIncome: Category get() = all.first { it.id == "entrate.altro" }

    fun byId(id: String): Category? = all.firstOrNull { it.id == id }

    private fun expense(id: String, name: String, group: String, icon: String, color: Int) =
        Category(id, name, CategoryKind.EXPENSE, group, icon, color, isSystem = true)

    private fun income(id: String, name: String, group: String, icon: String, color: Int) =
        Category(id, name, CategoryKind.INCOME, group, icon, color, isSystem = true)
}
