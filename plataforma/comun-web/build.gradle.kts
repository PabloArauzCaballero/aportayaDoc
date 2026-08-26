// Manejador global de errores, idempotencia, guardia por omision y traza. Lo que
// hace que un endpoint nuevo nazca cerrado y devuelva AP-CU<NN>-<nn>, no un stack.
plugins { id("aportaya.libreria") }

dependencies {
    api(project(":plataforma:comun-dominio"))
    api(project(":plataforma:comun-datos"))
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.jdbc)   // DataAccessException: la traduccion de restricciones
    implementation(libs.spring.boot.validation)
    api(libs.spring.boot.security)
    implementation(libs.spring.boot.oauth2)
    implementation(libs.micrometer.tracing)

    testImplementation(project(":plataforma:comun-pruebas"))
    testImplementation(libs.bundles.pruebas)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// erroresCatalogo — nombre de restriccion -> R-XXX-nn, generado desde sql/.
// Se genera en build/ y NO se versiona: el gate es que se pueda regenerar, no un
// diff que alguien tiene que acordarse de actualizar.
val catalogoDeErrores = layout.buildDirectory.dir("generated/recursos")

val erroresCatalogo = tasks.register<Exec>("erroresCatalogo") {
    group = "generadores"
    description = "Catalogo de errores: constraint_name -> R-XXX-nn, desde sql/"
    workingDir = rootDir
    executable = "python3"
    val salida = catalogoDeErrores.map { it.file("errores-restricciones.properties") }
    outputs.file(salida)
    inputs.files(
        rootProject.layout.projectDirectory.file("sql/50_verificacion/prueba_humo.sql"),
        rootProject.layout.projectDirectory.file("sql/40_reglas/restricciones.sql"),
    )
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf("scripts/generar_errores.py", salida.get().asFile.absolutePath)
        },
    )
}

sourceSets["main"].resources.srcDir(catalogoDeErrores)
tasks.named("processResources") { dependsOn(erroresCatalogo) }
