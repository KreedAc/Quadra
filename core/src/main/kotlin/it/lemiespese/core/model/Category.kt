package it.lemiespese.core.model

/** Distingue le categorie di uscita da quelle di entrata. */
enum class CategoryKind { EXPENSE, INCOME }

/**
 * Categoria di spesa o entrata, su due livelli.
 *
 * I due livelli non sono una raffinatezza tassonomica, sono la ragione per cui
 * l'inserimento è veloce. Il primo livello è una dozzina di voci disposte in una griglia
 * fissa: sta tutto su una schermata, non scorre, e dopo una settimana il pollice ci
 * arriva senza leggere. Il secondo livello è il dettaglio vero, e resta a un tocco
 * perché la scelta precedente lo ha già filtrato.
 *
 * Un elenco piatto di quaranta voci costringerebbe a cercare ogni volta, ed è
 * esattamente ciò che rende lenti i tracker manuali.
 *
 * [id] è una stringa stabile e leggibile ("casa.condominio") invece di un intero
 * autoincrementale: le categorie finiscono nei backup e devono restare riconoscibili
 * anche in un file aperto a mano, oltre che riconciliabili fra dispositivi diversi.
 *
 * [icon] e [colorArgb] sono dati, non risorse Android: il modulo :core non conosce
 * R.drawable. Lo strato UI mappa la chiave sull'icona effettiva.
 */
data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    /** null per le voci di primo livello; l'id del genitore per le sottocategorie. */
    val parentId: String?,
    /**
     * Colore della voce. Le sottocategorie ereditano quello del genitore: nell'interfaccia
     * i chip di dettaglio sono tinti col colore della categoria scelta, quindi tenerlo
     * già risolto qui evita una risalita dell'albero a ogni disegno di riga.
     */
    val colorArgb: Int,
    /** Presente solo al primo livello: nella griglia le voci hanno un'icona, i chip no. */
    val icon: String? = null,
    /** Ordine di comparsa nella griglia, per frequenza d'uso attesa. */
    val sortOrder: Int = 0,
    /** Le categorie di sistema non sono cancellabili, solo nascondibili. */
    val isSystem: Boolean = false,
    val hidden: Boolean = false,
) {
    val isExpense: Boolean get() = kind == CategoryKind.EXPENSE
    val isIncome: Boolean get() = kind == CategoryKind.INCOME
    val isTopLevel: Boolean get() = parentId == null
}

/**
 * Categorie predefinite, tarate sulle voci di spesa di una famiglia italiana.
 *
 * L'ordine del primo livello segue la frequenza d'uso attesa, non la parentela logica:
 * spesa, bar e ristoranti stanno in cima perché sono ciò che si inserisce ogni giorno.
 * Ordinare per tassonomia sarebbe più elegante e più lento da usare.
 *
 * Che ci si ritrovi subito "Condominio", "Bollo auto" e "IMU" senza doverle creare è il
 * motivo per cui il taglio italiano non è un dettaglio di localizzazione.
 */
object DefaultCategories {

    // Palette verificata su fondo chiaro e scuro: banda di luminosità, croma minimo,
    // contrasto sulla superficie e separazione per deuteranopia e tritanopia.
    private const val VERDE = 0xFF12A374.toInt()
    private const val VIOLA = 0xFF8B5CF6.toInt()
    private const val CORALLO = 0xFFE85545.toInt()
    private const val CIANO = 0xFF0E93AE.toInt()
    private const val AMBRA = 0xFFB07F0A.toInt()
    private const val BLU = 0xFF3B7BE8.toInt()
    private const val ROSA = 0xFFDB4F92.toInt()
    private const val OLIVA = 0xFF6E9E2F.toInt()
    private const val GRIGIO = 0xFF6B7A89.toInt()

