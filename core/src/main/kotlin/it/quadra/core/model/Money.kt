package it.quadra.core.model

import kotlin.math.absoluteValue

/**
 * Importo monetario espresso in centesimi di euro.
 *
 * Il denaro non si rappresenta con Double o Float: in virgola mobile 0,1 + 0,2 non fa 0,3,
 * e in un'app di spese l'errore diventa visibile all'utente dopo poche decine di movimenti.
 * Qui l'unità atomica è il centesimo ed è sempre un intero.
 *
 * Il segno è portatore di significato: negativo = uscita, positivo = entrata.
 * Così sommare una lista di movimenti è una somma e basta, senza casi particolari
 * e senza il rischio di dimenticarsi un segno in un ramo del codice.
 */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {

    val isExpense: Boolean get() = cents < 0
    val isIncome: Boolean get() = cents > 0
    val isZero: Boolean get() = cents == 0L

    operator fun plus(other: Money): Money = Money(cents + other.cents)
    operator fun minus(other: Money): Money = Money(cents - other.cents)
    operator fun times(factor: Int): Money = Money(cents * factor)
    operator fun unaryMinus(): Money = Money(-cents)

    fun abs(): Money = Money(cents.absoluteValue)

    /** Uscita equivalente: usato quando l'utente inserisce "12,50" intendendo una spesa. */
    fun asExpense(): Money = Money(-cents.absoluteValue)

    /** Entrata equivalente. */
    fun asIncome(): Money = Money(cents.absoluteValue)

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    /**
     * Formato italiano: "1.234,56 €".
     *
     * Volutamente implementato a mano invece che con NumberFormat: il risultato non deve
     * dipendere dal Locale del dispositivo (un utente italiano con telefono in inglese
     * deve vedere comunque la virgola) e deve essere deterministico nei test.
     *
     * @param withSymbol aggiunge " €" in coda.
     * @param withSign forza il "+" davanti alle entrate; il "-" delle uscite c'è sempre.
     */
    fun format(withSymbol: Boolean = true, withSign: Boolean = false): String = buildString {
        when {
            cents < 0 -> append('-')
            withSign && cents > 0 -> append('+')
        }
        val abs = cents.absoluteValue
        append(groupThousands(abs / 100))
        append(',')
        append((abs % 100).toString().padStart(2, '0'))
        if (withSymbol) {
            append(' ')
            append('€')
        }
    }

    override fun toString(): String = format()

    companion object {
        val ZERO: Money = Money(0)

        fun ofCents(cents: Long): Money = Money(cents)

        /** `Money.of(12, 50)` è 12,50 €. Il segno va messo su [units]. */
        fun of(units: Long, cents: Int = 0): Money {
            require(cents in 0..99) { "I centesimi devono stare fra 0 e 99, ricevuto $cents" }
            val magnitude = units.absoluteValue * 100 + cents
            return Money(if (units < 0) -magnitude else magnitude)
        }

        /**
         * Interpreta un importo scritto da un umano o letto da un CSV bancario.
         * Restituisce null se la stringa non è un importo riconoscibile: il chiamante
         * decide se è un errore di digitazione o una riga di CSV da saltare.
         *
         * Gestisce: simbolo € e sigla EUR, spazi normali e unificatori, segno davanti o
         * dietro, parentesi contabili "(12,50)" come negativo, separatore decimale
         * virgola o punto, separatore delle migliaia punto o spazio.
         *
         * L'ambiguità vera è il punto isolato. Regola adottata: un punto seguito da
         * esattamente tre cifre è un separatore di migliaia ("1.234" = 1234), altrimenti
         * è decimale ("12.5" = 12,50). Copre sia la scrittura italiana sia i CSV
         * esportati in formato anglosassone, che sono frequenti nell'home banking.
         */
        fun parse(input: String): Money? {
            var s = input.trim()
            if (s.isEmpty()) return null

            s = s.replace("€", "")
                .replace("EUR", "", ignoreCase = true)
                .replace(' ', ' ') // spazio unificatore, comune nei copia-incolla
                .replace(' ', ' ') // spazio stretto unificatore
                .replace(" ", "")
                .trim()
            if (s.isEmpty()) return null

            var negative = false
            if (s.startsWith("(") && s.endsWith(")")) {
                negative = true
                s = s.substring(1, s.length - 1)
            }
            // Il segno può stare davanti o in coda: alcuni estratti conto scrivono "12,50-".
            when {
                s.startsWith("-") -> { negative = !negative; s = s.drop(1) }
                s.startsWith("+") -> s = s.drop(1)
                s.endsWith("-") -> { negative = !negative; s = s.dropLast(1) }
                s.endsWith("+") -> s = s.dropLast(1)
            }
            if (s.isEmpty()) return null

            val lastComma = s.lastIndexOf(',')
            val lastDot = s.lastIndexOf('.')
            val decimalSeparator: Char? = when {
                lastComma >= 0 && lastDot >= 0 -> if (lastComma > lastDot) ',' else '.'
                lastComma >= 0 -> ','
                lastDot >= 0 -> if (s.length - lastDot - 1 == 3) null else '.'
                else -> null
            }

            val integerPart: String
            val fractionPart: String
            if (decimalSeparator == null) {
                integerPart = s
                fractionPart = ""
            } else {
                val idx = s.lastIndexOf(decimalSeparator)
                integerPart = s.substring(0, idx)
                fractionPart = s.substring(idx + 1)
            }

            // Nella parte intera restano solo i separatori delle migliaia, che si buttano.
            val digits = integerPart.filter { it != '.' && it != ',' }
            if (digits.any { !it.isDigit() }) return null
            if (fractionPart.any { !it.isDigit() }) return null
            if (digits.isEmpty() && fractionPart.isEmpty()) return null

            val units = if (digits.isEmpty()) 0L else digits.toLongOrNull() ?: return null

            // Più di due decimali capita nei cambi valuta e negli interessi: si arrotonda
            // al centesimo, per eccesso sul mezzo centesimo.
            val hundredths: Long = when {
                fractionPart.isEmpty() -> 0L
                fractionPart.length == 1 -> fractionPart.toLong() * 10
                fractionPart.length == 2 -> fractionPart.toLong()
                else -> {
                    val head = fractionPart.substring(0, 2).toLong()
                    val next = fractionPart[2].digitToInt()
                    if (next >= 5) head + 1 else head
                }
            }

            val magnitude = units * 100 + hundredths
            return Money(if (negative) -magnitude else magnitude)
        }

        private fun groupThousands(value: Long): String {
            val raw = value.toString()
            if (raw.length <= 3) return raw
            return buildString(raw.length + raw.length / 3) {
                val firstGroup = raw.length % 3
                if (firstGroup > 0) append(raw, 0, firstGroup)
                var i = firstGroup
                while (i < raw.length) {
                    if (isNotEmpty()) append('.')
                    append(raw, i, i + 3)
                    i += 3
                }
            }
        }
    }
}

/** Somma di una sequenza di importi. Esiste per non ripetere `sumOf { it.cents }` ovunque. */
fun Iterable<Money>.sum(): Money = Money(sumOf { it.cents })
