import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

// ADR-020: el contrato se escribe primero y el servidor se genera de el. Lo que el
// controlador implementa es una interfaz generada, no una firma escrita a mano: es
// lo que hace que "el codigo se aparto del contrato" no pueda pasar en silencio.
plugins {
    id("aportaya.base")
    id("org.openapi.generator")
}

val servicio = project.name
val contrato = layout.projectDirectory.file("src/main/resources/openapi/$servicio.yaml")
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
    inputSpec.set(contrato.asFile.absolutePath)
    outputDir.set(servidor.map { it.asFile.absolutePath })
    apiPackage.set("bo.aportaya.$servicio.web.generado")
    modelPackage.set("bo.aportaya.$servicio.web.generado.modelo")
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
    inputSpec.set(contrato.asFile.absolutePath)
    outputDir.set(clienteTs.asFile.absolutePath)
    configOptions.set(
        mapOf(
            "supportsES6" to "true",
            "withoutRuntimeChecks" to "false",
            "typescriptThreePlus" to "true",
        ),
    )
    onlyIf { tieneOperaciones() }
}

sourceSets["main"].java.srcDir(servidor.map { it.dir("src/main/java") })

tasks.named<JavaCompile>("compileJava") { dependsOn(generarServidor) }
