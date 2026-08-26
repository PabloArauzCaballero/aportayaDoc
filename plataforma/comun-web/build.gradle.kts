// Manejador global de errores, idempotencia, guardia por omision y traza. Lo que
// hace que un endpoint nuevo nazca cerrado y devuelva AP-CU<NN>-<nn>, no un stack.
plugins { id("aportaya.libreria") }

dependencies {
    api(project(":plataforma:comun-dominio"))
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.validation)
    implementation(libs.spring.boot.security)
    implementation(libs.spring.boot.oauth2)
    implementation(libs.micrometer.tracing)

    testImplementation(libs.bundles.pruebas)
    testRuntimeOnly(libs.junit.platform.launcher)
}
