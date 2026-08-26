// El unico archivo compartido por los catorce carriles, y por eso el unico que
// NADIE vuelve a editar: los servicios se descubren por barrido de directorio.
// Agregar un servicio es crear una carpeta con su build.gradle.kts.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Resuelve el toolchain 21 sin que cada maquina del parque instale el mismo JDK
    // a mano: si no esta, Gradle lo baja. Es lo que hace que el gate de la Fase 0
    // corra igual en macOS arm64 y en WSL2.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "aportaya"

// bd/ — las tareas de base de datos (levantar, aplicar, semillas, reset).
// Es un proyecto sin codigo: existe para que `./gradlew bd:reset` sea el mismo
// comando en las cinco maquinas.
include(":bd")

// plataforma/ — Ola 0. Lista explicita a proposito: no crece por carril, y una
// carpeta nueva aca es una decision troncal, no un descubrimiento.
listOf(
    "comun-dominio",
    "comun-datos",
    "comun-web",
    "comun-mensajeria",
    "comun-pruebas",
    "gateway",
).forEach { include(":plataforma:$it") }

// servicios/ — BARRIDO. Un directorio con build.gradle.kts es un servicio.
file("servicios").listFiles()
    ?.filter { it.isDirectory && File(it, "build.gradle.kts").isFile }
    ?.sortedBy { it.name }
    ?.forEach { include(":servicios:${it.name}") }
