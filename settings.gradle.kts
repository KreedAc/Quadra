pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // google() serve al modulo :app (Android Gradle Plugin, Compose, Room).
        // Va riabilitato quando il progetto viene aperto in Android Studio.
        // Vedi la sezione "Ambiente di build" nel README.
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // google() — idem, vedi sopra.
    }
}

rootProject.name = "LeMieSpese"

include(":core")
