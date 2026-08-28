import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

// ADR-020: el contrato se escribe primero y el servidor se genera de el. Lo que el
// controlador implementa es una interfaz generada, no una firma escrita a mano: es
// lo que hace que "el codigo se aparto del contrato" no pueda pasar en silencio.
plugins {
    id("aportaya.base")
    id("org.openapi.generator")
}

val servicio = project.name

// El paquete Java del servicio NO es su nombre de proyecto: `nucleo-financiero` vive
// en `bo.aportaya.nucleofinanciero`. Si el generador arma su paquete desde el nombre
// del proyecto, el guion se convierte en guion bajo y el codigo generado cae en
// `bo.aportaya.nucleo_financiero` — otro arbol de paquetes. El controlador que lo
// importa deja de cumplir `ArquitecturaTest > ningunImportCruzado`, porque para
// ArchUnit es otro servicio.
val paquete = servicio.replace("-", "")
val contrato = layout.projectDirectory.file("src/main/resources/openapi/$servicio.yaml")

// El generador interpreta `inputSpec` como URI. Una ruta absoluta de Windows
// (`C:\...`) no lo es: falla con «Illegal character in opaque part at index 2» y
// tumba `compileJava` en las tres maquinas Windows del parque. La forma `file:` es
// URI en las cinco.
val rutaDelContrato = contrato.asFile.toURI().toString()
val servidor = layout.buildDirectory.dir("generated/openapi")
val clienteTs = rootProject.layout.projectDirectory.dir("clientes/typescript/$servicio")

// Un contrato sin operaciones todavia no genera nada, y eso no es un error: es la
// Fase 0, donde los borradores existen y estan vacios a proposito.
fun tieneOperaciones(): Boolean {
    val f = contrato.asFile
    return f.isFile && !f.readText().contains(Regex("""(?m)^paths:\s*\{\s*\}\s*$"""))
}

val generarServidor = tasks.register<GenerateTask>("generarServidorOpenApi") {
    group = "build"
    description = "Interfaz de servidor de $servicio desde su OpenAPI"
    generatorName.set("spring")
    inputSpec.set(rutaDelContrato)
    outputDir.set(servidor.map { it.asFile.absolutePath })
    apiPackage.set("bo.aportaya.$paquete.web.generado")
    modelPackage.set("bo.aportaya.$paquete.web.generado.modelo")
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot3" to "true",
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
            "openApiNullable" to "false",
            "useJakartaEe" to "true",
        ),
    )
    onlyIf { tieneOperaciones() }
}

// clientes/typescript/ es GENERADO y no se edita a mano: el CI regenera y falla si
// hay diff. Un tipo escrito ahi es una divergencia esperando a ocurrir.
tasks.register<GenerateTask>("generarClienteTypescript") {
    group = "build"
    description = "Cliente TypeScript de $servicio para apps/movil y apps/backoffice"
    generatorName.set("typescript-fetch")
    inputSpec.set(rutaDelContrato)
    outputDir.set(clienteTs.asFile.absolutePath)
    configOptions.set(
        mapOf(
            "supportsES6" to "true",
            // Con las comprobaciones de runtime encendidas, los modelos importan
            // `mapValues` de un runtime.ts que el generador no siempre exporta, y el
            // cliente no compila. Sin ellas quedan interfaces puras, que es lo que la
            // app necesita: la entrada la valida el servidor con `strict()`, no el
            // cliente (contrato de implementacion §3 bis).
            "withoutRuntimeChecks" to "true",
            "typescriptThreePlus" to "true",
        ),
    )
    onlyIf { tieneOperaciones() }
}

sourceSets["main"].java.srcDir(servidor.map { it.dir("src/main/java") })

tasks.named<JavaCompile>("compileJava") { dependsOn(generarServidor) }
