// Convenciones comunes a todo modulo Java del monorepo: plataforma y servicios.
plugins {
    `java-library`
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

tasks.withType<Test>().configureEach {
    entornoDocker.forEach { (clave, valor) -> valor.orNull?.let { environment(clave, it) } }
    systemProperty("api.version", apiDeDocker)
    environment("DOCKER_API_VERSION", apiDeDocker)
}

// Los cinco corredores. Uno solo con todo adentro es un corredor que nadie corre
// en local porque tarda cinco minutos.
tasks.named<Test>("test") {
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

// Barrido: las reglas propias que no son de arquitectura viven en
// plataforma/comun-pruebas como pruebas, no como convencion de revision — y cada
// servicio las aplica sobre sus propias fuentes (planes/00 §6).
corredor("testBarrido", "Las reglas propias sobre las fuentes de este modulo", listOf("**/*BarridoTest.class"), "60s")

tasks.named("check") { dependsOn("testBarrido") }
