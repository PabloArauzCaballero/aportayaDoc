// La raiz no compila nada: agrega. Todo lo que el CI ejecuta tiene que poder
// ejecutarse igual en local con una sola tarea (planes/01 §0.1). Un paso de CI que
// no existe como tarea es un paso que nadie puede reproducir.
plugins {
    base
}

val modulosJava = subprojects.filter {
    it.path.startsWith(":plataforma:") || it.path.startsWith(":servicios:")
}
val servicios = subprojects.filter { it.path.startsWith(":servicios:") }

fun agregar(nombre: String, descripcion: String, proyectos: List<Project> = modulosJava) {
    val existente = tasks.names.contains(nombre)
    val tarea = if (existente) tasks.named(nombre) else tasks.register(nombre)
    tarea.configure {
        group = "verification"
        this.description = descripcion
        dependsOn(proyectos.map { "${it.path}:$nombre" })
    }
}

agregar("spotlessCheck", "Formato unico en todo el monorepo")
agregar("spotlessApply", "Aplica el formato unico")
agregar("check", "Compilacion, formato, ArchUnit y las reglas propias")
agregar("test", "Atomos de dominio · sin infraestructura")
agregar("integrationTest", "Casos de uso y repositorios contra PostgreSQL real")
agregar("contractTest", "Contratos entre pares de servicios")
agregar("sagaTest", "Sagas con dobles de los servicios participantes")
agregar("e2eTest", "Punta a punta sobre compose --profile todo")
agregar("generateJooq", "Clases de jOOQ de cada esquema, desde la base viva", servicios)
agregar("generateOpenApiClients", "Cliente TypeScript desde los OpenAPI · clientes/typescript", emptyList())

tasks.named("generateOpenApiClients") {
    group = "build"
    dependsOn(servicios.map { "${it.path}:generarClienteTypescript" })
}

tasks.register("verificar") {
    group = "verification"
    description = "Lo mismo que corre el CI, en un solo comando"
    dependsOn("spotlessCheck", "check", "test", "integrationTest", "contractTest", "sagaTest")
}

// -------------------------------------------------------------- generadores --
// Los tres generadores son scripts de Python a proposito: viven donde ya viven
// generar_ddl.py y verificar_boveda.py, leen la misma scripts/modelo.py y por lo
// tanto NO PUEDEN divergir del modelo. La tarea de Gradle es el envoltorio, para
// que el comando sea el mismo en las cinco maquinas.

fun generador(nombre: String, descripcion: String, guion: String, propiedad: String?, formatear: Boolean) =
    tasks.register<Exec>(nombre) {
        group = "generadores"
        this.description = descripcion
        workingDir = rootDir
        executable = "python3"
        val valor = propiedad?.let { providers.gradleProperty(it) }
        argumentProviders.add(
            CommandLineArgumentProvider {
                if (propiedad == null) {
                    listOf(guion)
                } else {
                    require(valor!!.isPresent) { "Falta -P$propiedad=<valor> en ./gradlew $nombre" }
                    listOf(guion, valor.get())
                }
            },
        )
        // El servicio y el caso de uso salen del generador, no de un teclado; el
        // formato unico se lo pone spotless para que nadie tenga que acordarse.
        if (formatear) finalizedBy("spotlessApply")
    }

generador(
    "nuevoServicio",
    "Crea un servicio entero desde cero · -Pnombre=<servicio>",
    "scripts/nuevo_servicio.py",
    "nombre",
    formatear = true,
)
generador(
    "nuevoCu",
    "Contrato, esqueleto, controlador y LAS PRUEBAS FALLANDO de un CU · -Pcu=<NN>",
    "scripts/nuevo_cu.py",
    "cu",
    formatear = true,
)
generador(
    "verificarCriterios",
    "Cada gherkin de la boveda tiene su prueba, y cada R-XXX-nn su prueba de rechazo",
    "scripts/verificar_criterios.py",
    null,
    formatear = false,
)
generador(
    "verificarBoveda",
    "La boveda y el esquema no divergen",
    "scripts/verificar_boveda.py",
    null,
    formatear = false,
)
generador(
    "verificarSeguridad",
    "Patrones prohibidos, secretos y R-SEG-10/11/12 sobre el repositorio",
    "scripts/verificar_seguridad.py",
    null,
    formatear = false,
)
