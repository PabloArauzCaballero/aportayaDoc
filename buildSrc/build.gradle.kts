plugins {
    `kotlin-dsl`
}

// Los plugins de convencion viven aca para que un servicio nuevo herede todo sin
// copiar nada: catorce servicios iguales, no catorce servicios parecidos.
dependencies {
    implementation(plugin(libs.plugins.spring.boot))
    implementation(plugin(libs.plugins.spring.dep.mgmt))
    implementation(plugin(libs.plugins.spotless))
    implementation(plugin(libs.plugins.openapi.generator))
    implementation(libs.codegen.jooq)
    implementation(libs.driver.postgresql)
}

fun plugin(dependencia: Provider<PluginDependency>): Provider<String> =
    dependencia.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
