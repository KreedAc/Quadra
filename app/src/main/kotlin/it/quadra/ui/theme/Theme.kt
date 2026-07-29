package it.quadra.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Il tema, trascritto dai token della bozza visiva.
 *
 * Il tema scuro è quello principale — l'app si apre la sera — ma il chiaro non è un
 * ripensamento: chi lascia il telefono in automatico se lo ritrova alle due del
 * pomeriggio.
 *
 * I neutri sono virati al blu invece che grigi puri, così il gradiente identitario
 * sembra parente del fondo e non appoggiato sopra. Il nero non è mai pieno: al buio
 * il nero assoluto fa risaltare i bordi del pannello.
 */

private val Blu = Color(0xFF2F6BFF)
private val Verde = Color(0xFF1FD8A4)

/**
 * Le stesse due tinte, abbassate per il fondo chiaro.
 *
 * Il verde del marchio è nato per brillare sul blu notte: sul bianco perde quasi tutto
 * il contrasto, e un'etichetta "Salva" scritta con quello si legge male in pieno sole —
 * che è esattamente quando il tema chiaro è acceso. Il gradiente pieno resta invece
 * quello acceso in entrambi i temi: lì il colore fa da fondo, non da inchiostro.
 */
private val BluChiaro = Color(0xFF2159E0)
private val VerdeChiaro = Color(0xFF0E9F76)

private val SchemaScuro = darkColorScheme(
    primary = Blu,
    onPrimary = Color(0xFF04121A),
    secondary = Verde,
    onSecondary = Color(0xFF04121A),
    background = Color(0xFF0D141C),
    onBackground = Color(0xFFE8EEF5),
    surface = Color(0xFF141E28),
    onSurface = Color(0xFFE8EEF5),
    surfaceVariant = Color(0xFF1C2836),
    onSurfaceVariant = Color(0xFF7B8998),
    outlineVariant = Color(0xFF24313F),
    error = Color(0xFFE85545),
    onError = Color(0xFF1A0906),
)

/**
 * Il chiaro non è lo scuro rovesciato.
 *
 * Tre livelli devono restare distinguibili contemporaneamente: il fondo della pagina, le
 * schede che ci stanno sopra, e le pastiglie — tasti del tastierino, chip, campi — che
 * stanno sia sulle schede sia sul fondo. Con un fondo troppo vicino al bianco le schede
 * spariscono; con una variante troppo chiara spariscono i tasti quando appoggiano sul
 * fondo invece che su una scheda, che è quello che succede nella schermata di
 * inserimento. Da qui i tre passi netti: fondo azzurrato, schede bianche, varianti più
 * scure di entrambi.
 */
private val SchemaChiaro = lightColorScheme(
    primary = BluChiaro,
    onPrimary = Color.White,
    secondary = VerdeChiaro,
    onSecondary = Color.White,
    background = Color(0xFFF2F5F9),
    onBackground = Color(0xFF101822),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101822),
    surfaceVariant = Color(0xFFE4EAF1),
    onSurfaceVariant = Color(0xFF667585),
    outlineVariant = Color(0xFFD7DEE7),
    error = Color(0xFFC93B2B),
    onError = Color.White,
)

/**
 * Ruoli che Material non prevede ma che servono a questa app.
 *
 * [income] è separato dal secondario perché il verde del marchio e il verde delle
 * entrate hanno significati diversi e possono divergere. [brand] è il gradiente
 * identitario, e va usato in due soli posti: il saldo e l'azione primaria.
 *
 * [brandStart] e [brandEnd] non sono i capi del gradiente: sono il marchio quando fa da
 * inchiostro — un'etichetta, un'icona, un accento su un fondo. Per questo cambiano col
 * tema mentre [brand] no, e per questo vanno usati loro e mai i capi del pennello.
 *
 * [onBrand] è quello che si scrive sopra [brand]: il gradiente è acceso in entrambi i
 * temi, quindi sopra ci va sempre lo stesso blu quasi nero.
 */
@Immutable
data class ColoriExtra(
    val income: Color,
    val brand: Brush,
    val brandStart: Color,
    val brandEnd: Color,
    val onBrand: Color,
    /** Serve a [tinta]: i colori dell'utente vanno letti diversamente sui due fondi. */
    val scuro: Boolean,
)

val LocalColoriExtra = staticCompositionLocalOf {
    ColoriExtra(
        income = Color(0xFF12A374),
        brand = Brush.horizontalGradient(listOf(Blu, Verde)),
        brandStart = Blu,
        brandEnd = Verde,
        onBrand = Color(0xFF04121A),
        scuro = true,
    )
}

