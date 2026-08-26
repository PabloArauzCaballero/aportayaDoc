// Atomos puros: Dinero, Periodo, PlazoHabil. SIN Spring y SIN jOOQ, a proposito —
// es lo que permite probarlos en milisegundos y lo que ArchUnit verifica arriba.
plugins { id("aportaya.libreria") }

dependencies {
    testImplementation(libs.bundles.pruebas)
    testImplementation(libs.jqwik)
    testRuntimeOnly(libs.junit.platform.launcher)
}
