// Unica entrada publica detras de NGINX: enruta por prefijo, corta por tasa y
// propaga x-request-id. NO compone respuestas, no traduce errores y no consulta la
// base — un gateway con logica es el monolito volviendo por la puerta de atras.
plugins { id("aportaya.servicio") }

dependencies {
    implementation(platform(libs.spring.cloud.bom))
    implementation(libs.spring.cloud.gateway)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.oauth2)
    implementation(libs.micrometer)
    implementation(libs.micrometer.tracing)

    testImplementation(libs.bundles.pruebas)
    testRuntimeOnly(libs.junit.platform.launcher)
}
