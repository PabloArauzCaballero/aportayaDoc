// Outbox transaccional, relevo a Kafka y consumidor idempotente (ADR-018). El
// evento se escribe en la MISMA transaccion que el hecho; publicar viene despues.
plugins { id("aportaya.libreria") }

dependencies {
    api(project(":plataforma:comun-dominio"))
    implementation(project(":plataforma:comun-datos"))
    implementation(libs.kafka)
    implementation(libs.shedlock)
    implementation(libs.shedlock.jdbc)

    testImplementation(libs.bundles.pruebas)
    testImplementation(libs.testcontainers.kafka)
    testRuntimeOnly(libs.junit.platform.launcher)
}
