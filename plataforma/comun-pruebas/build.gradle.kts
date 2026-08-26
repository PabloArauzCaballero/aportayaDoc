// Testcontainers, fixtures y los barridos. Se expone con `api` a proposito: un
// servicio no vuelve a declarar como se levanta una PostgreSQL de prueba.
plugins { id("aportaya.libreria") }

dependencies {
    api(project(":plataforma:comun-dominio"))
    api(project(":plataforma:comun-datos"))
    api(libs.bundles.pruebas)
    api(libs.testcontainers.kafka)
    api(libs.jqwik)
    api(libs.junit.jupiter)
    api(libs.snakeyaml)
    implementation(libs.spring.boot.jdbc)
    runtimeOnly(libs.postgresql)

    testRuntimeOnly(libs.junit.platform.launcher)
}
