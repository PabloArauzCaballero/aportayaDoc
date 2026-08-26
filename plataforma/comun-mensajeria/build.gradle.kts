// Outbox transaccional, relevo a Kafka y consumidor idempotente (ADR-018). El
// evento se escribe en la MISMA transaccion que el hecho; publicar viene despues.
plugins { id("aportaya.libreria") }

dependencies {
    api(project(":plataforma:comun-dominio"))
    api(project(":plataforma:comun-datos"))
    implementation(libs.jackson.databind)
    implementation(libs.micrometer)
    compileOnly(libs.jakarta.xml.bind)
    implementation(libs.kafka)
    implementation(libs.shedlock)
    implementation(libs.shedlock.jdbc)

    testImplementation(project(":plataforma:comun-pruebas"))
    testImplementation(libs.bundles.pruebas)
    testImplementation(libs.spring.boot.jdbc)
    testImplementation(libs.testcontainers.kafka)
    testRuntimeOnly(libs.junit.platform.launcher)
}
