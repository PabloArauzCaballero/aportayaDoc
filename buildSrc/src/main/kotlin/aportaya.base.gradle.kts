// Convenciones comunes a todo modulo Java del monorepo: plataforma y servicios.
plugins {
    `java-library`
    jacoco
    id("com.diffplug.spotless")
}

group = "bo.aportaya"
version = "1.0.0"

java {
    // Toolchain 21, no "el JDK que tenga la maquina". Si no esta, Gradle lo baja.
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // -Werror: una advertencia que nadie mira es una advertencia que no existe.
    options.compilerArgs.addAll(
        listOf("-Xlint:all,-processing,-serial,-this-escape", "-Werror", "-parameters"),
    )
}

spotless {
    java {
        target("src/*/java/**/*.java")
        palantirJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Testcontainers necesita saber donde esta el socket de Docker. En Linux lo
// encuentra solo; en macOS con Docker Desktop y en WSL2 no siempre, y el sintoma es
// "Could not find a valid Docker environment" en la maquina de otro. Se propaga lo
// que el entorno ya diga, sin cablear ninguna ruta en el repositorio.
val entornoDocker = listOf("DOCKER_HOST", "DOCKER_CONTEXT", "DOCKER_API_VERSION", "TESTCONTAINERS_RYUK_DISABLED")
    .associateWith { providers.environmentVariable(it) }

// docker-java —el cliente que trae Testcontainers— pide por omision la API 1.32,
// y Docker Engine 29 exige 1.40 como minimo: la peticion vuelve con 400 y, a traves
// del proxy de Docker Desktop, con un cuerpo que no dice nada. 1.41 la soporta
// cualquier motor desde 2020 y satisface el minimo de los actuales.
val apiDeDocker = "1.41"

val saltada = Regex("""<testcase name="([^"]*)"[^>]*>\s*<skipped""")

tasks.withType<Test>().configureEach {
    entornoDocker.forEach { (clave, valor) -> valor.orNull?.let { environment(clave, it) } }
    systemProperty("api.version", apiDeDocker)
    environment("DOCKER_API_VERSION", apiDeDocker)

    // Ninguna prueba saltada. No es celo: jqwik reporta una propiedad como SALTADA
    // cuando no puede correrla, y el build queda verde con mil casos de cuadre que
    // nunca se ejecutaron. Una prueba saltada miente mejor que una que falta.
    val resultados = reports.junitXml.outputLocation
    val corredor = name
    doLast {
        val carpeta = resultados.get().asFile
        if (!carpeta.isDirectory) return@doLast
        val saltadas = carpeta.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".xml") }
            .flatMap { archivo -> saltada.findAll(archivo.readText()).map { it.groupValues[1] } }
            .toList()
        require(saltadas.isEmpty()) {
            "$corredor dejo ${saltadas.size} prueba(s) saltada(s); ninguna @Disabled:\n" +
                saltadas.joinToString("\n") { "  $it" }
        }
    }
}

// --------------------------------------------------------------- contenedores --
//
// Cada modulo corre en su propia JVM, asi que cada uno arranca su PostgreSQL de
// Testcontainers y le aplica las 304 tablas de `sql/aplicar.sql`. Con
// `org.gradle.parallel=true` y seis modulos con pruebas de integracion, eso son
// seis contenedores compitiendo por CPU y disco: las pruebas empiezan a fallar por
// tiempo sin que nada este mal, que es la peor clase de fallo — enseña a
// desconfiar del gate.
//
// El limite se pone aca y no subiendo los timeouts: el gate que dice «ninguna
// prueba de caso de uso tarda mas de 120s» sigue intacto, que es el que importa.
// Lo que se acota es cuantas arrancan a la vez.
abstract class LimiteDeContenedores : BuildService<BuildServiceParameters.None>

val limiteDeContenedores =
    gradle.sharedServices.registerIfAbsent("limiteDeContenedores", LimiteDeContenedores::class) {
        maxParallelUsages.set(2)
    }

