/**
 * Tutti i plugin si dichiarano qui, una volta sola, con `apply false`.
 *
 * I moduli poi li applicano senza ripetere la versione. Non è una convenzione estetica:
 * dichiarando un plugin nella radice il suo artefatto finisce sul classpath del build, e
 * se un modulo lo richiede di nuovo indicando una versione Gradle si rifiuta di
 * proseguire — non riesce a verificare che le due richieste combacino e fallisce con
 * "already on the classpath with an unknown version".
 */
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
