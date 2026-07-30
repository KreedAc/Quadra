import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * La chiave di firma sta fuori dal repository.
 *
 * `keystore.properties` è ignorato da git, e la chiave vera va tenuta anche fuori dal
 * computer: perderla significa non poter più aggiornare l'app pubblicata, e nessun
 * backup del codice la rimette al suo posto.
 *
 * Se il file non c'è la build di release resta non firmata invece di fallire: chi clona
 * il progetto deve poterlo compilare senza avere le chiavi di nessuno.
 */
val fileChiavi = rootProject.file("keystore.properties")
val chiavi = Properties().apply {
    if (fileChiavi.exists()) fileChiavi.inputStream().use { load(it) }
}

android {
    namespace = "it.quadra"
    compileSdk = 36

    defaultConfig {
        // Lo spazio dei nomi di chi pubblica, non di chi si chiama così.
        //
        // Era `it.quadra`, che secondo la convenzione del DNS invertito dichiara di
        // possedere il dominio quadra.it — cosa non vera, e con altri "Quadra" attivi in
        // Italia è anche la parte più facile da leggere come appropriazione. Questo
        // invece corrisponde a un profilo GitHub che è davvero nostro.
        //
        // Non ha niente a che vedere con i package del codice, che restano `it.quadra.*`:
        // sono due cose indipendenti. E dopo la prima pubblicazione non si cambia più,
        // perché cambiarlo significa pubblicare un'altra app.
        applicationId = "io.github.kreedac.quadra"
        // API 26 copre praticamente tutto il parco installato e porta java.time
        // nativo, quindi niente desugaring da configurare.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (fileChiavi.exists()) {
            create("release") {
                storeFile = rootProject.file(chiavi.getProperty("storeFile"))
                storePassword = chiavi.getProperty("storePassword")
                keyAlias = chiavi.getProperty("keyAlias")
                keyPassword = chiavi.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (fileChiavi.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        // Serve solo a mostrare il numero di versione nelle impostazioni leggendolo da
        // qui, invece di riscriverlo a mano in un punto che poi nessuno aggiorna.
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// exportSchema = true nel database ha bisogno di una destinazione: lo schema
// versionato va in git, così le migrazioni future si scrivono guardando il diff.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Tutta la logica che produce numeri sta qui e non ha dipendenze da Android.
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