// Los cinco corredores. Uno solo con todo adentro es un corredor que nadie corre
// en local porque tarda cinco minutos.
tasks.named<Test>("test") {
    usesService(limiteDeContenedores)
    // Sin filtro de motores: corren Jupiter, jqwik y ArchUnit. Nombrar dos deja
    // fuera al tercero, y un servicio entero se queda sin pruebas de arquitectura
    // sin que nadie lo note.
    useJUnitPlatform()
    exclude(
        "**/CU*Test.class",
        "**/*RepositorioTest.class",
        "**/Aislamiento*Test.class",
        "**/*ContratoTest.class",
        "**/*SagaTest.class",
        "**/*E2ETest.class",
        "**/*BarridoTest.class",
    )
    systemProperty("junit.jupiter.execution.timeout.default", "5s")
    testLogging { events("failed") }
}

// El source set se toma FUERA del bloque de configuracion: dentro, `the<...>()`
// resuelve contra la tarea y no contra el proyecto, y falla recien cuando alguien
// realiza la tarea — es decir, en la maquina de otro.
val pruebas = the<SourceSetContainer>()["test"]


fun corredor(nombre: String, descripcion: String, patrones: List<String>, tiempo: String) =
    tasks.register<Test>(nombre) {
        group = "verification"
        description = descripcion
        testClassesDirs = pruebas.output.classesDirs
        classpath = pruebas.runtimeClasspath
        useJUnitPlatform()
        patrones.forEach { include(it) }
        // Un servicio sin sagas todavia no es un servicio roto.
        failOnNoDiscoveredTests = false
        systemProperty("junit.jupiter.execution.timeout.default", tiempo)
        // El arranque del contenedor NO es una prueba, y por eso tiene su propio
        // presupuesto. Cada modulo corre en su JVM, asi que con `org.gradle.parallel`
        // arrancan tantos PostgreSQL como servicios haya, y cada uno aplica las 304
        // tablas de `sql/aplicar.sql`. Con tres servicios entraba en 120s; con cinco
        // ya no, y las pruebas fallaban por «timeout» sin que nada estuviera mal.
        //
        // Se separa en vez de subir el limite de las pruebas: el gate que dice
        // «ningun caso de uso tarda mas de 120s» sigue intacto, que es el que importa.
        systemProperty("junit.jupiter.execution.timeout.beforeall.method.default", "600s")
        usesService(limiteDeContenedores)
        testLogging { events("failed") }
    }

corredor(
    "integrationTest",
    "Casos de uso y repositorios contra PostgreSQL real (Testcontainers)",
    listOf("**/CU*Test.class", "**/*RepositorioTest.class", "**/Aislamiento*Test.class"),
    "120s",
)
corredor("contractTest", "Contratos entre pares de servicios", listOf("**/*ContratoTest.class"), "60s")
corredor("sagaTest", "Sagas con dobles de los servicios participantes", listOf("**/*SagaTest.class"), "120s")
corredor("e2eTest", "Punta a punta sobre compose --profile todo", listOf("**/*E2ETest.class"), "300s")

// Cobertura como PISO, no como meta. No se excluye codigo dificil para subir el
// numero: la pregunta de ADR-026 es «que del dinero no esta probado», y un porcentaje
// alto conseguido excluyendo lo dificil la contesta al reves.
// El modulo lo declara con `extra["pisoDeCobertura"] = 0.95`; sin declaracion, no
// hay piso todavia y la tarea no corre.
fun piso(): Double = (project.findProperty("pisoDeCobertura") as? Number)?.toDouble() ?: 0.0

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn("test")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// Se configura la tarea que el plugin YA cablea contra el .exec de `test`. Una
// JacocoCoverageVerification registrada a mano no tiene datos de ejecucion y se
// saltea sola — verde, y sin haber medido nada.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("test")
    onlyIf { piso() > 0.0 }
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = piso().toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                minimum = piso().toBigDecimal()
            }
        }
    }
}

val cobertura = tasks.register("cobertura") {
    group = "verification"
    description = "El piso de cobertura del ambito de este modulo"
    dependsOn("jacocoTestCoverageVerification")
}

tasks.named("check") { dependsOn(cobertura) }

// Barrido: las reglas propias que no son de arquitectura viven en
// plataforma/comun-pruebas como pruebas, no como convencion de revision — y cada
// servicio las aplica sobre sus propias fuentes (planes/00 §6).
corredor("testBarrido", "Las reglas propias sobre las fuentes de este modulo", listOf("**/*BarridoTest.class"), "60s")

tasks.named("check") { dependsOn("testBarrido") }
