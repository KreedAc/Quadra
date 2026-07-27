package it.lemiespese.ui.theme

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

private val SchemaChiaro = lightColorScheme(
    primary = Blu,
    onPrimary = Color.White,
    secondary = Color(0xFF12A374),
    onSecondary = Color.White,
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF101822),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101822),
    surfaceVariant = Color(0xFFECEFF4),
    onSurfaceVariant = Color(0xFF6F7D8C),
    outlineVariant = Color(0xFFDDE3EA),
    error = Color(0xFFD1402F),
    onError = Color.White,
)

/**
 * Ruoli che Material non prevede ma che servono a questa app.
 *
 * [income] è separato dal secondario perché il verde del marchio e il verde delle
 * entrate hanno significati diversi e possono divergere. [brand] è il gradiente
 * identitario, e va usato in due soli posti: il saldo e l'azione primaria.
 */
@Immutable
data class ColoriExtra(
    val income: Color,
    val brand: Brush,
    val brandStart: Color,
    val brandEnd: Color,
)

val LocalColoriExtra = staticCompositionLocalOf {
    ColoriExtra(
        income = Color(0xFF12A374),
        brand = Brush.horizontalGradient(listOf(Blu, Verde)),
        brandStart = Blu,
        brandEnd = Verde,
    )
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
fun LeMieSpeseTheme(
    scuro: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val schema = if (scuro) SchemaScuro else SchemaChiaro
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !scuro
        }
    }
    val extraColori = ColoriExtra(
        income = if (scuro) Color(0xFF1FD8A4) else Color(0xFF12A374),
        brand = Brush.horizontalGradient(listOf(Blu, Verde)),
        brandStart = Blu,
        brandEnd = Verde,
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
