// conContexto(): SET LOCAL dentro de la transaccion, la fabrica de DSLContext y el
// pool. Toda consulta del proyecto pasa por aca (invariante 3).
plugins { id("aportaya.libreria") }

dependencies {
    api(project(":plataforma:comun-dominio"))
    api(libs.jooq)
    implementation(libs.spring.boot.jdbc)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.bundles.pruebas)
    testRuntimeOnly(libs.junit.platform.launcher)
}