    val all: List<Category> = buildList {
        group("spesa", "Spesa", VERDE, "cart", 0) {
            listOf("Supermercato", "Mercato", "Panetteria", "Alimentari", "Surgelati")
        }
        group("bar", "Bar", OLIVA, "coffee", 1) {
            listOf("Colazione", "Caffè", "Aperitivo", "Gelateria")
        }
        group("ristoranti", "Ristoranti", CORALLO, "restaurant", 2) {
            listOf("Pizzeria", "Trattoria", "Sushi", "Fast food", "Asporto")
        }
        group("trasporti", "Trasporti", AMBRA, "car", 3) {
            listOf(
                "Carburante", "Assicurazione", "Bollo auto", "Officina e revisione",
                "Pedaggi e parcheggi", "Mezzi pubblici", "Taxi",
            )
        }
        group("casa", "Casa", BLU, "building", 4) {
            listOf("Affitto", "Mutuo", "Condominio", "Manutenzione", "Arredamento", "Pulizie")
        }
        group("bollette", "Bollette", VIOLA, "bolt", 5) {
            listOf("Luce", "Gas", "Acqua", "Internet", "Telefono")
        }
        group("salute", "Salute", CIANO, "pill", 6) {
            listOf("Farmacia", "Visite e analisi", "Dentista", "Occhiali e lenti")
        }
        group("shopping", "Shopping", ROSA, "bag", 7) {
            listOf("Abbigliamento", "Scarpe", "Elettronica", "Cura personale", "Regali")
        }
        group("svago", "Svago", OLIVA, "ticket", 8) {
            listOf(
                "Cinema e concerti", "Libri", "Palestra e sport",
                "Viaggi e vacanze", "Abbonamenti digitali",
            )
        }
        group("tasse", "Tasse", CORALLO, "receipt", 9) {
            listOf("IMU", "TARI", "Imposte sul reddito", "Commercialista", "Multe")
        }
        group("altro", "Altro", GRIGIO, "dots", 10) {
            listOf(
                "Animali domestici", "Scuola e istruzione", "Spese per i figli",
                "Commissioni bancarie", "Rate e finanziamenti", "Donazioni",
            )
        }
        group("entrate", "Entrate", VERDE, "wallet", 11, kind = CategoryKind.INCOME) {
            listOf(
                "Stipendio", "Lavoro autonomo", "Rimborsi",
                "Bonus e sussidi", "Rendite e investimenti",
            )
        }
    }

    /** Le voci della griglia, nell'ordine in cui vanno disposte. */
    val topLevel: List<Category> = all.filter { it.isTopLevel }.sortedBy { it.sortOrder }

    val expenses: List<Category> get() = all.filter { it.isExpense }
    val incomes: List<Category> get() = all.filter { it.isIncome }

    /** Categoria di ripiego quando l'import non riesce ad attribuirne una. */
    val fallbackExpense: Category get() = byId("altro")!!
    val fallbackIncome: Category get() = byId("entrate")!!

    private val index: Map<String, Category> = all.associateBy { it.id }

    fun byId(id: String): Category? = index[id]

    /** Le sottocategorie di una voce di primo livello, nell'ordine di dichiarazione. */
    fun childrenOf(parentId: String): List<Category> = all.filter { it.parentId == parentId }

    /** Risale al primo livello, per aggregare le statistiche per categoria principale. */
    fun rootOf(id: String): Category? {
        val category = byId(id) ?: return null
        return if (category.isTopLevel) category else byId(category.parentId!!)
    }

    /**
     * Dichiara una voce di primo livello e le sue sottocategorie in un colpo solo.
     * Gli id dei figli derivano dal nome, così restano leggibili in un file di backup.
     */
    private fun MutableList<Category>.group(
        id: String,
        name: String,
        color: Int,
        icon: String,
        sortOrder: Int,
        kind: CategoryKind = CategoryKind.EXPENSE,
        children: () -> List<String>,
    ) {
        add(
            Category(
                id = id,
                name = name,
                kind = kind,
                parentId = null,
                colorArgb = color,
                icon = icon,
                sortOrder = sortOrder,
                isSystem = true,
            )
        )
        children().forEachIndexed { i, childName ->
            add(
                Category(
                    id = "$id.${slug(childName)}",
                    name = childName,
                    kind = kind,
                    parentId = id,
                    colorArgb = color,
                    icon = null,
                    sortOrder = i,
                    isSystem = true,
                )
            )
        }
    }

    private fun slug(name: String): String = buildString {
        for (ch in name.lowercase()) {
            when {
                ch.isLetterOrDigit() && ch.code < 128 -> append(ch)
                ch in "àáâä" -> append('a')
                ch in "èéêë" -> append('e')
                ch in "ìíîï" -> append('i')
                ch in "òóôö" -> append('o')
                ch in "ùúûü" -> append('u')
                isNotEmpty() && last() != '_' -> append('_')
            }
        }
    }.trim('_')
}
