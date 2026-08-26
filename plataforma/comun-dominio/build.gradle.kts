// Atomos puros: Dinero, Periodo, PlazoHabil. SIN Spring y SIN jOOQ, a proposito —
// es lo que permite probarlos en milisegundos y lo que ArchUnit verifica arriba.
plugins { id("aportaya.libreria") }

// Los atomos que tocan dinero y plazos: 95 % de lineas y ramas. Es el ambito mas alto
// del proyecto porque es el que usan los catorce servicios sin volver a probarlo.
extra["pisoDeCobertura"] = 0.95

dependencies {
    testImplementation(libs.bundles.pruebas)
    testImplementation(libs.jqwik)
    testRuntimeOnly(libs.junit.platform.launcher)
}
