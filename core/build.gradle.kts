import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// :core è Kotlin puro, senza una singola dipendenza da Android.
// È una scelta deliberata: tutta la logica di dominio resta testabile in JVM,
// in millisecondi, senza emulatore. Lo strato Android sopra deve limitarsi a
// persistenza (Room), UI (Compose) e I/O di sistema.
dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
}

tasks.test {
    useJUnitPlatform()
}
