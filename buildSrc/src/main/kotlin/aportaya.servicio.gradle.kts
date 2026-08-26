import bo.aportaya.gradle.AportayaExtension

// Un servicio desplegable: Spring Boot, el BOM que sincroniza las versiones, y la
// guarda de JPA. Nada de esto se copia catorce veces.
plugins {
    id("aportaya.base")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

extensions.create<AportayaExtension>("aportaya")

val catalogo = extensions.getByType<VersionCatalogsExtension>().named("libs")
// El generador y la libreria de jOOQ tienen que ser la MISMA version: el codigo
// generado por 3.20 llama a metodos que el runtime del BOM (3.19) no tiene, y el
// error aparece recien al compilar 300 clases generadas. Se sobrescribe la
// propiedad del BOM de Spring Boot, que es donde vive la decision.
extra["jooq.version"] = catalogo.findVersion("jooq").get().requiredVersion

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    // Capas de Spring Boot: un cambio de codigo no empuja 200 MB de dependencias.
    layered { enabled.set(true) }
}

// ADR-016: JPA compite con sql/ por la propiedad del esquema, y su dirty checking
// es incompatible con append-only. La regla se implementa en la Fase 0 porque la
// tentacion aparece el primer dia.
val sinJpa = tasks.register("sinJpa") {
    group = "verification"
    description = "Falla si alguien declara JPA o configura spring.jpa"
    val declaradas = provider {
        configurations
            .filter { it.isCanBeDeclared }
            .flatMap { it.dependencies }
            .map { "${it.group}:${it.name}" }
    }
    val configuracion = layout.projectDirectory.file("src/main/resources/application.yml").asFile
    val donde = path
    doLast {
        val prohibidas = declaradas.get().filter {
            it.contains("data-jpa") || it.contains("hibernate") || it.contains("jakarta.persistence")
        }
        require(prohibidas.isEmpty()) { "$donde declara JPA, y JPA esta prohibido (ADR-016): $prohibidas" }
        if (configuracion.isFile) {
            val lineas = configuracion.readLines().withIndex().filter { (_, l) -> l.trim().startsWith("jpa:") }
            require(lineas.isEmpty()) {
                "$donde tiene spring.jpa en application.yml (linea ${lineas.first().index + 1}): prohibido (ADR-016)"
            }
        }
    }
}

tasks.named("check") { dependsOn(sinJpa) }