/**
 * Un colore scelto dall'utente, letto sul tema corrente.
 *
 * La tavolozza è tarata sul fondo scuro: sono tinte medie, che sul blu notte hanno il
 * peso giusto. Le stesse su una scheda bianca si sgonfiano, e si sgonfia soprattutto
 * l'icona — che sta dentro una pastiglia fatta con la sua stessa tinta annacquata, cioè
 * colore chiaro sopra colore chiarissimo. Sul chiaro le porto giù di un passo: quel
 * tanto che stacca l'inchiostro dal suo fondo, senza che il verde smetta di essere il
 * verde che l'utente ha scelto.
 *
 * Un passo solo, e verso il blu di notte invece che verso il nero: schiacciarle di più
 * le farebbe virare tutte allo stesso fango, che è il modo più veloce per rendere le
 * categorie indistinguibili proprio dove servono a distinguere.
 */
@Composable
fun tinta(colore: Color): Color = if (extra.scuro) colore else lerp(colore, Notte, 0.22f)

@Composable
fun tinta(argb: Int): Color = tinta(Color(argb))

private val Notte = Color(0xFF0A1119)

/**
 * Chiaro, scuro, o quello che dice il telefono.
 *
 * Il valore preferito sta nella tabella delle preferenze e non in un file a parte, così
 * viaggia col backup: chi cambia telefono ritrova l'app come l'aveva lasciata, senza
 * dover ricordare di aver toccato quell'interruttore.
 */
enum class Tema(val etichetta: String) {
    SISTEMA("Sistema"),
    CHIARO("Chiaro"),
    SCURO("Scuro");

    companion object {
        /** La chiave sotto cui è salvato, unica per tutta l'app. */
        const val CHIAVE = "tema"

        /** Un valore assente o non riconosciuto vale [SISTEMA]: nessuna scelta è una scelta. */
        fun da(valore: String?): Tema = entries.firstOrNull { it.name == valore } ?: SISTEMA
    }
}

/** Se questo tema, adesso, sia scuro. Solo [Tema.SISTEMA] deve chiederlo ad Android. */
@Composable
fun Tema.scuro(): Boolean = when (this) {
    Tema.SISTEMA -> isSystemInDarkTheme()
    Tema.CHIARO -> false
    Tema.SCURO -> true
}

/**
 * Un solo carattere, quello di sistema: Roboto su Android, e l'app sembra parte del
 * telefono invece di un ospite. I pesi sono solo Regular, Medium e Bold perché sono
 * gli unici che Roboto ha davvero — chiedergli un 600 significa lasciare che il
 * sistema arrotondi a caso.
 */
private val Tipografia = Typography().run {
    copy(
        displaySmall = displaySmall.copy(
            fontFamily = FontFamily.Default,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.2).sp,
        ),
        headlineSmall = headlineSmall.copy(
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
        ),
        titleMedium = titleMedium.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
        ),
        bodyLarge = bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp),
        bodySmall = bodySmall.copy(fontSize = 12.sp),
        labelSmall = labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
    )
}

/**
 * Cifre a larghezza fissa, così le colonne di importi restano allineate.
 * "tnum" è la feature OpenType che Roboto espone per le cifre tabulari.
 */
val TextStyle.tabular: TextStyle
    get() = copy(fontFeatureSettings = "tnum")

@Composable
fun QuadraTheme(tema: Tema, content: @Composable () -> Unit) {
    QuadraTheme(scuro = tema.scuro(), content = content)
}

@Composable
fun QuadraTheme(
    scuro: Boolean,
    content: @Composable () -> Unit,
) {
    val schema = if (scuro) SchemaScuro else SchemaChiaro
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !scuro
            // Anche la barra di navigazione: sul tema chiaro le tre icone di sistema
            // restano bianche su bianco e spariscono.
            controller.isAppearanceLightNavigationBars = !scuro
        }
    }
    val extraColori = ColoriExtra(
        income = if (scuro) Color(0xFF1FD8A4) else Color(0xFF12A374),
        brand = Brush.horizontalGradient(listOf(Blu, Verde)),
        brandStart = if (scuro) Blu else BluChiaro,
        brandEnd = if (scuro) Verde else VerdeChiaro,
        onBrand = Color(0xFF04121A),
        scuro = scuro,
    )
    CompositionLocalProvider(LocalColoriExtra provides extraColori) {
        MaterialTheme(
            colorScheme = schema,
            typography = Tipografia,
            content = content,
        )
    }
}

/** Scorciatoia leggibile per i ruoli extra. */
val extra: ColoriExtra
    @Composable get() = LocalColoriExtra.current
